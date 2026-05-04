package com.apiproxy.local;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 本地HTTP代理服务器 - 基于NanoHTTPD
 * 监听localhost指定端口，将请求转发到目标API并注入API Key
 */
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
        if (logCallback != null) {
            logCallback.onLog(message);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Method method = session.getMethod();
            
            log(method + " " + uri);

            // 获取请求体
            Map<String, String> bodyMap = new HashMap<>();
            if (method == Method.POST || method == Method.PUT || method == Method.PATCH) {
                try {
                    session.parseBody(bodyMap);
                } catch (Exception e) {
                    log("Parse body error: " + e.getMessage());
                }
            }
            String requestBody = bodyMap.get("postData");

            // 找provider
            ApiProvider provider = findProvider(uri);
            if (provider == null) {
                log("No provider found for: " + uri);
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, 
                    "application/json", "{\"error\":\"No API provider configured. Please add an API Key first.\"}");
            }

            // 构建目标URL
            String targetUrl = provider.getBaseUrl() + uri;
            log("Forwarding to: " + targetUrl);

            // 转发请求
            return forwardRequest(session, targetUrl, provider, requestBody);

        } catch (Exception e) {
            log("Serve error: " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private ApiProvider findProvider(String uri) {
        if (providerManager == null) return null;

        // 根据路径前缀匹配
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

        // 默认：第一个有Key的provider
        for (ApiProvider p : providerManager.getAllProviders()) {
            if (p.hasApiKey()) return p;
        }
        return null;
    }

    private Response forwardRequest(IHTTPSession session, String targetUrl, 
                                     ApiProvider provider, String requestBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(0);
            conn.setDoInput(true);

            // 注入API Key
            conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());

            // 复制其他headers
            for (Map.Entry<String, String> header : session.getHeaders().entrySet()) {
                String key = header.getKey();
                if (key == null) continue;
                String lowerKey = key.toLowerCase();
                // 跳过host、已有的key header、content-length（后面自动处理）
                if (!lowerKey.equals("host") && 
                    !key.equalsIgnoreCase(provider.getKeyHeader()) &&
                    !lowerKey.equals("content-length")) {
                    conn.setRequestProperty(key, header.getValue());
                }
            }

            // 写body
            if (requestBody != null && !requestBody.isEmpty()) {
                conn.setDoOutput(true);
                byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                OutputStream out = conn.getOutputStream();
                out.write(bodyBytes);
                out.flush();
                out.close();
            }

            // 读取响应
            int responseCode = conn.getResponseCode();
            InputStream responseStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            
            if (responseStream == null) {
                log("No response stream, code: " + responseCode);
                return newFixedLengthResponse(Response.Status.BAD_GATEWAY,
                    "application/json", "{\"error\":\"No response from upstream\"}");
            }

            // 检查是否流式
            String contentType = conn.getContentType();
            boolean isStreaming = contentType != null && 
                (contentType.contains("text/event-stream") || contentType.contains("text/plain"));

            // 复制响应headers
            Response.IStatus status = Response.Status.lookup(responseCode);
            if (status == null) status = Response.Status.INTERNAL_ERROR;
            
            Response response;
            if (isStreaming) {
                // 流式：用chunked response
                response = newChunkedResponse(status, contentType, responseStream);
                log("Streaming response started");
            } else {
                // 非流式：读取完整响应
                int contentLength = conn.getContentLength();
                if (contentLength > 0) {
                    response = newFixedLengthResponse(status, contentType, responseStream, contentLength);
                } else {
                    response = newChunkedResponse(status, contentType, responseStream);
                }
            }

            // 复制响应headers
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().equalsIgnoreCase("Content-Length") 
                    && !entry.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                    for (String val : entry.getValue()) {
                        response.addHeader(entry.getKey(), val);
                    }
                }
            }

            log("Response: " + responseCode);
            return response;

        } catch (IOException e) {
            log("Forward error: " + e.getMessage());
            if (conn != null) conn.disconnect();
            return newFixedLengthResponse(Response.Status.BAD_GATEWAY,
                "application/json", "{\"error\":\"Proxy error: " + e.getMessage() + "\"}");
        }
    }
}
