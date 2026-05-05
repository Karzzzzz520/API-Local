package com.apiproxy.local;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * API 服务商配置模型
 */
public class ApiProvider {
    private String id;        // 唯一ID
    private String name;      // 显示名称
    private String baseUrl;   // API 基础地址, 如 https://api.openai.com
    private String apiKey;    // API 密钥
    private boolean enabled;  // 是否启用

    public ApiProvider(String id, String name, String baseUrl, String apiKey, boolean enabled) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 获取本地代理端点 */
    public String getLocalEndpoint(int port) {
        return "http://localhost:" + port + "/" + name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** 获取路径前缀 (用于路由) */
    public String getPathPrefix() {
        return "/" + name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** 掩码显示 API Key */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() < 8) return "***";
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("baseUrl", baseUrl);
            json.put("apiKey", apiKey);
            json.put("enabled", enabled);
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static ApiProvider fromJson(JSONObject json) {
        try {
            return new ApiProvider(
                    json.optString("id", ""),
                    json.optString("name", ""),
                    json.optString("baseUrl", ""),
                    json.optString("apiKey", ""),
                    json.optBoolean("enabled", true)
            );
        } catch (Exception e) {
            return null;
        }
    }
}