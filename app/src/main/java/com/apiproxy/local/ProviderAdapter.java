package com.apiproxy.local;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务商列表适配器
 */
public class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.ViewHolder> {
    
    public interface OnProviderClickListener {
        void onProviderClick(ApiProvider provider);
        void onProviderLongClick(ApiProvider provider);
    }

    private List<ApiProvider> providers = new ArrayList<>();
    private OnProviderClickListener listener;

    public void setProviders(List<ApiProvider> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnProviderClickListener(OnProviderClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_provider, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApiProvider provider = providers.get(position);
        holder.bind(provider);
    }

    @Override
    public int getItemCount() {
        return providers.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivStatus;
        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvApiKey;
        private final TextView tvBaseUrl;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStatus = itemView.findViewById(R.id.iv_status);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvApiKey = itemView.findViewById(R.id.tv_api_key);
            tvBaseUrl = itemView.findViewById(R.id.tv_base_url);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProviderClick(providers.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProviderLongClick(providers.get(pos));
                    return true;
                }
                return false;
            });
        }

        void bind(ApiProvider provider) {
            tvName.setText(provider.getName());
            tvBaseUrl.setText(provider.getBaseUrl());
            
            if (provider.hasApiKey()) {
                tvApiKey.setText(provider.getMaskedApiKey());
                ivStatus.setImageTintList(null);
                ivStatus.setImageResource(R.drawable.ic_status_active);
                ivIcon.setAlpha(1.0f);
            } else {
                tvApiKey.setText(R.string.not_configured);
                ivStatus.setImageTintList(null);
                ivStatus.setImageResource(R.drawable.ic_status_inactive);
                ivIcon.setAlpha(0.5f);
            }

            // 设置图标
            int iconRes = getProviderIcon(provider.getId());
            ivIcon.setImageResource(iconRes);
        }

        private int getProviderIcon(String id) {
            switch (id) {
                case "openai":
                    return R.drawable.ic_provider_openai;
                case "gemini":
                    return R.drawable.ic_provider_gemini;
                case "claude":
                    return R.drawable.ic_provider_claude;
                case "deepseek":
                    return R.drawable.ic_provider_deepseek;
                default:
                    return R.drawable.ic_provider_default;
            }
        }
    }
}
