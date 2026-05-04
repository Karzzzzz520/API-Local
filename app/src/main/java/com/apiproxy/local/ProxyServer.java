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
            
            String targetUrl = resolveTargetUrl(request);
            if (targetUrl == null) {
                sendError(clientSocket, 400, "No valid target API configured");
                return;
            }

            ApiProvider provider = findProvider(request);
            if (provider == null) {
                sendError(clientSocket, 400, "No matching API provider found");
                return;
            }

            log("Forwarding to: " + targetUrl);

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

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    request.headers.put(key, value);
                }
            }

            if (request.headers.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(request.headers.get("Content-Length"));
                char[] body = new char[contentLength];
                int read = reader.read(body);
                if (read > 0) {
                    request.body = new String(body, 0, read);
                }
            }

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
        String targetUrl = request.headers.get("X-Target-URL");
        if (targetUrl != null && !targetUrl.isEmpty()) {
            return targetUrl;
        }

        String path = request.path;
        if (path.startsWith("/v1/")) {
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && path.startsWith("/v1")) {
                    return provider.getBaseUrl() + path;
                }
            }
        } else if (path.startsWith("/api/")) {
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && provider.getId().equals("gemini")) {
                    return provider.getBaseUrl() + path;
                }
            }
        } else if (path.startsWith("/anthropic/")) {
            for (ApiProvider provider : providerManager.getAllProviders()) {
                if (provider.hasApiKey() && provider.getId().equals("claude")) {
                    return provider.getBaseUrl() + path;
                }
            }
        }

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
            
            // Use HttpsURLConnection for https URLs
            javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection();
            conn.setRequestMethod(request.method);
            conn.setDoInput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(0); // No read timeout for streaming
            
            // Add API Key header
            conn.setRequestProperty(provider.getKeyHeader(), provider.getFullKeyValue());
            
            // Add other headers
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                String key = entry.getKey();
                if (!key.equalsIgnoreCase("Host") && 
                    !key.equalsIgnoreCase(provider.getKeyHeader()) &&
                    !key.equalsIgnoreCase("X-Target-URL") &&
                    !key.equalsIgnoreCase("Content-Length")) {
                    conn.setRequestProperty(key, entry.getValue());
                }
            }
            
            // Write body if present
            if (request.body != null && !request.body.isEmpty()) {
                conn.setDoOutput(true);
                byte[] bodyBytes = request.body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                OutputStream out = conn.getOutputStream();
                out.write(bodyBytes);
                out.flush();
            }

            log("Request forwarded, receiving response...");

            // Read response
            int responseCode = conn.getResponseCode();
            java.io.InputStream responseStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            
            if (responseStream == null) {
                sendError(clientSocket, 502, "No response from target API");
                return;
            }
            
            // Build HTTP response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("HTTP/1.1 ").append(responseCode).append(" ")
                .append(getStatusText(responseCode)).append("\r\n");
            
            // Response headers
            for (Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null) {
                    for (String value : entry.getValue()) {
                        responseBuilder.append(entry.getKey()).append(": ").append(value).append("\r\n");
                    }
                }
            }
            responseBuilder.append("\r\n");
            
            OutputStream clientOut = clientSocket.getOutputStream();
            clientOut.write(responseBuilder.toString().getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
            
            // Stream response body
            byte[] buffer = new byte[8192];
            int read;
            while ((read = responseStream.read(buffer)) > 0) {
                clientOut.write(buffer, 0, read);
                clientOut.flush();
            }
            
            clientOut.flush();
            log("Response forwarded complete");
            
            conn.disconnect();

        } catch (IOException e) {
            log("Forward error: " + e.getMessage());
            sendError(clientSocket, 502, "Proxy forward failed: " + e.getMessage());
        }
    }
    
    private String getStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Unknown";
        }
    }

    private void sendError(Socket clientSocket, int code, String message) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            String body = "{\"error\": \"" + message + "\"}";
            String response = "HTTP/1.1 " + code + " " + message + "\r\n" +
                             "Content-Type: application/json\r\n" +
                             "Content-Length: " + body.length() + "\r\n" +
                             "\r\n" + body;
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
