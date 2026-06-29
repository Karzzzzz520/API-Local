package com.apiproxy.local;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final List<ApiProvider> providers = new ArrayList<>();
    private ProviderAdapter adapter;
    private ProxyServer proxyServer;
    private Button btnToggle;
    private TextView tvStatus;
    private int proxyPort = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerProviders);
        btnToggle = findViewById(R.id.btnToggleProxy);
        tvStatus = findViewById(R.id.tvStatus);
        FloatingActionButton fabSettings = findViewById(R.id.fabSettings);

        fabSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter(this, providers, proxyPort, new ProviderAdapter.OnProviderListener() {
            @Override
            public void onToggleEnabled(ApiProvider provider, boolean enabled) {
                provider.setEnabled(enabled);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onProviderClick(ApiProvider provider) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("endpoint",
                        provider.getLocalEndpoint(proxyPort));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, R.string.endpoint_copied, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProviderLongClick(ApiProvider provider) {
                android.widget.EditText et = new android.widget.EditText(MainActivity.this);
                et.setText(provider.getApiKey());
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(provider.getName() + " API Key")
                        .setView(et)
                        .setPositiveButton("保存", (d, w) -> {
                            provider.setApiKey(et.getText().toString().trim());
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setAdapter(adapter);

        btnToggle.setOnClickListener(v -> toggleProxy());

        loadProviders();
        updateStatus(false);
    }

    private void loadProviders() {
        providers.clear();
        providers.add(new ApiProvider("1", "GPT", "https://api.openai.com/v1", "", true));
        providers.add(new ApiProvider("2", "Gemini", "https://generativelanguage.googleapis.com/v1beta", "", true));
        providers.add(new ApiProvider("3", "Claude", "https://api.anthropic.com/v1", "", true));
        providers.add(new ApiProvider("4", "DeepSeek", "https://api.deepseek.com/v1", "", true));

        String cliJson = getSharedPreferences("apiproxy", MODE_PRIVATE)
                .getString("cli_accounts", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(cliJson);
            for (int i = 0; i < arr.length(); i++) {
                CliAccount acc = CliAccount.fromJson(arr.getJSONObject(i));
                if (acc != null && acc.isEnabled()) {
                    providers.add(new ApiProvider(
                            acc.getId(),
                            acc.getService(),
                            "https://api." + acc.getService().toLowerCase() + ".com/v1",
                            "", "email", acc.getEmail(), true
                    ));
                }
            }
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
    }

    private void toggleProxy() {
        if (proxyServer != null && proxyServer.isRunning()) {
            proxyServer.stop();
            updateStatus(false);
        } else {
            proxyServer = new ProxyServer(proxyPort, providers);
            proxyServer.setStatusCallback(running -> runOnUiThread(() -> updateStatus(running)));
            try {
                proxyServer.start();
                updateStatus(true);
            } catch (Exception e) {
                Toast.makeText(this, "启动代理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateStatus(boolean running) {
        if (running) {
            tvStatus.setText("代理运行中 - localhost:" + proxyPort);
            btnToggle.setText("停止代理");
        } else {
            tvStatus.setText("代理未启动");
            btnToggle.setText("启动代理");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProviders();
    }
}