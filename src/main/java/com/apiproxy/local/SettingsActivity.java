package com.apiproxy.local;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.color.DynamicColors;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

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
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            ListPreference languagePref = findPreference("language");
            ListPreference themePref = findPreference("theme_mode");
            SwitchPreferenceCompat dynamicPref = findPreference("dynamic_colors");
            Preference githubPref = findPreference("github_link");
            Preference cliAccountsPref = findPreference("cli_accounts");
            Preference versionPref = findPreference("version");

            if (languagePref != null) {
                languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    setLocale(newValue.toString());
                    requireActivity().recreate();
                    return true;
                });
            }

            if (themePref != null) {
                themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    AppCompatDelegate.setDefaultNightMode(Integer.parseInt(newValue.toString()));
                    return true;
                });
            }

            if (dynamicPref != null) {
                dynamicPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    requireActivity().recreate();
                    return true;
                });
            }

            if (githubPref != null) {
                githubPref.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/Karzzzzz520/API-Local")));
                    return true;
                });
            }

            if (cliAccountsPref != null) {
                cliAccountsPref.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), CliAccountsActivity.class));
                    return true;
                });
            }

            if (versionPref != null) {
                try {
                    String version = requireContext().getPackageManager()
                            .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                    versionPref.setSummary("v" + version);
                } catch (Exception e) {
                    versionPref.setSummary("v1.2.7");
                }
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
        }
    }
}