package com.apiproxy.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider管理器 - 负责存储和加载API服务商配置
 */
public class ProviderManager {
    private static final String PREFS_NAME = "api_proxy_prefs";
    private static final String KEY_PROVIDERS = "providers";
    private static final String KEY_PORT = "port";
    private static final String KEY_DEFAULT_PORT = "8080";

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<ApiProvider> providers;

    public ProviderManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        loadProviders();
    }

    private void loadProviders() {
        String json = prefs.getString(KEY_PROVIDERS, null);
        if (json != null) {
            try {
                Type type = new TypeToken<List<ApiProvider>>(){}.getType();
                providers = gson.fromJson(json, type);
            } catch (Exception e) {
                providers = new ArrayList<>();
            }
        }
        
        if (providers == null || providers.isEmpty()) {
            // 添加默认预设
            providers = new ArrayList<>();
            providers.add(new ApiProvider("openai", "OpenAI", "https://api.openai.com", "",
                "Authorization", "Bearer ", null));
            providers.add(new ApiProvider("gemini", "Gemini", "https://generativelanguage.googleapis.com", "",
                "x-goog-api-key", "", null));
            providers.add(new ApiProvider("claude", "Claude", "https://api.anthropic.com", "",
                "x-api-key", "", null));
            providers.add(new ApiProvider("deepseek", "DeepSeek", "https://api.deepseek.com", "",
                "Authorization", "Bearer ", null));
            saveProviders();
        }
    }

    private void saveProviders() {
        String json = gson.toJson(providers);
        prefs.edit().putString(KEY_PROVIDERS, json).apply();
    }

    public List<ApiProvider> getAllProviders() {
        return new ArrayList<>(providers);
    }

    public ApiProvider getProvider(String id) {
        for (ApiProvider provider : providers) {
            if (provider.getId().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    public void addProvider(ApiProvider provider) {
        // 检查是否已存在
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).getId().equals(provider.getId())) {
                providers.set(i, provider);
                saveProviders();
                return;
            }
        }
        providers.add(provider);
        saveProviders();
    }

    public void updateProvider(ApiProvider provider) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).getId().equals(provider.getId())) {
                providers.set(i, provider);
                saveProviders();
                return;
            }
        }
    }

    public void removeProvider(String id) {
        providers.removeIf(p -> p.getId().equals(id));
        saveProviders();
    }

    public int getPort() {
        return Integer.parseInt(prefs.getString(KEY_PORT, KEY_DEFAULT_PORT));
    }

    public void setPort(int port) {
        prefs.edit().putString(KEY_PORT, String.valueOf(port)).apply();
    }
}
