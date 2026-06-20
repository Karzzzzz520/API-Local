package com.apiproxy.local;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CliAccountsActivity extends AppCompatActivity {

    private static final String PREF_KEY = "cli_accounts";
    private final List<CliAccount> accounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.cli_accounts_title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new CliAccountsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class CliAccountsFragment extends androidx.preference.PreferenceFragmentCompat {

        private final List<CliAccount> accounts = new ArrayList<>();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.cli_accounts_preferences, rootKey);

            loadAccounts();

            androidx.preference.Preference savePref = findPreference("cli_save");
            if (savePref != null) {
                savePref.setOnPreferenceClickListener(preference -> {
                    showEditor(null);
                    return true;
                });
            }
        }

        private void loadAccounts() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String json = prefs.getString(PREF_KEY, "[]");
            try {
                JSONArray array = new JSONArray(json);
                accounts.clear();
                for (int index = 0; index < array.length(); index++) {
                    CliAccount account = CliAccount.fromJson(array.getJSONObject(index));
                    if (account != null) {
                        accounts.add(account);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void saveAccounts() {
            try {
                JSONArray array = new JSONArray();
                for (CliAccount account : accounts) {
                    array.put(account.toJson());
                }
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(PREF_KEY, array.toString())
                        .apply();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        }

        private void showEditor(CliAccount existing) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            android.view.View view = android.view.LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_add_provider, null);

            TextInputEditText nameField = view.findViewById(R.id.etName);
            TextInputEditText baseUrlField = view.findViewById(R.id.etBaseUrl);
            TextInputEditText apiKeyField = view.findViewById(R.id.etApiKey);
            MaterialButton cancelButton = view.findViewById(R.id.btnCancel);
            MaterialButton saveButton = view.findViewById(R.id.btnSave);

            MaterialAutoCompleteTextView providerField = new MaterialAutoCompleteTextView(requireContext());
            providerField.setHint(R.string.cli_account_provider);
            providerField.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"GPT", "Gemini", "Claude", "DeepSeek", "Custom"}));

            MaterialAutoCompleteTextView loginTypeField = new MaterialAutoCompleteTextView(requireContext());
            loginTypeField.setHint(R.string.cli_account_login_type);
            loginTypeField.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"api_key", "account", "token", "none"}));

            android.widget.LinearLayout extra = new android.widget.LinearLayout(requireContext());
            extra.setOrientation(android.widget.LinearLayout.VERTICAL);
            extra.addView(providerField);
            extra.addView(loginTypeField);

            if (view instanceof android.view.ViewGroup group) {
                group.addView(extra, 0);
            }

            if (existing != null) {
                nameField.setText(existing.getName());
                baseUrlField.setText(existing.getBaseUrl());
                apiKeyField.setText(existing.getApiKey());
                providerField.setText(existing.getProvider(), false);
                loginTypeField.setText(existing.getLoginType(), false);
            } else {
                providerField.setText("GPT", false);
                loginTypeField.setText("api_key", false);
            }

            builder.setTitle(R.string.cli_accounts_title)
                    .setView(view)
                    .setCancelable(true);

            android.app.AlertDialog dialog = builder.create();
            dialog.show();

            cancelButton.setOnClickListener(v -> dialog.dismiss());
            saveButton.setOnClickListener(v -> {
                String name = safeText(nameField);
                String baseUrl = safeText(baseUrlField);
                String apiKey = safeText(apiKeyField);
                String provider = safeText(providerField);
                String loginType = safeText(loginTypeField);

                if (name.isEmpty()) {
                    nameField.setError("请输入名称");
                    return;
                }

                if (existing == null) {
                    accounts.add(new CliAccount(UUID.randomUUID().toString(), name, provider, loginType, baseUrl, apiKey, "", "", true));
                } else {
                    existing.setName(name);
                    existing.setProvider(provider);
                    existing.setLoginType(loginType);
                    existing.setBaseUrl(baseUrl);
                    existing.setApiKey(apiKey);
                }

                saveAccounts();
                dialog.dismiss();
                Toast.makeText(requireContext(), R.string.cli_login_configured, Toast.LENGTH_SHORT).show();
            });
        }

        private String safeText(TextInputEditText editText) {
            return editText.getText() == null ? "" : editText.getText().toString().trim();
        }
    }
}
