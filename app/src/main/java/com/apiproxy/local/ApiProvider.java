package com.apiproxy.local;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * API服务商配置模型
 */
public class ApiProvider {
    @SerializedName("id")
    private String id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("baseUrl")
    private String baseUrl;
    
    @SerializedName("apiKey")
    private String apiKey;
    
    @SerializedName("keyHeader")
    private String keyHeader;
    
    @SerializedName("keyPrefix")
    private String keyPrefix;
    
    @SerializedName("models")
    private List<String> models;
    
    @SerializedName("enabled")
    private boolean enabled;

    public ApiProvider() {
        this.keyPrefix = "Bearer ";
        this.enabled = true;
    }

    public ApiProvider(String id, String name, String baseUrl, String apiKey, 
                       String keyHeader, String keyPrefix, List<String> models) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.keyHeader = keyHeader;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "";
        this.models = models;
        this.enabled = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getKeyHeader() { return keyHeader; }
    public void setKeyHeader(String keyHeader) { this.keyHeader = keyHeader; }

    public String getKeyPrefix() { return keyPrefix != null ? keyPrefix : ""; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix != null ? keyPrefix : ""; }

    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public String getFullKeyValue() {
        return (keyPrefix != null ? keyPrefix : "") + apiKey;
    }

    public static class Preset {
        public static final ApiProvider OPENAI = new ApiProvider(
            "openai", "OpenAI", "https://api.openai.com", "",
            "Authorization", "Bearer ", null
        );
        
        public static final ApiProvider GEMINI = new ApiProvider(
            "gemini", "Gemini", "https://generativelanguage.googleapis.com", "",
            "x-goog-api-key", "", null
        );
        
        public static final ApiProvider CLAUDE = new ApiProvider(
            "claude", "Claude", "https://api.anthropic.com", "",
            "x-api-key", "", null
        );
        
        public static final ApiProvider DEEPSEEK = new ApiProvider(
            "deepseek", "DeepSeek", "https://api.deepseek.com", "",
            "Authorization", "Bearer ", null
        );
    }
}
