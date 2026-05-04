package com.apiproxy.local;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer extends NanoHTTPD {
    private static final String TAG = "ProxyServer";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public interface LogCallback { void onLog(String message); }

    private LogCallback logCallback;
    private ProviderManager providerManager;
    private int requestId = 0;

    public ProxyServer(int port) { super("127.0.0.1", port); }
    public void setLogCallback(LogCallback c) { this.logCallback = c; }
    public void setProviderManager(ProviderManager m) { this.providerManager = m; }

    private void log(int id, String msg) {
        String line = "[#" + id + "] " + msg;
        Log.d(TAG, line);
        if (logCallback != null) logCallback.onLog(line);
    }

    @Override
    public Response serve(IHTTPSession session) {
        int id = ++requestId;
        String uri = session.getUri();
        Method method = session.getMethod();

        log(id, "━━━ " + method + " " + uri + " ━━━");

        try {
            Map<String, String> bodyMap = new HashMap<>();
            try { session.parseBody(bodyMap); } catch (Exception e) { log(id, "Body parse: " + e.getMessage()); }
            byte[] bodyBytes = new byte[0];
            String postData = bodyMap.get("postData");
            if (postData != null && !postData.isEmpty()) {
                bodyBytes = postData.getBytes(StandardCharsets.UTF_8);
                log(id, "Body: " + bodyBytes.length + " bytes");
            }

            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                log(id, "✗ No provider");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"No API provider with Key\"}");
            }

            String targetPath = smartRoute(uri, method, postData);
            String base = provider.getBaseUrl();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String targetUrl = base + targetPath;

            // Check if client wants streaming
            boolean clientStreaming = postData != null && postData.contains("\"stream\"\\s*:\\s*true");
            // Also check accept header
            String accept = session.getHeaders().get("accept");
            if (accept != null && accept.contains("text/event-stream")) clientStreaming = true;

            log(id, provider.getName() + " " + uri + " -> " + targetPath + (clientStreaming ? " [stream]" : ""));

            if (clientStreaming) {
                return forwardStreaming(id, targetUrl, provider, session, bodyBytes);
            } else {
                return forwardBuffered(id, targetUrl, provider, session, bodyBytes);
            }
        } catch (Exception e) {
            log(id, "✗ " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private String smartRoute(String uri, Method method, String body) {
        if (uri.startsWith("/v1/")) return uri;
        if (uri.startsWith("/v1beta/") || uri.startsWith("/v1alpha/")) return uri;
        if (uri.equals("/models")) return "/v1/models";
        if (uri.equals("/chat/completions")) return "/v1/chat/completions";
        if (method == Method.POST && (uri.equals("/") || uri.isEmpty())) return "/v1/chat/completions";
        return "/v1" + uri;
    }

    private ApiProvider findProvider(String uri) {
        if (providerManager == null) return null;
        for (ApiProvider p : providerManager.getAllProviders()) {
            if (p.hasApiKey()) return p;
        }
        return null;
    }

    /**
     * Streaming forward: pipe response bytes as they arrive
     */
    private Response forwardStreaming(int id, String targetUrl, ApiProvider provider, IHTTPSession session, byte[] bodyBytes) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(300000);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);

            setupHeaders(conn, provider, session, url);
            writeBody(conn, bodyBytes);

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                log(id, "← " + code + " (no body)");
                conn.disconnect();
                return newFixedLengthResponse(Response.Status.lookup(code) != null ? Response.Status.lookup(code) : Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"empty\"}");
            }

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "text/event-stream";

            Response.IStatus status = Response.Status.lookup(code);
            if (status == null) status = Response.Status.INTERNAL_ERROR;

            // Use PipedStream to stream response
            PipedOutputStream pout = new PipedOutputStream();
            PipedInputStream pin = new PipedInputStream(pout, 8192);

            // Background thread: read from upstream -> pipe to client
            executor.execute(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int read;
                    long total = 0;
                    long start = System.currentTimeMillis();
                    while ((read = is.read(buf)) != -1) {
                        pout.write(buf, 0, read);
                        pout.flush();
                        total += read;
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    log(id, "← " + code + " [stream done " + total + "B in " + elapsed + "ms]");
                    log(id, "━━━ DONE ━━━");
                } catch (IOException e) {
                    log(id, "Stream err: " + e.getMessage());
                } finally {
                    try { pout.close(); } catch (IOException ignored) {}
                    try { is.close(); } catch (IOException ignored) {}
                    conn.disconnect();
                }
            });

            Response response = newChunkedResponse(status, contentType, pin);
            // Copy relevant response headers
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null &&
                    !entry.getKey().equalsIgnoreCase("Content-Length") &&
                    !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String v : entry.getValue()) response.addHeader(entry.getKey(), v);
                }
            }

            log(id, "← " + code + " [streaming...]");
            return response;

        } catch (IOException e) {
            log(id, "✗ " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * Buffered forward: wait for full response, then send
     */
    private Response forwardBuffered(int id, String targetUrl, ApiProvider provider, IHTTPSession session, byte[] bodyBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);

            setupHeaders(conn, provider, session, url);
            writeBody(conn, bodyBytes);

            log(id, "Waiting...");
            long start = System.currentTimeMillis();

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                log(id, "← " + code + " (no body)");
                return newFixedLengthResponse(Response.Status.lookup(code) != null ? Response.Status.lookup(code) : Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"empty\"}");
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) { bos.write(buf, 0, read); }
            byte[] respBytes = bos.toByteArray();
            is.close();

            long elapsed = System.currentTimeMillis() - start;
            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/json";

            Response.IStatus status = Response.Status.lookup(code);
            if (status == null) status = Response.Status.INTERNAL_ERROR;

            Response response = newFixedLengthResponse(status, contentType,
                new ByteArrayInputStream(respBytes), respBytes.length);

            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null &&
                    !entry.getKey().equalsIgnoreCase("Content-Length") &&
                    !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String v : entry.getValue()) response.addHeader(entry.getKey(), v);
                }
            }

            conn.disconnect();

            String respPreview = "";
            if (respBytes.length > 0 && (contentType.contains("json") || contentType.contains("text"))) {
                String rs = new String(respBytes, StandardCharsets.UTF_8);
                respPreview = rs.length() > 200 ? rs.substring(0, 200) + "..." : rs;
            }

            log(id, "← " + code + " [" + respBytes.length + "B " + elapsed + "ms]");
            if (!respPreview.isEmpty()) log(id, "Resp: " + respPreview);
            log(id, "━━━ DONE ━━━");
            return response;

        } catch (IOException e) {
            log(id, "✗ " + e.getMessage());
            if (conn != null) conn.disconnect();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void setupHeaders(HttpURLConnection conn, ApiProvider provider, IHTTPSession session, URL url) {
        for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
            String k = h.getKey();
            if (k == null) continue;
            String lk = k.toLowerCase();
            if (!lk.equals("host") && !lk.equals("content-length") &&
                !lk.contains("authorization") && !lk.contains("x-api-key") &&
                !lk.contains("x-goog-api-key") && !lk.contains("api-key")) {
                conn.setRequestProperty(k, h.getValue());
            }
        }
        conn.setRequestProperty("Host", url.getHost());
        conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());
    }

    private void writeBody(HttpURLConnection conn, byte[] bodyBytes) throws IOException {
        if (bodyBytes.length > 0) {
            conn.setDoOutput(true);
            if (conn.getRequestProperty("Content-Type") == null) {
                conn.setRequestProperty("Content-Type", "application/json");
            }
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            OutputStream out = conn.getOutputStream();
            out.write(bodyBytes);
            out.flush();
            out.close();
        }
    }
}
