package com.apiproxy.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量级 HTTP 代理服务器
 * 监听 localhost:port，将请求转发到对应 API 服务商
 */
public class ProxyServer {

    public interface LogCallback {
        void onLog(String message);
    }

    public interface StatusCallback {
        void onStatusChanged(boolean running);
    }

    private final int port;
    private final List<ApiProvider> providers;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private LogCallback logCallback;
    private StatusCallback statusCallback;

    public ProxyServer(int port, List<ApiProvider> providers) {
        this.port = port;
        this.providers = providers;
    }

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    public void setStatusCallback(StatusCallback callback) {
        this.statusCallback = callback;
    }

    private void log(String msg) {
        if (logCallback != null) logCallback.onLog(msg);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() throws IOException {
        if (running.get()) return;

        serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1"));
        running.set(true);

        if (statusCallback != null) statusCallback.onStatusChanged(true);
        log("🚀 代理服务器已启动，监听端口: " + port);

        threadPool.submit(() -> {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    threadPool.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running.get()) {
                        log("❌ 接受连接错误: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("⚠️ 关闭服务器错误: " + e.getMessage());
        }

        if (statusCallback != null) statusCallback.onStatusChanged(false);
        log("🛑 代理服务器已停止");
    }

    private void handleClient(Socket client) {
        try (
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream()
        ) {
            // 读取 HTTP 请求
            String request = readHttpRequest(in);
            if (request == null || request.isEmpty()) {
                sendError(out, 400, "Bad Request");
                return;
            }

            // 解析请求行
            String[] lines = request.split("\r\n");
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length < 3) {
                sendError(out, 400, "Bad Request");
                return;
            }

            String method = requestLine[0];
            String path = requestLine[1];

            log("📥 " + method + " " + path);

            // 健康检查端点
            if (path.equals("/health") || path.equals("/")) {
                String body = "{\"status\":\"running\",\"port\":" + port + ",\"providers\":" + getActiveProvidersCount() + "}";
                sendJsonResponse(out, 200, body);
                return;
            }

            // 查找匹配的服务商
            ApiProvider matchedProvider = null;
            String remainingPath = null;

            for (ApiProvider provider : providers) {
                if (!provider.isEnabled()) continue;
                String prefix = provider.getPathPrefix();
                if (path.startsWith(prefix)) {
                    matchedProvider = provider;
                    remainingPath = path.substring(prefix.length());
                    if (remainingPath.isEmpty()) remainingPath = "/";
                    break;
                }
            }

            if (matchedProvider == null) {
                sendError(out, 404, "No matching API provider found for path: " + path + 
                        ". Available: " + getAvailablePaths());
                return;
            }

            // 读取请求体
            String body = "";
            int bodyStart = request.indexOf("\r\n\r\n");
            if (bodyStart > 0) {
                body = request.substring(bodyStart + 4);
            }

            // 转发请求
            String targetUrl = matchedProvider.getBaseUrl() + remainingPath;
            log("➡️ 转发到: " + matchedProvider.getName() + " -> " + targetUrl);

            String response = forwardRequest(method, targetUrl, matchedProvider.getApiKey(), body, request);

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            log("✅ " + matchedProvider.getName() + " 响应已返回");

        } catch (Exception e) {
            log("❌ 处理请求错误: " + e.getMessage());
            try {
                sendError(client.getOutputStream(), 500, "Internal Server Error: " + e.getMessage());
            } catch (IOException ignored) {}
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {}
        }
    }

    private String readHttpRequest(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder request = new StringBuilder();
        String line;
        int contentLength = 0;
        boolean headerEnd = false;

        while ((line = reader.readLine()) != null) {
            request.append(line).append("\r\n");
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
            if (line.isEmpty()) {
                headerEnd = true;
                break;
            }
        }

        if (headerEnd && contentLength > 0) {
            char[] body = new char[contentLength];
            int read = reader.read(body, 0, contentLength);
            if (read > 0) {
                request.append(body, 0, read);
            }
        }

        return request.toString();
    }

    @SuppressWarnings("deprecation")
    private String forwardRequest(String method, String targetUrl, String apiKey, String body, String originalRequest) throws IOException {
        URL url;
        try {
            url = new URI(targetUrl).toURL();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Invalid URI: " + targetUrl, e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod(method);
        conn.setDoInput(true);

        // 设置认证头
        if (apiKey != null && !apiKey.isEmpty()) {
            if (targetUrl.contains("generativelanguage.googleapis.com")) {
                // Gemini: API Key 作为 query parameter
                String separator = targetUrl.contains("?") ? "&" : "?";
                String urlStr = targetUrl + separator + "key=" + apiKey;
                URL newUrl;
                try {
                    newUrl = new URI(urlStr).toURL();
                } catch (java.net.URISyntaxException e) {
                    throw new IOException("Invalid URI: " + urlStr, e);
                }
                conn.disconnect();
                conn = (HttpURLConnection) newUrl.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setRequestMethod(method);
                conn.setDoInput(true);
            } else if (targetUrl.contains("anthropic.com")) {
                // Claude: x-api-key header
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                // OpenAI 及其他: Bearer token
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
        }

        // 设置 Content-Type
        conn.setRequestProperty("Content-Type", "application/json");

        // 设置 Host
        try {
            conn.setRequestProperty("Host", new URI(targetUrl).getHost());
        } catch (Exception ignored) {}

        // 发送请求体
        if (!body.isEmpty() && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        // 读取响应
        int responseCode = conn.getResponseCode();
        StringBuilder responseBody = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8
                ))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                responseBody.append(responseLine);
            }
        } catch (Exception e) {
            responseBody.append("{\"error\":\"").append(e.getMessage()).append("\"}");
        }

        // 构建 HTTP 响应
        return buildHttpResponse(responseCode, responseBody.toString());
    }

    private String buildHttpResponse(int statusCode, String body) {
        String statusText;
        switch (statusCode) {
            case 200: statusText = "OK"; break;
            case 201: statusText = "Created"; break;
            case 400: statusText = "Bad Request"; break;
            case 401: statusText = "Unauthorized"; break;
            case 403: statusText = "Forbidden"; break;
            case 404: statusText = "Not Found"; break;
            case 429: statusText = "Too Many Requests"; break;
            case 500: statusText = "Internal Server Error"; break;
            default: statusText = "Unknown"; break;
        }

        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                body;
    }

    private void sendJsonResponse(OutputStream out, int code, String body) throws IOException {
        out.write(buildHttpResponse(code, body).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendError(OutputStream out, int code, String message) throws IOException {
        String body = "{\"error\":\"" + message + "\"}";
        sendJsonResponse(out, code, body);
    }

    private int getActiveProvidersCount() {
        int count = 0;
        for (ApiProvider p : providers) {
            if (p.isEnabled()) count++;
        }
        return count;
    }

    private String getAvailablePaths() {
        StringBuilder sb = new StringBuilder();
        for (ApiProvider p : providers) {
            if (p.isEnabled()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getPathPrefix());
            }
        }
        return sb.toString();
    }
}