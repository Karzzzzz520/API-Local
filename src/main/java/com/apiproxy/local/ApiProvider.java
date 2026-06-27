package com.apiproxy.local;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * API 服务商配置模型
 * 支持：API Key 模式 / CLI 账号邮箱登录模式
 */
public class ApiProvider {
    private String id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String loginType;  // "api_key" 或 "email"
    private String email;      // CLI 邮箱登录模式的邮箱
    private boolean enabled;

    public ApiProvider(String id, String name, String baseUrl, String apiKey, boolean enabled) {
        this(id, name, baseUrl, apiKey, "api_key", "", enabled);
    }

    public ApiProvider(String id, String name, String baseUrl, String apiKey,
                       String loginType, String email, boolean enabled) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.loginType = loginType == null ? "api_key" : loginType;
        this.email = email == null ? "" : email;
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

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? "" : email; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getLocalEndpoint(int port) {
        return "http://localhost:" + port + "/" + name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public String getPathPrefix() {
        return "/" + name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

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
            json.put("loginType", loginType);
            json.put("email", email);
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
                    json.optString("loginType", "api_key"),
                    json.optString("email", ""),
                    json.optBoolean("enabled", true)
            );
        } catch (Exception e) {
            return null;
        }
    }
}