package com.apiproxy.local;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private View emptyView;
    private SimpleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.i("CliAccountsActivity onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cli_accounts);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.cli_accounts_title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView = findViewById(R.id.recyclerAccounts);
        emptyView = findViewById(R.id.emptyView);
        FloatingActionButton fabAdd = findViewById(R.id.btnAddAccount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter(accounts, this::showEditor);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            Logger.d("FAB add account clicked");
            showEditor(null);
        });

        loadAccounts();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void loadAccounts() {
        String json = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            accounts.clear();
            for (int i = 0; i < array.length(); i++) {
                CliAccount acc = CliAccount.fromJson(array.getJSONObject(i));
                if (acc != null) accounts.add(acc);
            }
            Logger.d("Loaded " + accounts.size() + " CLI accounts");
        } catch (Exception e) {
            Logger.e("Failed to load CLI accounts", e);
        }
        updateUI();
    }

    private void saveAccounts() {
        try {
            JSONArray array = new JSONArray();
            for (CliAccount acc : accounts) array.put(acc.toJson());
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_KEY, array.toString()).apply();
            Logger.d("Saved " + accounts.size() + " CLI accounts");
        } catch (Exception e) {
            Logger.e("Failed to save CLI accounts", e);
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
        Logger.d("Opening editor, isEdit=" + isEdit);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        TextInputLayout emailLayout = new TextInputLayout(this);
        emailLayout.setHint("邮箱");
        emailLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText etEmail = new TextInputEditText(this);
        emailLayout.addView(etEmail);

        TextInputLayout pwdLayout = new TextInputLayout(this);
        pwdLayout.setHint("密码");
        pwdLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText etPwd = new TextInputEditText(this);
        pwdLayout.addView(etPwd);

        TextInputLayout svcLayout = new TextInputLayout(this);
        svcLayout.setHint("服务 (GPT/Gemini/Claude/DeepSeek)");
        svcLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        EditText etSvc = new EditText(this);
        svcLayout.addView(etSvc);

        if (isEdit) {
            etEmail.setText(existing.getEmail());
            etPwd.setText(existing.getPassword());
            etSvc.setText(existing.getService());
        } else {
            etSvc.setText("GPT");
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        emailLayout.setLayoutParams(lp);
        pwdLayout.setLayoutParams(lp);
        svcLayout.setLayoutParams(lp);

        layout.addView(emailLayout);
        layout.addView(pwdLayout);
        layout.addView(svcLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? "编辑账号" : "添加账号")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                    String pwd = etPwd.getText() != null ? etPwd.getText().toString().trim() : "";
                    String svc = etSvc.getText() != null ? etSvc.getText().toString().trim() : "GPT";

                    if (email.isEmpty() || pwd.isEmpty()) {
                        Toast.makeText(this, "邮箱和密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isEdit) {
                        existing.setEmail(email);
                        existing.setPassword(pwd);
                        existing.setService(svc);
                        Logger.i("Updated account: " + email);
                    } else {
                        accounts.add(new CliAccount(UUID.randomUUID().toString(), email, pwd, svc, true));
                        Logger.i("Added account: " + email);
                    }
                    saveAccounts();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VH> {
        private final List<CliAccount> data;
        private final OnEditListener listener;
        interface OnEditListener { void onEdit(CliAccount acc); }

        SimpleAdapter(List<CliAccount> data, OnEditListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(parent.getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(32, 24, 32, 24);
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = 8;
            item.setLayoutParams(rlp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(android.graphics.Color.parseColor("#1A000000"));
            item.setBackground(bg);

            TextView tvEmail = new TextView(parent.getContext());
            tvEmail.setTextSize(16);
            tvEmail.getPaint().setFakeBoldText(true);
            TextView tvDetail = new TextView(parent.getContext());
            tvDetail.setTextSize(13);
            tvDetail.setAlpha(0.6f);
            item.addView(tvEmail);
            item.addView(tvDetail);
            return new VH(item, tvEmail, tvDetail);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CliAccount acc = data.get(pos);
            h.tvEmail.setText(acc.getEmail());
            h.tvDetail.setText(acc.getService() + " 账号");
            h.itemView.setOnClickListener(v -> listener.onEdit(acc));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvEmail, tvDetail;
            VH(View v, TextView tvEmail, TextView tvDetail) {
                super(v);
                this.tvEmail = tvEmail;
                this.tvDetail = tvDetail;
            }
        }
    }
}