package com.apiproxy.local;
import org.json.JSONException;
import org.json.JSONObject;
public class CliAccount {
    private String id;
    private String name;
    private String provider;
    private String loginType;
    private String baseUrl;
    private String apiKey;
    private String email;
    private String token;
    private boolean enabled;
    public CliAccount(String id, String name, String provider, String loginType, String baseUrl, String apiKey, String email, String token, boolean enabled) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.loginType = loginType;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.email = email == null ? "" : email;
        this.token = token == null ? "" : token;
        this.enabled = enabled;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? "" : email; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token == null ? "" : token; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMaskedSecret() {
        String secret = !apiKey.isEmpty() ? apiKey : token;
        if (secret == null || secret.length() < 8) return "***";
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }
    public ApiProvider toApiProvider() {
        String effectiveKey = !apiKey.isEmpty() ? apiKey : token;
        String effectiveUrl = baseUrl;
        if (effectiveUrl.isEmpty()) {
            switch (provider.toLowerCase()) {
                case "gpt": effectiveUrl = "https://api.openai.com"; break;
                case "gemini": effectiveUrl = "https://generativelanguage.googleapis.com"; break;
                case "claude": effectiveUrl = "https://api.anthropic.com"; break;
                default: effectiveUrl = ""; break;
            }
        }
        return new ApiProvider(id, name, effectiveUrl, effectiveKey, enabled);
    }
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("provider", provider);
            json.put("loginType", loginType);
            json.put("baseUrl", baseUrl);
            json.put("apiKey", apiKey);
            json.put("email", email);
            json.put("token", token);
            json.put("enabled", enabled);
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    public static CliAccount fromJson(JSONObject json) {
        try {
            return new CliAccount(
                    json.optString("id", ""),
                    json.optString("name", ""),
                    json.optString("provider", "gpt"),
                    json.optString("loginType", "api_key"),
                    json.optString("baseUrl", ""),
                    json.optString("apiKey", ""),
                    json.optString("email", ""),
                    json.optString("token", ""),
                    json.optBoolean("enabled", true)
            );
        } catch (Exception e) {
            return null;
        }
    }
}