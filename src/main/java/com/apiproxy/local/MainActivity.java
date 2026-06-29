package com.apiproxy.local;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final String[][] DEFAULT_PROVIDERS = {
            {"OpenAI", "https://api.openai.com"},
            {"Gemini", "https://generativelanguage.googleapis.com"},
            {"Claude", "https://api.anthropic.com"},
            {"DeepSeek", "https://api.deepseek.com"}
    };

    private TextInputEditText etPort;
    private MaterialButton btnToggleServer;
    private TextView tvStatus, tvEndpoint, tvProviderCount;
    private View statusDot;
    private RecyclerView recyclerProviders;
    private FloatingActionButton fabAddProvider;
    private MaterialCardView cardServer;
    private ProviderAdapter adapter;
    private final List<ApiProvider> providers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProxyService proxyService;
    private boolean serviceBound = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> startProxyServer());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            proxyService = ((ProxyService.LocalBinder) service).getService();
            serviceBound = true;
            updateServerUI(proxyService.isProxyRunning());
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        initViews();
        loadProviders();
        bindProxyService();
    }

    private void initViews() {
        cardServer = findViewById(R.id.cardServer);
        etPort = findViewById(R.id.etPort);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        tvStatus = findViewById(R.id.tvStatus);
        tvEndpoint = findViewById(R.id.tvEndpoint);
        tvProviderCount = findViewById(R.id.tvProviderCount);
        statusDot = findViewById(R.id.statusDot);
        recyclerProviders = findViewById(R.id.recyclerProviders);
        fabAddProvider = findViewById(R.id.fabAddProvider);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(true);

        btnToggleServer.setOnClickListener(v -> toggleServer());
        fabAddProvider.setOnClickListener(v -> showAddProviderDialog(null));

        recyclerProviders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter(this, providers, getPort(), new ProviderAdapter.OnProviderListener() {
            @Override
            public void onToggleEnabled(ApiProvider p, boolean enabled) {
                p.setEnabled(enabled); saveProviders(); updateProviderCount();
                if (serviceBound && proxyService != null && proxyService.isProxyRunning()) restartServer();
            }
            @Override
            public void onProviderClick(ApiProvider p) { showAddProviderDialog(p); }
            @Override
            public void onProviderLongClick(ApiProvider p) { showDeleteConfirmDialog(p); }
        });
        recyclerProviders.setAdapter(adapter);

        etPort.setText(String.valueOf(getSharedPreferences("apiproxy", MODE_PRIVATE).getInt("port", 8080)));
        if (providers.isEmpty()) loadDefaultProviders();
    }

    private void bindProxyService() {
        bindService(new Intent(this, ProxyService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!serviceBound) bindService(new Intent(this, ProxyService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int getPort() {
        try { return Integer.parseInt(etPort.getText().toString().trim()); }
        catch (NumberFormatException e) { return 8080; }
    }

    private void toggleServer() {
        if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
            proxyService.stopProxy();
            updateServerUI(false);
            Toast.makeText(this, "代理服务器已停止", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= 33)
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            else startProxyServer();
        }
    }

    private void startProxyServer() {
        int port = getPort();
        if (port < 1024 || port > 65535) { etPort.setError("端口: 1024-65535"); return; }
        getSharedPreferences("apiproxy", MODE_PRIVATE).edit().putInt("port", port).apply();

        boolean hasEnabled = false;
        for (ApiProvider p : providers) {
            if (p.isEnabled() && p.getApiKey() != null && !p.getApiKey().isEmpty()) { hasEnabled = true; break; }
        }
        if (!hasEnabled) {
            Snackbar.make(findViewById(android.R.id.content), "请至少添加一个启用的 API Key", Snackbar.LENGTH_LONG).show();
            return;
        }

        ContextCompat.startForegroundService(this, new Intent(this, ProxyService.class));
        List<ApiProvider> allProviders = new ArrayList<>(providers);
        for (ApiProvider cp : loadCliProviders()) {
            boolean exists = false;
            for (ApiProvider p : allProviders) { if (p.getName().equals(cp.getName())) { exists = true; break; } }
            if (!exists) allProviders.add(cp);
        }
        final List<ApiProvider> finalProviders = allProviders;
        mainHandler.postDelayed(() -> {
            if (serviceBound && proxyService != null)
                proxyService.startProxy(port, finalProviders, msg -> runOnUiThread(() -> logToUI(msg)), running -> runOnUiThread(() -> updateServerUI(running)));
        }, 300);
        Toast.makeText(this, "正在启动代理服务器...", Toast.LENGTH_SHORT).show();
    }

    private void updateServerUI(boolean running) {
        if (running) {
            btnToggleServer.setText(R.string.stop_server);
            btnToggleServer.setIconResource(R.drawable.ic_stop);
            btnToggleServer.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            tvStatus.setText(R.string.server_running);
            tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            ((GradientDrawable) statusDot.getBackground()).setColor(android.graphics.Color.parseColor("#4CAF50"));
            tvEndpoint.setVisibility(View.VISIBLE);
            tvEndpoint.setText("http://localhost:" + getPort());
            etPort.setEnabled(false);
        } else {
            btnToggleServer.setText(R.string.start_server);
            btnToggleServer.setIconResource(R.drawable.ic_play);
            btnToggleServer.setBackgroundTintList(ColorStateList.valueOf(getColor(android.R.color.holo_green_dark)));
            tvStatus.setText(R.string.server_stopped);
            tvStatus.setTextColor(android.graphics.Color.parseColor("#757575"));
            ((GradientDrawable) statusDot.getBackground()).setColor(android.graphics.Color.parseColor("#F44336"));
            tvEndpoint.setVisibility(View.GONE);
            etPort.setEnabled(true);
        }
        adapter.notifyDataSetChanged();
    }

    private void restartServer() {
        if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
            List<ApiProvider> allProviders = new ArrayList<>(providers);
            for (ApiProvider cp : loadCliProviders()) {
                boolean exists = false;
                for (ApiProvider p : allProviders) { if (p.getName().equals(cp.getName())) { exists = true; break; } }
                if (!exists) allProviders.add(cp);
            }
            proxyService.startProxy(getPort(), allProviders, msg -> runOnUiThread(() -> logToUI(msg)), running -> runOnUiThread(() -> updateServerUI(running)));
        }
    }

    private void logToUI(String msg) {
        if (msg.startsWith("❌") || msg.startsWith("⚠️"))
            Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
    }

    // =================== Provider Management ===================

    private void showAddProviderDialog(ApiProvider existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_provider, null);
        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputEditText etBaseUrl = view.findViewById(R.id.etBaseUrl);
        TextInputEditText etApiKey = view.findViewById(R.id.etApiKey);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnSave = view.findViewById(R.id.btnSave);

        boolean isEdit = existing != null;
        if (isEdit) {
            etName.setText(existing.getName());
            etBaseUrl.setText(existing.getBaseUrl());
            etApiKey.setText(existing.getApiKey());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(view).create();
        dialog.show();
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();
            if (name.isEmpty()) { etName.setError("必填"); return; }
            if (baseUrl.isEmpty()) { etBaseUrl.setError("必填"); return; }
            if (apiKey.isEmpty()) { etApiKey.setError("必填"); return; }
            if (isEdit) { existing.setName(name); existing.setBaseUrl(baseUrl); existing.setApiKey(apiKey); }
            else providers.add(new ApiProvider(UUID.randomUUID().toString(), name, baseUrl, apiKey, true));
            saveProviders();
            adapter.notifyDataSetChanged();
            updateProviderCount();
            dialog.dismiss();
            if (serviceBound && proxyService != null && proxyService.isProxyRunning()) restartServer();
        });
    }

    private void showDeleteConfirmDialog(ApiProvider provider) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除服务商")
                .setMessage("确定要删除 " + provider.getName() + " 吗？")
                .setPositiveButton("删除", (d, w) -> {
                    providers.remove(provider);
                    saveProviders();
                    adapter.notifyDataSetChanged();
                    updateProviderCount();
                    if (serviceBound && proxyService != null && proxyService.isProxyRunning()) restartServer();
                })
                .setNegativeButton("取消", null).show();
    }

    private void loadDefaultProviders() {
        for (String[] def : DEFAULT_PROVIDERS) {
            providers.add(new ApiProvider(UUID.randomUUID().toString(), def[0], def[1], "", false));
        }
        saveProviders();
        adapter.notifyDataSetChanged();
        updateProviderCount();
    }

    // =================== Persistence ===================

    private void loadProviders() {
        providers.clear();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("apiproxy", MODE_PRIVATE).getString("providers", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                ApiProvider p = ApiProvider.fromJson(arr.getJSONObject(i));
                if (p != null) providers.add(p);
            }
        } catch (Exception ignored) {}
        updateProviderCount();
    }

    private void saveProviders() {
        try {
            JSONArray arr = new JSONArray();
            for (ApiProvider p : providers) arr.put(p.toJson());
            getSharedPreferences("apiproxy", MODE_PRIVATE).edit().putString("providers", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<ApiProvider> loadCliProviders() {
        List<ApiProvider> cli = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(PreferenceManager.getDefaultSharedPreferences(this).getString("cli_accounts", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                CliAccount acc = CliAccount.fromJson(arr.getJSONObject(i));
                if (acc != null && acc.isEnabled()) cli.add(acc.toApiProvider());
            }
        } catch (Exception ignored) {}
        return cli;
    }

    private void updateProviderCount() {
        int c = 0;
        for (ApiProvider p : providers) { if (!p.getApiKey().isEmpty()) c++; }
        String t = providers.size() + " 个服务商";
        if (c > 0) t += " (" + c + " 个已配置)";
        tvProviderCount.setText(t);
    }
}