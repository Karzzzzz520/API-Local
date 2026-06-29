package com.apiproxy.local;

import org.json.JSONObject;

public class CliAccount {
    private String id;
    private String email;
    private String password;
    private String service;
    private boolean enabled;

    public CliAccount(String id, String email, String password, String service, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.service = service;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getService() { return service; }
    public boolean isEnabled() { return enabled; }

    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setService(String service) { this.service = service; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("email", email);
            obj.put("password", password);
            obj.put("service", service);
            obj.put("enabled", enabled);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static CliAccount fromJson(JSONObject obj) {
        try {
            return new CliAccount(
                obj.optString("id", ""),
                obj.optString("email", ""),
                obj.optString("password", ""),
                obj.optString("service", "GPT"),
                obj.optBoolean("enabled", true)
            );
        } catch (Exception e) {
            return null;
        }
    }
}