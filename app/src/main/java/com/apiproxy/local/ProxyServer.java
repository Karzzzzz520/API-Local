package com.apiproxy.local;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
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

public class ProxyServer extends NanoHTTPD {
    private static final String TAG = "ProxyServer";

    public interface LogCallback {
        void onLog(String message);
    }

    private LogCallback logCallback;
    private ProviderManager providerManager;

    public ProxyServer(int port) {
        super("127.0.0.1", port);
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    public void setProviderManager(ProviderManager manager) {
        this.providerManager = manager;
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (logCallback != null) logCallback.onLog(message);
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Method method = session.getMethod();
            log(method + " " + uri);

            // Read body properly
            byte[] bodyBytes = readBody(session);

            // Find provider
            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                log("No provider with API Key configured");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"No API provider configured. Add an API Key first.\"}");
            }

            // Build target URL
            String targetUrl = provider.getBaseUrl() + uri;
            log("-> " + targetUrl);

            // Forward
            return forwardRequest(session, targetUrl, provider, bodyBytes);

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                "application/json", "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private byte[] readBody(IHTTPSession session) throws IOException {
        // NanoHTTPD stores body in inputStream after parseBody
        Map<String, String> bodyMap = new HashMap<>();
        try {
            session.parseBody(bodyMap);
        } catch (Exception e) {
            log("Parse body: " + e.getMessage());
        }
        
        String postData = bodyMap.get("postData");
        if (postData != null && !postData.isEmpty()) {
            return postData.getBytes(StandardCharsets.UTF_8);
        }
        
        // Fallback: read from content-length
        long contentLen = getContentLength(session);
        if (contentLen <= 0) return new byte[0];
        
        InputStream is = session.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        long remaining = contentLen;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int read = is.read(buf, 0, toRead);
            if (read <= 0) break;
            bos.write(buf, 0, read);
            remaining -= read;
        }
        return bos.toByteArray();
    }

    private long getContentLength(IHTTPSession session) {
        String cl = session.getHeaders().get("content-length");
        if (cl == null) cl = session.getHeaders().get("Content-Length");
        if (cl != null) {
            try { return Long.parseLong(cl); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private ApiProvider findProvider(String uri) {
        if (providerManager == null) return null;

        // Route by path prefix
        if (uri.startsWith("/gemini/")) {
            for (ApiProvider p : providerManager.getAllProviders()) {
                if (p.hasApiKey() && p.getId().equals("gemini")) return p;
            }
        } else if (uri.startsWith("/claude/")) {
            for (ApiProvider p : providerManager.getAllProviders()) {
                if (p.hasApiKey() && p.getId().equals("claude")) return p;
            }
        } else if (uri.startsWith("/deepseek/")) {
            for (ApiProvider p : providerManager.getAllProviders()) {
                if (p.hasApiKey() && p.getId().equals("deepseek")) return p;
            }
        } else if (uri.startsWith("/openai/")) {
            for (ApiProvider p : providerManager.getAllProviders()) {
                if (p.hasApiKey() && p.getId().equals("openai")) return p;
            }
        }

        // Default: first provider with key
        for (ApiProvider p : providerManager.getAllProviders()) {
            if (p.hasApiKey()) return p;
        }
        return null;
    }

    private Response forwardRequest(IHTTPSession session, String targetUrl,
                                     ApiProvider provider, byte[] bodyBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);

            // Inject API Key
            conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());

            // Forward all headers
            for (Map.Entry<String, String> header : session.getHeaders().entrySet()) {
                String key = header.getKey();
                if (key == null) continue;
                String lowerKey = key.toLowerCase();
                if (!lowerKey.equals("host") &&
                    !key.equalsIgnoreCase(provider.getKeyHeader()) &&
                    !lowerKey.equals("content-length")) {
                    conn.setRequestProperty(key, header.getValue());
                }
            }

            // Write body
            if (bodyBytes != null && bodyBytes.length > 0) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                // Ensure content-type is set
                if (conn.getRequestProperty("Content-Type") == null &&
                    conn.getRequestProperty("content-type") == null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                }
                OutputStream out = conn.getOutputStream();
                out.write(bodyBytes);
                out.flush();
                out.close();
            }

            // Read response
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                log("No response, code=" + code);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"Empty response from upstream\"}");
            }

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/json";

            boolean streaming = contentType.contains("text/event-stream");

            // Build response status
            Response.IStatus status = Response.Status.lookup(code);
            if (status == null) status = Response.Status.INTERNAL_ERROR;

            Response response;
            int contentLen = conn.getContentLength();
            if (streaming || contentLen <= 0) {
                response = newChunkedResponse(status, contentType, is);
            } else {
                response = newFixedLengthResponse(status, contentType, is, contentLen);
            }

            // Copy response headers
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null &&
                    !entry.getKey().equalsIgnoreCase("Content-Length") &&
                    !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String val : entry.getValue()) {
                        response.addHeader(entry.getKey(), val);
                    }
                }
            }

            log("<- " + code);
            return response;

        } catch (IOException e) {
            log("Forward error: " + e.getMessage());
            if (conn != null) conn.disconnect();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                "application/json", "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
