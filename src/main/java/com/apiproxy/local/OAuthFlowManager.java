package com.apiproxy.local;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth PKCE flow: generate challenge, open browser, capture callback, exchange token.
 */
public class OAuthFlowManager {

    private static final OAuthProviderConfig[] ALL_CONFIGS = new OAuthProviderConfig[]{OAuthProviderConfig.GOOGLE};

    public static OAuthProviderConfig getConfigByName(String name) {
        for (OAuthProviderConfig c : ALL_CONFIGS) {
            if (c.getName().equals(name)) return c;
        }
        return OAuthProviderConfig.GOOGLE;
    }

    public static void startAuthorization(Activity act, OAuthProviderConfig c, int requestCode) {
        try {
            String[] pkce = generatePkce();
            String verifier = pkce[0], challenge = pkce[1];
            String state = generateState();
            Uri authUrl = buildAuthUrl(c, challenge, state);

            Bundle extra = new Bundle();
            extra.putString("providerId", c.getProviderId());
            extra.putString("codeVerifier", verifier);
            RedirectReceiverActivity.setPendingAuthInfo(extra);

            act.startActivityForResult(new Intent(Intent.ACTION_VIEW, authUrl), requestCode);
            Logger.d("OAuth: Starting for " + c.getName());
        } catch (Exception e) {
            Logger.e(e, "OAuth: Fail to start");
        }
    }

    public static String handleAuthResult(Activity act, int req, int res, Intent data) {
        if (req != RedirectReceiverActivity.REQUEST_OAUTH || res != Activity.RESULT_OK || data == null)
            return null;
        String code = data.getStringExtra("authCode");
        String id = data.getStringExtra("providerId");
        String verifier = data.getStringExtra("codeVerifier");
        if (code == null || verifier == null) return null;

        OAuthProviderConfig c = getConfigById(id);
        if (c == null) return null;
        exchangeCode(act, c, code, verifier);
        return id;
    }

    private static String[] generatePkce() throws Exception {
        SecureRandom r = new SecureRandom();
        byte[] vb = new byte[96];
        r.nextBytes(vb);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(vb);
        byte[] digest = MessageDigest.getInstance("SHA256").digest(vb);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return new String[]{verifier, challenge};
    }

    private static String generateState() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static Uri buildAuthUrl(OAuthProviderConfig c, String challenge, String state) {
        return Uri.parse(c.getAuthorizationUrl()).buildUpon()
                .appendQueryParameter("client_id", c.getClientId())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", c.getRedirectUri())
                .appendQueryParameter("scope", c.getScopes())
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .build();
    }

    private static void exchangeCode(Activity act, OAuthProviderConfig c, String code, String verifier) {
        new Thread(() -> {
            try {
                String body = "code=" + Uri.encode(code)
                        + "&client_id=" + Uri.encode(c.getClientId())
                        + "&redirect_uri=" + Uri.encode(c.getRedirectUri())
                        + "&grant_type=authorization_code"
                        + "&code_verifier=" + Uri.encode(verifier);

                Response resp = new OkHttpClient.Builder().build()
                        .newCall(new Request.Builder()
                                .url(c.getTokenUrl())
                                .post(RequestBody.create(body, MediaType.get("application/x-www-form-urlencoded")))
                                .build()).execute();

                if (!resp.isSuccessful()) {
                    Logger.e("Token fail: " + resp.body().string());
                    return;
                }

                JSONObject js = new JSONObject(resp.body().string());
                String at = js.optString("access_token", "");
                String rt = js.optString("refresh_token", "");
                long exp = js.optLong("expires_in", 0L);
                if (at.isEmpty()) {
                    Logger.e("No access_token");
                    return;
                }
                OAuthTokenStore.saveToken(act, c.getProviderId(), at, rt,
                        System.currentTimeMillis() + exp * 1000);
                Logger.d("Token OK for " + c.getName());
            } catch (Exception e) {
                Logger.e(e, "OAuth: exchange error");
            }
        }).start();
    }

    private static OAuthProviderConfig getConfigById(String id) {
        for (OAuthProviderConfig c : ALL_CONFIGS) {
            if (c.getProviderId().equals(id)) return c;
        }
        return OAuthProviderConfig.GOOGLE;
    }
}