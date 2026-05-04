package com.apiproxy.local;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class ProxyServer extends NanoHTTPD {
    private static final String TAG = "ProxyServer";

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
            // Parse body
            Map<String, String> bodyMap = new HashMap<>();
            try { session.parseBody(bodyMap); } catch (Exception e) { log(id, "Body parse: " + e.getMessage()); }
            byte[] bodyBytes = new byte[0];
            String postData = bodyMap.get("postData");
            if (postData != null && !postData.isEmpty()) {
                bodyBytes = postData.getBytes(StandardCharsets.UTF_8);
                String preview = postData.length() > 200 ? postData.substring(0, 200) + "..." : postData;
                log(id, "Body (" + postData.length() + "): " + preview);
            }

            // Find provider
            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                log(id, "✗ No provider with API Key");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"No API provider with Key. Open API Proxy app and add your Key.\"}");
            }

            // Smart path routing
            String targetPath = smartRoute(uri, method, postData);
            String base = provider.getBaseUrl();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String targetUrl = base + targetPath;

            log(id, "Provider: " + provider.getName());
            log(id, "Route: " + uri + " -> " + targetPath);
            log(id, "Target: " + targetUrl);

            return forward(id, targetUrl, provider, session, bodyBytes);
        } catch (Exception e) {
            log(id, "✗ " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * Smart route: normalize paths for OpenAI-compatible APIs
     * / -> /v1/chat/completions (if POST with chat body)
     * /models -> /v1/models
     * /chat/completions -> /v1/chat/completions
     * /v1/* -> /v1/* (pass through)
     * /gemini/* -> keep as-is for Gemini
     * /anthropic/* -> keep as-is for Claude
     */
    private String smartRoute(String uri, Method method, String body) {
        // Already has /v1 prefix - pass through
        if (uri.startsWith("/v1/")) return uri;
        
        // Gemini API paths
        if (uri.startsWith("/v1beta/") || uri.startsWith("/v1alpha/")) return uri;
        
        // /models -> /v1/models
        if (uri.equals("/models")) return "/v1/models";
        
        // /chat/completions -> /v1/chat/completions
        if (uri.equals("/chat/completions")) return "/v1/chat/completions";
        
        // POST to / with chat body -> /v1/chat/completions
        if (method == Method.POST && (uri.equals("/") || uri.isEmpty())) {
            if (body != null && (body.contains("\"messages\"") || body.contains("\"model\""))) {
                return "/v1/chat/completions";
            }
        }
        
        // POST to / with other body - try /v1/chat/completions anyway
        if (method == Method.POST && (uri.equals("/") || uri.isEmpty())) {
            return "/v1/chat/completions";
        }
        
        // Everything else - prefix with /v1
        return "/v1" + uri;
    }

    private ApiProvider findProvider(String uri) {
        if (providerManager == null) return null;
        for (ApiProvider p : providerManager.getAllProviders()) {
            if (p.hasApiKey()) return p;
        }
        return null;
    }

    private Response forward(int id, String targetUrl, ApiProvider provider, IHTTPSession session, byte[] bodyBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);

            // Forward headers (strip auth, host, content-length)
            int headerCount = 0;
            for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
                String k = h.getKey();
                if (k == null) continue;
                String lk = k.toLowerCase();
                if (!lk.equals("host") && !lk.equals("content-length") &&
                    !lk.contains("authorization") && !lk.contains("x-api-key") &&
                    !lk.contains("x-goog-api-key") && !lk.contains("api-key")) {
                    conn.setRequestProperty(k, h.getValue());
                    headerCount++;
                }
            }
            
            // Force correct Host
            conn.setRequestProperty("Host", url.getHost());
            
            // Inject API Key
            conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());

            // Write body
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

            log(id, "Waiting...");

            int code = conn.getResponseCode();
            String respMsg = conn.getResponseMessage();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();

            if (is == null) {
                log(id, "← " + code + " " + respMsg + " (no body)");
                Response.IStatus st = Response.Status.lookup(code);
                return newFixedLengthResponse(st != null ? st : Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"empty response (" + code + ")\"}");
            }

            // Buffer full response
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) { bos.write(buf, 0, read); }
            byte[] respBytes = bos.toByteArray();
            is.close();

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

            // Response preview
            String respPreview = "";
            if (respBytes.length > 0 && (contentType.contains("json") || contentType.contains("text"))) {
                String rs = new String(respBytes, StandardCharsets.UTF_8);
                respPreview = rs.length() > 300 ? rs.substring(0, 300) + "..." : rs;
            }

            log(id, "← " + code + " " + respMsg + " [" + respBytes.length + "B]");
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
}
