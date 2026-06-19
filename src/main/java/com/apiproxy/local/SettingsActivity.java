package com.apiproxy.local;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
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

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
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
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Language preference
            ListPreference languagePref = findPreference("language");
            if (languagePref != null) {
                languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    setLocale(newValue.toString());
                    requireActivity().recreate();
                    return true;
                });
            }

            // Theme mode preference
            ListPreference themePref = findPreference("theme_mode");
            if (themePref != null) {
                themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int mode = Integer.parseInt(newValue.toString());
                    AppCompatDelegate.setDefaultNightMode(mode);
                    return true;
                });
            }

            // Dynamic colors preference
            SwitchPreferenceCompat dynamicPref = findPreference("dynamic_colors");
            if (dynamicPref != null) {
                dynamicPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    requireActivity().recreate();
                    return true;
                });
            }

            // GitHub link
            Preference githubPref = findPreference("github_link");
            if (githubPref != null) {
                githubPref.setOnPreferenceClickListener(preference -> {
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/Karzzzzz520/API-Local")
                    );
                    startActivity(intent);
                    return true;
                });
            }

            // Version info
            Preference versionPref = findPreference("version");
            if (versionPref != null) {
                try {
                    String version = requireContext().getPackageManager()
                            .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                    versionPref.setSummary("v" + version);
                } catch (Exception e) {
                    versionPref.setSummary("v1.2.5");
                }
            }
        }

        private void setLocale(String lang) {
            Locale locale;
            switch (lang) {
                case "zh":
                    locale = Locale.CHINESE;
                    break;
                case "ja":
                    locale = Locale.JAPANESE;
                    break;
                case "en":
                default:
                    locale = Locale.ENGLISH;
                    break;
            }
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);
            requireContext().getResources().updateConfiguration(config,
                    requireContext().getResources().getDisplayMetrics());

            SharedPreferences prefs = requireContext().getSharedPreferences("apiproxy", MODE_PRIVATE);
            prefs.edit().putString("language", lang).apply();
        }
    }
}