package com.apiproxy.local;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CliAccountsActivity extends AppCompatActivity {
    private static final String PREF_KEY = "cli_accounts";
    private final List<CliAccount> accounts = new ArrayList<>();
    private RecyclerView recyclerView;
    private AccountAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cli_accounts);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.cli_accounts_title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerAccounts);
        emptyView = findViewById(R.id.emptyView);
        MaterialButton btnAdd = findViewById(R.id.btnAddAccount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(accounts, this::showEditor);
        recyclerView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> showEditor(null));

        loadAccounts();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadAccounts() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String json = prefs.getString(PREF_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            accounts.clear();
            for (int i = 0; i < array.length(); i++) {
                CliAccount account = CliAccount.fromJson(array.getJSONObject(i));
                if (account != null) accounts.add(account);
            }
        } catch (Exception ignored) {}
        updateUI();
    }

    private void saveAccounts() {
        try {
            JSONArray array = new JSONArray();
            for (CliAccount account : accounts) {
                array.put(account.toJson());
            }
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(PREF_KEY, array.toString())
                    .apply();
        } catch (Exception e) {
            Toast.makeText(this, R.string.cli_login_missing, Toast.LENGTH_SHORT).show();
        }
        updateUI();
    }

    private void updateUI() {
        boolean empty = accounts.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void showEditor(CliAccount existing) {
        boolean isEdit = existing != null;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextInputLayout nameLayout = createInputLayout(getString(R.string.cli_account_name));
        TextInputEditText etName = new TextInputEditText(this);
        nameLayout.addView(etName);

        TextInputLayout urlLayout = createInputLayout(getString(R.string.cli_account_base_url));
        TextInputEditText etUrl = new TextInputEditText(this);
        urlLayout.addView(etUrl);

        TextInputLayout keyLayout = createInputLayout(getString(R.string.cli_account_api_key));
        TextInputEditText etKey = new TextInputEditText(this);
        keyLayout.addView(etKey);

        TextInputLayout providerLayout = createInputLayout(getString(R.string.cli_account_provider));
        MaterialAutoCompleteTextView etProvider = new MaterialAutoCompleteTextView(this);
        etProvider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new String[]{"GPT", "Gemini", "Claude", "Custom"}));
        etProvider.setText("GPT", false);
        providerLayout.addView(etProvider);

        TextInputLayout loginLayout = createInputLayout(getString(R.string.cli_account_login_type));
        MaterialAutoCompleteTextView etLogin = new MaterialAutoCompleteTextView(this);
        etLogin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new String[]{"api_key", "account", "token", "none"}));
        etLogin.setText("api_key", false);
        loginLayout.addView(etLogin);

        if (isEdit) {
            etName.setText(existing.getName());
            etUrl.setText(existing.getBaseUrl());
            etKey.setText(existing.getApiKey());
            etProvider.setText(existing.getProvider(), false);
            etLogin.setText(existing.getLoginType(), false);
        }

        layout.addView(nameLayout);
        layout.addView(urlLayout);
        layout.addView(keyLayout);
        layout.addView(providerLayout);
        layout.addView(loginLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? getString(R.string.cli_account_name) : getString(R.string.add_provider))
                .setView(layout)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                    String key = etKey.getText() != null ? etKey.getText().toString().trim() : "";
                    String prov = etProvider.getText() != null ? etProvider.getText().toString().trim() : "GPT";
                    String login = etLogin.getText() != null ? etLogin.getText().toString().trim() : "api_key";

                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.cli_account_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isEdit) {
                        existing.setName(name);
                        existing.setProvider(prov);
                        existing.setLoginType(login);
                        existing.setBaseUrl(url);
                        existing.setApiKey(key);
                    } else {
                        accounts.add(new CliAccount(UUID.randomUUID().toString(), name, prov, login, url, key, "", "", true));
                    }
                    saveAccounts();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private TextInputLayout createInputLayout(String hint) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        til.setLayoutParams(lp);
        return til;
    }

    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        private final List<CliAccount> data;
        private final OnEditListener listener;

        interface OnEditListener { void onEdit(CliAccount acc); }

        AccountAdapter(List<CliAccount> data, OnEditListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(parent.getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(32, 24, 32, 24);
            item.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(android.graphics.Color.parseColor("#1A000000"));
            item.setBackground(bg);
            LinearLayout.LayoutParams bgParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bgParams.bottomMargin = 8;
            item.setLayoutParams(new RecyclerView.LayoutParams(bgParams));

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextSize(16);
            tvName.getPaint().setFakeBoldText(true);
            TextView tvDetail = new TextView(parent.getContext());
            tvDetail.setTextSize(13);
            tvDetail.setAlpha(0.6f);
            item.addView(tvName);
            item.addView(tvDetail);
            return new VH(item, tvName, tvDetail);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CliAccount acc = data.get(pos);
            h.tvName.setText(acc.getName());
            h.tvDetail.setText(acc.getProvider() + " | " + acc.getLoginType());
            h.itemView.setOnClickListener(v -> listener.onEdit(acc));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDetail;
            VH(View v, TextView tvName, TextView tvDetail) {
                super(v);
                this.tvName = tvName;
                this.tvDetail = tvDetail;
            }
        }
    }
}