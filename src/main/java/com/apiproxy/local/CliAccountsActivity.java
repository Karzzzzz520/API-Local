package com.apiproxy.local;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class CliAccountsActivity extends AppCompatActivity {

    private static class CliTool {
        String name, description, authUrl;
        CliTool(String name, String description, String authUrl) {
            this.name = name;
            this.description = description;
            this.authUrl = authUrl;
        }
    }

    private final List<CliTool> tools = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cli_accounts);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("CLI 工具授权");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tools.add(new CliTool("Claude Code",
                "Anthropic 的 CLI 编程工具\n需 API Key 授权",
                "https://console.anthropic.com/settings/keys"));
        tools.add(new CliTool("Copilot CLI",
                "GitHub 的 AI CLI 助手\n需 GitHub Token 授权",
                "https://github.com/settings/tokens"));
        tools.add(new CliTool("Codex CLI",
                "OpenAI 的 CLI 编程工具\n需 API Key 授权",
                "https://platform.openai.com/api-keys"));
        tools.add(new CliTool("Gemini CLI",
                "Google 的 AI CLI 工具\n需 API Key 授权",
                "https://aistudio.google.com/app/apikey"));

        RecyclerView recyclerView = findViewById(R.id.recyclerAccounts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ToolAdapter(tools, tool -> {
            Logger.d("Opening auth: " + tool.name + " -> " + tool.authUrl);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tool.authUrl)));
        }));

        findViewById(R.id.emptyView).setVisibility(View.GONE);
        findViewById(R.id.btnAddAccount).setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private static class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.VH> {
        private final List<CliTool> data;
        private final OnToolClickListener listener;
        interface OnToolClickListener { void onClick(CliTool tool); }

        ToolAdapter(List<CliTool> data, OnToolClickListener listener) {
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
            rlp.bottomMargin = 12;
            item.setLayoutParams(rlp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(android.graphics.Color.parseColor("#1A000000"));
            item.setBackground(bg);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextSize(18);
            tvName.getPaint().setFakeBoldText(true);
            TextView tvDesc = new TextView(parent.getContext());
            tvDesc.setTextSize(13);
            tvDesc.setAlpha(0.7f);
            item.addView(tvName);
            item.addView(tvDesc);
            return new VH(item, tvName, tvDesc);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CliTool tool = data.get(pos);
            h.tvName.setText(tool.name);
            h.tvDesc.setText(tool.description);
            h.itemView.setOnClickListener(v -> listener.onClick(tool));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDesc;
            VH(View v, TextView tvName, TextView tvDesc) {
                super(v);
                this.tvName = tvName;
                this.tvDesc = tvDesc;
            }
        }
    }
}