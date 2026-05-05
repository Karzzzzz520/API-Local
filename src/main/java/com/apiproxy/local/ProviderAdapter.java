package com.apiproxy.local;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.ViewHolder> {

    private final List<ApiProvider> providers;
    private final Context context;
    private final OnProviderListener listener;
    private final int port;

    public interface OnProviderListener {
        void onToggleEnabled(ApiProvider provider, boolean enabled);
        void onProviderClick(ApiProvider provider);
        void onProviderLongClick(ApiProvider provider);
    }

    public ProviderAdapter(Context context, List<ApiProvider> providers, int port, OnProviderListener listener) {
        this.context = context;
        this.providers = providers;
        this.port = port;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_provider, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApiProvider provider = providers.get(position);
        holder.tvName.setText(provider.getName());
        holder.tvEndpoint.setText(provider.getLocalEndpoint(port));
        holder.tvApiKeyMasked.setText("API Key: " + provider.getMaskedApiKey());
        holder.switchEnabled.setChecked(provider.isEnabled());

        // Set provider icon based on name
        String name = provider.getName().toLowerCase();
        int iconRes;
        if (name.contains("openai") || name.contains("gpt")) {
            iconRes = R.drawable.logo_openai;
        } else if (name.contains("gemini") || name.contains("google")) {
            iconRes = R.drawable.logo_gemini;
        } else if (name.contains("claude") || name.contains("anthropic")) {
            iconRes = R.drawable.logo_claude;
        } else if (name.contains("deepseek")) {
            iconRes = R.drawable.logo_deepseek;
        } else {
            iconRes = R.drawable.ic_provider;
        }
        holder.ivIcon.setImageResource(iconRes);

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onToggleEnabled(provider, isChecked);
        });

        holder.itemView.setOnClickListener(v -> listener.onProviderClick(provider));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onProviderLongClick(provider);
            return true;
        });

        // Copy endpoint on tap
        holder.tvEndpoint.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("endpoint", provider.getLocalEndpoint(port));
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, R.string.endpoint_copied, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return providers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvEndpoint, tvApiKeyMasked;
        SwitchMaterial switchEnabled;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivProviderIcon);
            tvName = itemView.findViewById(R.id.tvProviderName);
            tvEndpoint = itemView.findViewById(R.id.tvEndpointUrl);
            tvApiKeyMasked = itemView.findViewById(R.id.tvApiKeyMasked);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
        }
    }
}