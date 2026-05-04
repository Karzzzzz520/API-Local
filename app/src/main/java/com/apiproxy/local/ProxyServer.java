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

    public ProxyServer(int port) { super("127.0.0.1", port); }
    public void setLogCallback(LogCallback c) { this.logCallback = c; }
    public void setProviderManager(ProviderManager m) { this.providerManager = m; }

    private void log(String msg) {
        Log.d(TAG, msg);
        if (logCallback != null) logCallback.onLog(msg);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        String clientIP = session.getRemoteIpAddress();

        log("━━━ REQUEST ━━━");
        log("From: " + clientIP);
        log(method + " " + uri);

        try {
            // Log request headers
            StringJoiner hj = new StringJoiner(", ");
            for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
                String k = h.getKey();
                String v = h.getValue();
                // Mask sensitive headers
                if (k.toLowerCase().contains("key") || k.toLowerCase().contains("auth") || k.toLowerCase().contains("cookie")) {
                    v = v.length() > 8 ? v.substring(0, 4) + "****" : "****";
                }
                hj.add(k + ": " + v);
            }
            log("Headers: " + hj);

            // Parse body
            Map<String, String> bodyMap = new HashMap<>();
            try { session.parseBody(bodyMap); } catch (Exception e) { log("Body parse: " + e.getMessage()); }
            byte[] bodyBytes = new byte[0];
            String postData = bodyMap.get("postData");
            if (postData != null && !postData.isEmpty()) {
                bodyBytes = postData.getBytes(StandardCharsets.UTF_8);
                // Log body snippet (first 200 chars)
                String bodyPreview = postData.length() > 200 ? postData.substring(0, 200) + "..." : postData;
                log("Body (" + postData.length() + " chars): " + bodyPreview);
            } else {
                log("Body: (empty)");
            }

            // Find provider
            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                log("✗ No provider with API Key configured");
                log("━━━ END (no provider) ━━━");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"No API provider with Key configured. Open API Proxy app and add your API Key.\"}");
            }

            // Build target URL
            String base = provider.getBaseUrl();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String targetUrl = base + uri;
            log("Provider: " + provider.getName() + " [" + provider.getId() + "]");
            log("Target: " + targetUrl);
            log("Key Header: " + provider.getKeyHeader() + ": " + provider.getMaskedApiKey());

            return forward(targetUrl, provider, session, bodyBytes);
        } catch (Exception e) {
            log("✗ ERROR: " + e.getMessage());
            log("━━━ END (error) ━━━");
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private ApiProvider findProvider(String uri) {
        if (providerManager == null) return null;
        for (ApiProvider p : providerManager.getAllProviders()) {
            if (p.hasApiKey()) return p;
        }
        return null;
    }

    private Response forward(String targetUrl, ApiProvider provider, IHTTPSession session, byte[] bodyBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);

            // Inject API Key
            conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());

            // Forward headers
            int headerCount = 0;
            for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
                String k = h.getKey();
                if (k == null) continue;
                String lk = k.toLowerCase();
                if (!lk.equals("host") && !k.equalsIgnoreCase(provider.getKeyHeader()) && !lk.equals("content-length")) {
                    conn.setRequestProperty(k, h.getValue());
                    headerCount++;
                }
            }
            log("Forwarded " + headerCount + " headers");

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
                log("Sent " + bodyBytes.length + " bytes");
            }

            log("Waiting for response...");

            int code = conn.getResponseCode();
            String respMsg = conn.getResponseMessage();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();

            if (is == null) {
                log("← " + code + " " + respMsg + " (no body)");
                log("━━━ END ━━━");
                Response.IStatus st = Response.Status.lookup(code);
                return newFixedLengthResponse(st != null ? st : Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"empty response (" + code + ")\"}");
            }

            // Log response headers
            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/json";
            log("Response Headers:");
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null) {
                    log("  " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
                }
            }

            // Buffer full response
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) { bos.write(buf, 0, read); }
            byte[] respBytes = bos.toByteArray();
            is.close();

            // Log response body preview
            String respPreview = "";
            if (respBytes.length > 0 && contentType.contains("json") || contentType.contains("text")) {
                String respStr = new String(respBytes, StandardCharsets.UTF_8);
                respPreview = respStr.length() > 300 ? respStr.substring(0, 300) + "..." : respStr;
            }

            Response.IStatus status = Response.Status.lookup(code);
            if (status == null) status = Response.Status.INTERNAL_ERROR;

            Response response = newFixedLengthResponse(status, contentType,
                new ByteArrayInputStream(respBytes), respBytes.length);

            // Copy response headers
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null &&
                    !entry.getKey().equalsIgnoreCase("Content-Length") &&
                    !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String v : entry.getValue()) response.addHeader(entry.getKey(), v);
                }
            }

            conn.disconnect();

            log("← " + code + " " + respMsg + " [" + respBytes.length + " bytes]");
            if (!respPreview.isEmpty()) log("Body: " + respPreview);
            log("━━━ END ━━━");
            return response;

        } catch (IOException e) {
            log("✗ FWD ERROR: " + e.getMessage());
            log("━━━ END (io error) ━━━");
            if (conn != null) conn.disconnect();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
