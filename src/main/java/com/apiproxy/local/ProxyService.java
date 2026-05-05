package com.apiproxy.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * 代理服务器的前台服务，保证后台运行
 */
public class ProxyService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "proxy_service_channel";

    private ProxyServer proxyServer;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        ProxyService getService() {
            return ProxyService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("API Proxy 正在启动...");
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void startProxy(int port, List<ApiProvider> providers, ProxyServer.LogCallback logCallback,
                           ProxyServer.StatusCallback statusCallback) {
        if (proxyServer != null && proxyServer.isRunning()) {
            proxyServer.stop();
        }

        proxyServer = new ProxyServer(port, providers);
        proxyServer.setLogCallback(logCallback);
        proxyServer.setStatusCallback(status -> {
            if (statusCallback != null) statusCallback.onStatusChanged(status);
            if (!status) {
                stopSelf();
            }
        });

        try {
            proxyServer.start();
            updateNotification("API Proxy 运行在 localhost:" + port);
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.onLog("❌ 启动失败: " + e.getMessage());
            }
            stopSelf();
        }
    }

    public void stopProxy() {
        if (proxyServer != null) {
            proxyServer.stop();
            proxyServer = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    public boolean isProxyRunning() {
        return proxyServer != null && proxyServer.isRunning();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_desc));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }
}