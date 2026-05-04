package com.apiproxy.local;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_PROXY_STATUS = "com.apiproxy.local.PROXY_STATUS";
    public static final String ACTION_LOG = "com.apiproxy.local.LOG";
    public static final String EXTRA_PROXY_RUNNING = "running";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_LOG_MESSAGE = "message";

    private MaterialSwitch switchProxy;
    private EditText etPort;
    private RecyclerView recyclerProviders;
    private ProviderAdapter adapter;
    private TextView tvLogs;
    private View tvLogsHeader;
    private ScrollView logsScroll;
    
    private ProviderManager providerManager;
    private boolean isProxyRunning = false;
    private boolean updatingUI = false;
    private StringBuilder logBuilder = new StringBuilder();
    private final int MAX_LOG_LINES = 100;

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PROXY_STATUS.equals(intent.getAction())) {
                boolean running = intent.getBooleanExtra(EXTRA_PROXY_RUNNING, false);
                updateProxyStatus(running);
            } else if (ACTION_LOG.equals(intent.getAction())) {
                String message = intent.getStringExtra(EXTRA_LOG_MESSAGE);
                appendLog(message);
            }
        }
    };

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startProxyService();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        providerManager = new ProviderManager(this);
        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
        loadSavedPort();
        checkServiceStatus();
    }

    private void initViews() {
        switchProxy = findViewById(R.id.switch_proxy);
        etPort = findViewById(R.id.et_port);
        recyclerProviders = findViewById(R.id.recycler_providers);
        tvLogs = findViewById(R.id.tv_logs);
        tvLogsHeader = findViewById(R.id.tv_logs_header);
        logsScroll = findViewById(R.id.logs_scroll);
        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> showAddProviderDialog(null));
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.app_name);
    }

    private void setupRecyclerView() {
        adapter = new ProviderAdapter();
        adapter.setOnProviderClickListener(new ProviderAdapter.OnProviderClickListener() {
            @Override
            public void onProviderClick(ApiProvider provider) { showAddProviderDialog(provider); }
            @Override
            public void onProviderLongClick(ApiProvider provider) { showDeleteProviderDialog(provider); }
        });
        recyclerProviders.setLayoutManager(new LinearLayoutManager(this));
        recyclerProviders.setAdapter(adapter);
        refreshProvidersList();
    }

    private void setupListeners() {
        switchProxy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingUI) return; // Don't react to programmatic changes
            if (isChecked) {
                requestStartProxy();
            } else {
                stopProxyService();
            }
        });

        etPort.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String port = s.toString().trim();
                if (!port.isEmpty()) {
                    try {
                        int portInt = Integer.parseInt(port);
                        if (portInt > 0 && portInt < 65536) providerManager.setPort(portInt);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        tvLogsHeader.setOnClickListener(v -> {
            if (logsScroll.getVisibility() == View.VISIBLE) {
                logsScroll.setVisibility(View.GONE);
            } else {
                logsScroll.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadSavedPort() {
        etPort.setText(String.valueOf(providerManager.getPort()));
    }

    private void refreshProvidersList() {
        adapter.setProviders(providerManager.getAllProviders());
    }

    private void requestStartProxy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                updatingUI = true;
                switchProxy.setChecked(false);
                updatingUI = false;
                return;
            }
        }
        startProxyService();
    }

    private void startProxyService() {
        int port = 8080;
        try { port = Integer.parseInt(etPort.getText().toString().trim()); }
        catch (NumberFormatException ignored) {}
        
        Intent intent = new Intent(this, ProxyService.class);
        intent.setAction(ProxyService.ACTION_START);
        intent.putExtra(ProxyService.EXTRA_PORT, port);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopProxyService() {
        Intent intent = new Intent(this, ProxyService.class);
        intent.setAction(ProxyService.ACTION_STOP);
        startService(intent);
    }

    private void checkServiceStatus() {
        updateProxyStatus(false);
    }

    private void updateProxyStatus(boolean running) {
        this.isProxyRunning = running;
        updatingUI = true;
        switchProxy.setChecked(running);
        updatingUI = false;
        
        View statusView = findViewById(R.id.status_indicator);
        if (statusView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(running ? Color.parseColor("#4CAF50") : Color.parseColor("#9E9E9E"));
            statusView.setBackground(drawable);
        }
        
        TextView tvStatus = findViewById(R.id.tv_status);
        if (tvStatus != null) {
            tvStatus.setText(running ? R.string.status_running : R.string.status_stopped);
        }
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            if (logBuilder.length() > 0) logBuilder.append("\n");
            logBuilder.append(message);
            
            String[] lines = logBuilder.toString().split("\n");
            if (lines.length > MAX_LOG_LINES) {
                StringBuilder nb = new StringBuilder();
                for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                    if (nb.length() > 0) nb.append("\n");
                    nb.append(lines[i]);
                }
                logBuilder = nb;
            }
            
            tvLogs.setText(logBuilder.toString());
            if (logsScroll != null) logsScroll.post(() -> logsScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void showAddProviderDialog(ApiProvider existingProvider) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_provider, null);
        AutoCompleteTextView actvName = dialogView.findViewById(R.id.actv_name);
        TextInputEditText etBaseUrl = dialogView.findViewById(R.id.et_base_url);
        TextInputEditText etApiKey = dialogView.findViewById(R.id.et_api_key);
        AutoCompleteTextView actvKeyHeader = dialogView.findViewById(R.id.actv_key_header);
        TextInputEditText etKeyPrefix = dialogView.findViewById(R.id.et_key_prefix);
        Spinner spinnerKeyType = dialogView.findViewById(R.id.spinner_key_type);

        String[] presetNames = {"OpenAI", "Gemini", "Claude", "DeepSeek", "自定义"};
        actvName.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, presetNames));

        String[] headerOptions = {"Authorization", "x-api-key", "x-goog-api-key", "api-key", "自定义"};
        actvKeyHeader.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, headerOptions));

        String[] keyTypes = {"Bearer (如 OpenAI, DeepSeek)", "无前缀 (如 Gemini, Claude)"};
        spinnerKeyType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, keyTypes));

        if (existingProvider != null) {
            actvName.setText(existingProvider.getName());
            etBaseUrl.setText(existingProvider.getBaseUrl());
            etApiKey.setText(existingProvider.getApiKey());
            actvKeyHeader.setText(existingProvider.getKeyHeader());
            etKeyPrefix.setText(existingProvider.getKeyPrefix());
            spinnerKeyType.setSelection(existingProvider.getKeyPrefix().isEmpty() ? 1 : 0);
        }

        actvName.setOnItemClickListener((parent, view, position, id) -> {
            switch ((String) parent.getItemAtPosition(position)) {
                case "OpenAI":
                    etBaseUrl.setText("https://api.openai.com");
                    actvKeyHeader.setText("Authorization");
                    etKeyPrefix.setText("Bearer ");
                    spinnerKeyType.setSelection(0); break;
                case "Gemini":
                    etBaseUrl.setText("https://generativelanguage.googleapis.com");
                    actvKeyHeader.setText("x-goog-api-key");
                    etKeyPrefix.setText("");
                    spinnerKeyType.setSelection(1); break;
                case "Claude":
                    etBaseUrl.setText("https://api.anthropic.com");
                    actvKeyHeader.setText("x-api-key");
                    etKeyPrefix.setText("");
                    spinnerKeyType.setSelection(1); break;
                case "DeepSeek":
                    etBaseUrl.setText("https://api.deepseek.com");
                    actvKeyHeader.setText("Authorization");
                    etKeyPrefix.setText("Bearer ");
                    spinnerKeyType.setSelection(0); break;
            }
        });

        spinnerKeyType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                etKeyPrefix.setText(pos == 0 ? "Bearer " : "");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        new MaterialAlertDialogBuilder(this)
            .setTitle(existingProvider != null ? R.string.edit_provider : R.string.add_provider)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (dialog, which) -> {
                String name = actvName.getText().toString().trim();
                String baseUrl = etBaseUrl.getText().toString().trim();
                String apiKey = etApiKey.getText().toString().trim();
                String keyHeader = actvKeyHeader.getText().toString().trim();
                String keyPrefix = etKeyPrefix.getText().toString();
                if (name.isEmpty() || baseUrl.isEmpty()) {
                    Toast.makeText(this, R.string.fill_required_fields, Toast.LENGTH_SHORT).show();
                    return;
                }
                String id = existingProvider != null ? existingProvider.getId() :
                    name.toLowerCase().replaceAll("[^a-z0-9]", "") + "_" + System.currentTimeMillis();
                providerManager.addProvider(new ApiProvider(id, name, baseUrl, apiKey, keyHeader, keyPrefix, null));
                refreshProvidersList();
                Toast.makeText(this, R.string.provider_saved, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showDeleteProviderDialog(ApiProvider provider) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_provider)
            .setMessage(getString(R.string.delete_provider_confirm, provider.getName()))
            .setPositiveButton(R.string.delete, (d, w) -> {
                providerManager.removeProvider(provider.getId());
                refreshProvidersList();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.menu_main, menu); return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_logs) { logBuilder.setLength(0); tvLogs.setText(""); return true; }
        if (id == R.id.action_copy_url) {
            String url = "http://localhost:" + providerManager.getPort();
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("Proxy URL", url));
            Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_about) { showAboutDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.app_name)
            .setMessage(R.string.about_message).setPositiveButton(R.string.ok, null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PROXY_STATUS);
        filter.addAction(ACTION_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }
}
