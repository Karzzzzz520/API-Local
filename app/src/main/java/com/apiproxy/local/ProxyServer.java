package com.apiproxy.local;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地HTTP代理服务器
 * 监听localhost指定端口，转发请求到目标API并注入API Key
 */
public class ProxyServer {
    private static final String TAG = "ProxyServer";
    
    public interface LogCallback {
        void onLog(String message);
    }

    private int port;
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final AtomicBoolean running;
    private LogCallback logCallback;
    private ProviderManager providerManager;

    public ProxyServer(int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);
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

    public boolean start() {
        if (running.get()) {
            log("Server already running");
            return true;
        }

        try {
            serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getLoopbackAddress());
            running.set(true);
            log("Proxy server started on localhost:" + port);
            
            executorService.execute(this::acceptConnections);
            return true;
        } catch (IOException e) {
            log("Failed to start server: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdownNow();
            log("Proxy server stopped");
        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(30000);
                executorService.execute(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    log("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            HttpRequest request = parseRequest(clientSocket);
            if (request == null) {
                clientSocket.close();
                return;
            }

            log("Request: " + request.method + " " + request.path);
            
            // 确定目标URL
            String targetUrl = resolveTargetUrl(request);
            if (targetUrl == null) {
                sendError(clientSocket, 400, "No valid target API configured");
                return;
            }

            // 找到对应的provider
            ApiProvider provider = findProvider(request);
            if (provider == null) {
                sendError(clientSocket, 400, "No matching API provider found");
                return;
            }

            log("Forwarding to: " + targetUrl);

            // 转发请求
            forwardRequest(clientSocket, request, targetUrl, provider);

        } catch (Exception e) {
            log("Handle client error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private HttpRequest parseRequest(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );

            String firstLine = reader.readLine();
            if (firstLine == null) return null;

            String[] parts = firstLine.split(" ");
            if (parts.length < 2) return null;

            HttpRequest request = new HttpRequest();
            request.method = parts[0];
            request.path = parts[1];

            // 解析headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    request.headers.put(key, value);
                }
            }

            // 读取body
            if (request.headers.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(request.headers.get("Content-Length"));
                char[] body = new char[contentLength];
                int read = reader.read(body);
                if (read > 0) {
                    request.body = new String(body, 0, read);
                }
            }

            // 检查是否SSE/流式请求
            String accept = request.headers.get("Accept");
            if (accept != null && accept.contains("text/event-stream")) {
                request.isStreaming = true;
            }

            return request;
        } catch (IOException e) {
            log("Parse request error: " + e.getMessage());
            return null;
        }
    }

    private String resolveTargetUrl(HttpRequest request) {
        // 首先检查X-Target-URL header
        String targetUrl = request.headers.get("X-Target-URL");
        if (targetUrl != null && !targetUrl.isEmpty()) {
            return targetUrl;
        }

        // 从path解析provider
        String path = request.path;
        if (path.startsWith("/v1/")) {
            // OpenAI兼容格式
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && path.startsWith("/v1")) {
                    return provider.getBaseUrl() + path;
                }
            }
        } else if (path.startsWith("/api/")) {
            // Gemini格式
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && provider.getId().equals("gemini")) {
                    return provider.getBaseUrl() + path;
                }
            }
        } else if (path.startsWith("/anthropic/")) {
            // Claude格式
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && provider.getId().equals("claude")) {
                    return provider.getBaseUrl() + path;
                }
            }
        }

        // 返回第一个有配置的provider
        for (ApiProvider provider : providerManager.getAllProviders()) {
            if (provider.hasApiKey()) {
                return provider.getBaseUrl() + path;
            }
        }

        return null;
    }

    private ApiProvider findProvider(HttpRequest request) {
        String targetUrl = request.headers.get("X-Target-URL");
        if (targetUrl != null) {
            try {
                URL url = new URL(targetUrl);
                String host = url.getHost();
                for (ApiProvider provider : providerManager.getAllProviders()) {
                    if (provider.hasApiKey() && provider.getBaseUrl().contains(host)) {
                        return provider;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 匹配baseUrl
        for (ApiProvider provider : providerManager.getAllProviders()) {
            if (provider.hasApiKey()) {
                return provider;
            }
        }

        return null;
    }

    private void forwardRequest(Socket clientSocket, HttpRequest request, 
                                 String targetUrl, ApiProvider provider) {
        try {
            URL url = new URL(targetUrl);
            int urlPort = url.getPort() != -1 ? url.getPort() : 
                          (url.getProtocol().equals("https") ? 443 : 80);
            
            Socket targetSocket = new Socket(url.getHost(), urlPort);
            targetSocket.setSoTimeout(0); // 无超时，支持长连接

            // 构建转发请求
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(request.method).append(" ").append(url.getFile())
                         .append(" HTTP/1.1\r\n");
            
            // Host header
            requestBuilder.append("Host: ").append(url.getHost());
            if (urlPort != 80 && urlPort != 443) {
                requestBuilder.append(":").append(urlPort);
            }
            requestBuilder.append("\r\n");

            // 添加API Key
            requestBuilder.append(provider.getKeyHeader()).append(": ")
                         .append(provider.getFullKeyValue()).append("\r\n");

            // 添加其他headers
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                String key = entry.getKey();
                if (!key.equalsIgnoreCase("Host") && 
                    !key.equalsIgnoreCase(provider.getKeyHeader()) &&
                    !key.equalsIgnoreCase("X-Target-URL") &&
                    !key.equalsIgnoreCase("Content-Length")) {
                    requestBuilder.append(key).append(": ").append(entry.getValue())
                                 .append("\r\n");
                }
            }

            // Content-Length
            if (request.body != null) {
                requestBuilder.append("Content-Length: ").append(request.body.length())
                             .append("\r\n");
            }

            requestBuilder.append("\r\n");

            OutputStream out = targetSocket.getOutputStream();
            out.write(requestBuilder.toString().getBytes(StandardCharsets.UTF_8));
            
            if (request.body != null) {
                out.write(request.body.getBytes(StandardCharsets.UTF_8));
            }
            out.flush();

            log("Request forwarded, receiving response...");

            // 转发响应
            forwardResponse(targetSocket, clientSocket, request.isStreaming);

        } catch (IOException e) {
            log("Forward error: " + e.getMessage());
            sendError(clientSocket, 502, "Proxy forward failed: " + e.getMessage());
        }
    }

    private void forwardResponse(Socket targetSocket, Socket clientSocket, boolean isStreaming) {
        try {
            InputStreamReader reader = new InputStreamReader(targetSocket.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(reader);
            OutputStream clientOut = clientSocket.getOutputStream();

            // 读取状态行
            String statusLine = bufferedReader.readLine();
            if (statusLine == null) return;

            clientOut.write((statusLine + "\r\n").getBytes(StandardCharsets.UTF_8));

            // 读取headers
            Map<String, String> responseHeaders = new HashMap<>();
            String line;
            int contentLength = -1;
            boolean chunked = false;

            while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
                clientOut.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
                
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim().toLowerCase();
                    String value = line.substring(colonIndex + 1).trim();
                    responseHeaders.put(key, value);
                    
                    if (key.equals("content-length")) {
                        contentLength = Integer.parseInt(value);
                    } else if (key.equals("transfer-encoding") && value.equalsIgnoreCase("chunked")) {
                        chunked = true;
                    }
                }
            }
            clientOut.write("\r\n".getBytes(StandardCharsets.UTF_8));
            clientOut.flush();

            log("Response headers sent, streaming: " + isStreaming);

            if (isStreaming || chunked) {
                // 流式响应
                forwardStreaming(bufferedReader, clientOut);
            } else if (contentLength > 0) {
                // 普通响应
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int remaining = contentLength - totalRead;
                    int toRead = Math.min(buffer.length, remaining);
                    int read = targetSocket.getInputStream().read(buffer, 0, toRead);
                    if (read <= 0) break;
                    clientOut.write(buffer, 0, read);
                    totalRead += read;
                }
            } else {
                // 读取全部
                byte[] buffer = new byte[8192];
                int read;
                while ((read = targetSocket.getInputStream().read(buffer)) > 0) {
                    clientOut.write(buffer, 0, read);
                }
            }

            clientOut.flush();
            log("Response forwarded complete");

        } catch (IOException e) {
            log("Forward response error: " + e.getMessage());
        } finally {
            try {
                targetSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void forwardStreaming(BufferedReader reader, OutputStream out) {
        try {
            char[] buffer = new char[8192];
            int lastRead = 0;
            
            // 使用带超时的读取来检测连接关闭
            while (!Thread.currentThread().isInterrupted()) {
                int ch = -1;
                try {
                    // 检查是否有数据可用
                    if (reader.ready()) {
                        ch = reader.read();
                        if (ch == -1) break;
                        
                        // 回写缓冲的数据
                        if (lastRead > 0) {
                            out.write(new String(buffer, 0, lastRead).getBytes(StandardCharsets.UTF_8));
                            lastRead = 0;
                        }
                        out.write(ch);
                        out.flush();
                    } else {
                        // 累积buffer
                        if (lastRead >= buffer.length) {
                            out.write(new String(buffer, 0, lastRead).getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            lastRead = 0;
                        }
                        buffer[lastRead++] = (char) ch;
                        
                        // 小延迟避免忙等
                        Thread.sleep(10);
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // 超时，检查是否需要继续
                    if (lastRead > 0) {
                        out.write(new String(buffer, 0, lastRead).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        lastRead = 0;
                    }
                }
            }
            
            // 发送剩余数据
            if (lastRead > 0) {
                out.write(new String(buffer, 0, lastRead).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            log("Streaming error: " + e.getMessage());
        }
    }

    private void sendError(Socket clientSocket, int code, String message) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            String response = "HTTP/1.1 " + code + " " + message + "\r\n" +
                             "Content-Type: application/json\r\n" +
                             "Content-Length: " + (message.length() + 30) + "\r\n" +
                             "\r\n" +
                             "{\"error\": \"" + message + "\"}";
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {}
    }

    private static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers = new HashMap<>();
        String body;
        boolean isStreaming;
    }
}
