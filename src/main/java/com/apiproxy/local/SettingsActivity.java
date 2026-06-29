package com.apiproxy.local;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private void writeDebugLog(String msg) {
        Log.e(TAG, msg);
        try {
            File logFile = new File(Environment.getExternalStorageDirectory(), "apiproxy_debug.log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(System.currentTimeMillis() + " " + msg + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        writeDebugLog("onCreate start");
        try {
            DynamicColors.applyToActivityIfAvailable(this);
            super.onCreate(savedInstanceState);
            writeDebugLog("super.onCreate done");
            setContentView(R.layout.activity_settings);
            writeDebugLog("setContentView done");
            if (savedInstanceState == null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings_container, new SettingsFragment())
                        .commit();
            }
            com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setTitle(R.string.settings);
                }
            }
            writeDebugLog("onCreate complete");
        } catch (Exception e) {
            String err = "设置页崩溃: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            writeDebugLog(err);
            try {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            try {
                setPreferencesFromResource(R.xml.preferences, rootKey);

                ListPreference languagePref = findPreference("language");
                if (languagePref != null) {
                    languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                        String lang = newValue.toString();
                        setLocale(lang);
                        requireActivity().recreate();
                        return true;
                    });
                }

                ListPreference themePref = findPreference("theme_mode");
                if (themePref != null) {
                    themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                        AppCompatDelegate.setDefaultNightMode(Integer.parseInt(newValue.toString()));
                        return true;
                    });
                }

                SwitchPreferenceCompat dynamicPref = findPreference("dynamic_colors");
                if (dynamicPref != null) {
                    dynamicPref.setOnPreferenceChangeListener((preference, newValue) -> {
                        requireActivity().recreate();
                        return true;
                    });
                }

                Preference githubPref = findPreference("github_link");
                if (githubPref != null) {
                    githubPref.setOnPreferenceClickListener(preference -> {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/Karzzzzz520/API-Local")));
                        return true;
                    });
                }

                Preference cliAccountsPref = findPreference("cli_accounts");
                if (cliAccountsPref != null) {
                    cliAccountsPref.setOnPreferenceClickListener(preference -> {
                        startActivity(new Intent(requireContext(), CliAccountsActivity.class));
                        return true;
                    });
                }

                Preference versionPref = findPreference("version");
                if (versionPref != null) {
                    try {
                        String version = requireContext().getPackageManager()
                                .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                        versionPref.setSummary("v" + version);
                    } catch (Exception e) {
                        versionPref.setSummary("v1.2.7");
                    }
                }
            } catch (Exception e) {
                try {
                    Toast.makeText(getActivity(), "设置项: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            }
        }

        private void setLocale(String lang) {
            Locale locale;
            switch (lang) {
                case "zh": locale = Locale.CHINESE; break;
                case "ja": locale = Locale.JAPANESE; break;
                default: locale = Locale.ENGLISH; break;
            }
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);
            requireContext().getResources().updateConfiguration(config,
                    requireContext().getResources().getDisplayMetrics());
            requireActivity().getSharedPreferences("apiproxy", Context.MODE_PRIVATE)
                    .edit().putString("language", lang).apply();
        }
    }
}