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
        try {
            String uri = session.getUri();
            Method method = session.getMethod();
            log(method + " " + uri);

            Map<String, String> bodyMap = new HashMap<>();
            try { session.parseBody(bodyMap); } catch (Exception e) { log("body: " + e.getMessage()); }
            byte[] bodyBytes = new byte[0];
            String postData = bodyMap.get("postData");
            if (postData != null && !postData.isEmpty()) bodyBytes = postData.getBytes(StandardCharsets.UTF_8);

            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"No provider with API Key\"}");
            }

            String targetUrl = provider.getBaseUrl() + uri;
            log("-> " + targetUrl);

            return forward(targetUrl, provider, session, bodyBytes);
        } catch (Exception e) {
            log("ERR: " + e.getMessage());
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
            for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
                String k = h.getKey();
                if (k == null) continue;
                String lk = k.toLowerCase();
                if (!lk.equals("host") && !k.equalsIgnoreCase(provider.getKeyHeader()) && !lk.equals("content-length")) {
                    conn.setRequestProperty(k, h.getValue());
                }
            }

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

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                log("<- " + code + " (no body)");
                return newFixedLengthResponse(Response.Status.lookup(code) != null ? Response.Status.lookup(code) : Response.Status.INTERNAL_ERROR,
                    "application/json", "{\"error\":\"empty response\"}");
            }

            // Read FULL response into buffer (reliable, no stream hanging)
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

            // Copy response headers
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null &&
                    !entry.getKey().equalsIgnoreCase("Content-Length") &&
                    !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String v : entry.getValue()) response.addHeader(entry.getKey(), v);
                }
            }

            conn.disconnect();
            log("<- " + code + " (" + respBytes.length + " bytes)");
            return response;

        } catch (IOException e) {
            log("FWD ERR: " + e.getMessage());
            if (conn != null) conn.disconnect();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
