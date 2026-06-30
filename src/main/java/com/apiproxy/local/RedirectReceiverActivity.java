package com.apiproxy.local;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Captures OAuth callback via custom scheme (oauth-api-proxy://callback?code=...).
 */
public class RedirectReceiverActivity extends AppCompatActivity {

    public static final int REQUEST_OAUTH = 1001;

    private static Bundle pendingAuthInfo = null;

    public static synchronized void setPendingAuthInfo(Bundle bundle) {
        pendingAuthInfo = bundle;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();

        Logger.d("Redirect: data=" + (data != null ? data.toString() : "null"));

        String authCode = null;
        if (data != null) {
            authCode = data.getQueryParameter("code");
        }

        Intent resultIntent = new Intent();
        if (authCode != null) {
            resultIntent.putExtra("authCode", authCode);
            if (pendingAuthInfo != null) {
                resultIntent.putExtra("providerId", pendingAuthInfo.getString("providerId"));
                resultIntent.putExtra("codeVerifier", pendingAuthInfo.getString("codeVerifier"));
            }
            setResult(RESULT_OK, resultIntent);
        } else {
            Logger.e("Redirect: No auth code");
            setResult(RESULT_CANCELED);
        }

        finish();
    }
}