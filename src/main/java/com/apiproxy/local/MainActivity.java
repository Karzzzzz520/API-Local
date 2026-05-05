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
import android.view.Gravity;
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
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    // 默认预设服务商
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

    // 通知权限请求 (安卓13+ 前台服务需要)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    // 权限已授予，重新触发启动
                    startProxyServer();
                } else {
                    // 权限被拒绝，仍然尝试启动
                    startProxyServer();
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ProxyService.LocalBinder binder = (ProxyService.LocalBinder) service;
            proxyService = binder.getService();
            serviceBound = true;
            updateServerUI(proxyService.isProxyRunning());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 启用 Material You 动态取色 (Android 12+)
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置沉浸式状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
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

        // Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // 服务器开关按钮
        btnToggleServer.setOnClickListener(v -> toggleServer());

        // 添加服务商 FAB
        fabAddProvider.setOnClickListener(v -> showAddProviderDialog(null));

        // RecyclerView 设置
        recyclerProviders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter(this, providers, getPort(), new ProviderAdapter.OnProviderListener() {
            @Override
            public void onToggleEnabled(ApiProvider provider, boolean enabled) {
                provider.setEnabled(enabled);
                saveProviders();
                updateProviderCount();
                if (serviceBound && proxyService.isProxyRunning()) {
                    // 如果服务器在运行，需要重启以应用更改
                    restartServer();
                }
            }

            @Override
            public void onProviderClick(ApiProvider provider) {
                showAddProviderDialog(provider);
            }

            @Override
            public void onProviderLongClick(ApiProvider provider) {
                showDeleteConfirmDialog(provider);
            }
        });
        recyclerProviders.setAdapter(adapter);

        // 默认端口
        SharedPreferences prefs = getSharedPreferences("apiproxy", MODE_PRIVATE);
        etPort.setText(String.valueOf(prefs.getInt("port", 8080)));

        if (providers.isEmpty()) {
            loadDefaultProviders();
        }
    }

    private void bindProxyService() {
        Intent intent = new Intent(this, ProxyService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!serviceBound) {
            Intent intent = new Intent(this, ProxyService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 不在这里解绑，因为服务可能仍在运行
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_log) {
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int getPort() {
        try {
            return Integer.parseInt(etPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private void toggleServer() {
        if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
            // 停止服务器
            proxyService.stopProxy();
            updateServerUI(false);
            Toast.makeText(this, "代理服务器已停止", Toast.LENGTH_SHORT).show();
        } else {
            // 安卓13+ 需要运行时请求通知权限
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                startProxyServer();
            }
        }
    }

    // 提取启动逻辑，给权限回调复用
    private void startProxyServer() {
        int port = getPort();
        if (port < 1024 || port > 65535) {
            etPort.setError("端口范围: 1024-65535");
            return;
        }

        // 保存端口
        getSharedPreferences("apiproxy", MODE_PRIVATE)
                .edit()
                .putInt("port", port)
                .apply();

        // 检查是否有启用的服务商
        boolean hasEnabled = false;
        for (ApiProvider p : providers) {
            if (p.isEnabled() && p.getApiKey() != null && !p.getApiKey().isEmpty()) {
                hasEnabled = true;
                break;
            }
        }

        if (!hasEnabled) {
            Snackbar.make(findViewById(android.R.id.content),
                    "请至少添加一个启用的 API Key", Snackbar.LENGTH_LONG).show();
            return;
        }

        // 启动代理服务
        Intent serviceIntent = new Intent(this, ProxyService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        // 绑定并启动
        mainHandler.postDelayed(() -> {
            if (serviceBound && proxyService != null) {
                proxyService.startProxy(port, providers,
                        msg -> runOnUiThread(() -> logToUI(msg)),
                        running -> runOnUiThread(() -> updateServerUI(running)));
            }
        }, 300);

        Toast.makeText(this, "正在启动代理服务器...", Toast.LENGTH_SHORT).show();
    }
    }

    private void updateServerUI(boolean running) {
        if (running) {
            btnToggleServer.setText(R.string.stop_server);
            btnToggleServer.setIconResource(R.drawable.ic_stop);
            btnToggleServer.setBackgroundTintList(ColorStateList.valueOf(
                    getColor(com.google.android.material.R.color.material_dynamic_tertiary20)));
            tvStatus.setText(R.string.server_running);
            tvStatus.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_primary40));

            GradientDrawable drawable = (GradientDrawable) statusDot.getBackground();
            drawable.setColor(getColor(com.google.android.material.R.color.material_dynamic_primary40));
            statusDot.setBackground(drawable);

            tvEndpoint.setVisibility(View.VISIBLE);
            tvEndpoint.setText("http://localhost:" + getPort());

            etPort.setEnabled(false);
        } else {
            btnToggleServer.setText(R.string.start_server);
            btnToggleServer.setIconResource(R.drawable.ic_play);
            btnToggleServer.setBackgroundTintList(ColorStateList.valueOf(
                    getColor(com.google.android.material.R.color.material_dynamic_primary60)));
            tvStatus.setText(R.string.server_stopped);
            tvStatus.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral40));

            GradientDrawable drawable = (GradientDrawable) statusDot.getBackground();
            drawable.setColor(getColor(com.google.android.material.R.color.material_dynamic_error30));
            statusDot.setBackground(drawable);

            tvEndpoint.setVisibility(View.GONE);
            etPort.setEnabled(true);
        }

        // 更新适配器中的端口
        adapter.notifyDataSetChanged();
    }

    private void restartServer() {
        if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
            int port = getPort();
            proxyService.startProxy(port, providers,
                    msg -> runOnUiThread(() -> logToUI(msg)),
                    running -> runOnUiThread(() -> updateServerUI(running)));
        }
    }

    private void logToUI(String msg) {
        // 日志显示在 Snackbar（轻量）
        if (msg.startsWith("❌") || msg.startsWith("⚠️")) {
            Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
        }
    }

    // =================== Provider Management ===================

    private void showAddProviderDialog(ApiProvider existing) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_add_provider, null);

        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etBaseUrl = dialogView.findViewById(R.id.etBaseUrl);
        TextInputEditText etApiKey = dialogView.findViewById(R.id.etApiKey);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSave);

        boolean isEdit = existing != null;
        if (isEdit) {
            etName.setText(existing.getName());
            etBaseUrl.setText(existing.getBaseUrl());
            etApiKey.setText(existing.getApiKey());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // 如果要编辑，设置标题
        if (isEdit) {
            dialog.setTitle("编辑服务商");
        }

        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("请输入名称");
                return;
            }
            if (baseUrl.isEmpty()) {
                etBaseUrl.setError("请输入 API 地址");
                return;
            }
            if (apiKey.isEmpty()) {
                etApiKey.setError("请输入 API Key");
                return;
            }

            if (isEdit) {
                existing.setName(name);
                existing.setBaseUrl(baseUrl);
                existing.setApiKey(apiKey);
            } else {
                String id = UUID.randomUUID().toString();
                providers.add(new ApiProvider(id, name, baseUrl, apiKey, true));
            }

            saveProviders();
            adapter.notifyDataSetChanged();
            updateProviderCount();
            dialog.dismiss();

            // 如果服务器运行中，重启
            if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
                restartServer();
            }
        });
    }

    private void showDeleteConfirmDialog(ApiProvider provider) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除服务商")
                .setMessage("确定要删除 " + provider.getName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    providers.remove(provider);
                    saveProviders();
                    adapter.notifyDataSetChanged();
                    updateProviderCount();

                    if (serviceBound && proxyService != null && proxyService.isProxyRunning()) {
                        restartServer();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showInfoDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("API Proxy")
                .setMessage("将各种 AI 服务商的 API Key 转为本地代理地址。\n\n" +
                        "使用方法：\n" +
                        "1. 添加服务商并填入 API Key\n" +
                        "2. 设置端口号\n" +
                        "3. 启动代理服务\n" +
                        "4. 将 AI 客户端的 API 地址改为对应的本地端点\n\n" +
                        "支持: OpenAI / Gemini / Claude / DeepSeek / 自定义")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void loadDefaultProviders() {
        for (String[] def : DEFAULT_PROVIDERS) {
            String id = UUID.randomUUID().toString();
            providers.add(new ApiProvider(id, def[0], def[1], "", false));
        }
        saveProviders();
        adapter.notifyDataSetChanged();
        updateProviderCount();
    }

    // =================== Persistence ===================

    private void loadProviders() {
        providers.clear();
        try {
            SharedPreferences prefs = getSharedPreferences("apiproxy", MODE_PRIVATE);
            String jsonStr = prefs.getString("providers", "[]");
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                ApiProvider provider = ApiProvider.fromJson(jsonArray.getJSONObject(i));
                if (provider != null) {
                    providers.add(provider);
                }
            }
        } catch (Exception e) {
            // 加载失败，使用默认
        }
        updateProviderCount();
    }

    private void saveProviders() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (ApiProvider provider : providers) {
                jsonArray.put(provider.toJson());
            }
            SharedPreferences prefs = getSharedPreferences("apiproxy", MODE_PRIVATE);
            prefs.edit().putString("providers", jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateProviderCount() {
        int count = 0;
        for (ApiProvider p : providers) {
            if (!p.getApiKey().isEmpty()) count++;
        }
        String text = providers.size() + " 个服务商";
        if (count > 0) text += " (" + count + " 个已配置)";
        tvProviderCount.setText(text);
    }
}