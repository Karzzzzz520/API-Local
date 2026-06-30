package com.apiproxy.local;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class CliAccountsActivity extends AppCompatActivity {

    private static class OAuthTool {
        String name, description, authUrl;
        OAuthTool(String name, String description, String authUrl) {
            this.name = name;
            this.description = description;
            this.authUrl = authUrl;
        }
    }

    private final List<OAuthTool> tools = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cli_accounts);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("OAuth 授权");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tools.add(new OAuthTool("Claude Code",
                "Anthropic CLI - OAuth Device Flow",
                "https://console.anthropic.com/settings/keys"));
        tools.add(new OAuthTool("Copilot CLI",
                "GitHub CLI - OAuth Device Flow",
                "https://github.com/login/device"));
        tools.add(new OAuthTool("Codex CLI",
                "OpenAI CLI - API Key (no public OAuth)",
                "https://platform.openai.com/api-keys"));
        tools.add(new OAuthTool("Gemini CLI",
                "Google Gemini CLI - OAuth PKCE Flow",
                "https://aistudio.google.com/app/apikey"));

        RecyclerView recyclerView = findViewById(R.id.recyclerAccounts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ToolAdapter(tools, tool -> {
            Logger.d("Opening: " + tool.name + " -> " + tool.authUrl);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tool.authUrl)));
        }));

        findViewById(R.id.emptyView).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnAddAccount).setVisibility(android.view.View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private static class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.VH> {
        private final List<OAuthTool> data;
        private final OnToolClickListener listener;
        interface OnToolClickListener { void onClick(OAuthTool tool); }

        ToolAdapter(List<OAuthTool> data, OnToolClickListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @androidx.annotation.NonNull
        @Override
        public VH onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.widget.LinearLayout item = new android.widget.LinearLayout(parent.getContext());
            item.setOrientation(android.widget.LinearLayout.VERTICAL);
            item.setPadding(32, 24, 32, 24);
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = 12;
            item.setLayoutParams(rlp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(android.graphics.Color.parseColor("#1A000000"));
            item.setBackground(bg);

            android.widget.TextView tvName = new android.widget.TextView(parent.getContext());
            tvName.setTextSize(18);
            tvName.getPaint().setFakeBoldText(true);
            android.widget.TextView tvDesc = new android.widget.TextView(parent.getContext());
            tvDesc.setTextSize(13);
            tvDesc.setAlpha(0.7f);
            item.addView(tvName);
            item.addView(tvDesc);
            return new VH(item, tvName, tvDesc);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            OAuthTool tool = data.get(pos);
            h.tvName.setText(tool.name);
            h.tvDesc.setText(tool.description);
            h.itemView.setOnClickListener(v -> listener.onClick(tool));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvDesc;
            VH(android.view.View v, android.widget.TextView tvName, android.widget.TextView tvDesc) {
                super(v);
                this.tvName = tvName;
                this.tvDesc = tvDesc;
            }
        }
    }
}