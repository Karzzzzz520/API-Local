package com.apiproxy.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;

/**
 * 前台服务 - 管理代理服务生命周期
 */
public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private static final String CHANNEL_ID = "proxy_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.apiproxy.local.START";
    public static final String ACTION_STOP = "com.apiproxy.local.STOP";
    public static final String EXTRA_PORT = "port";

    private ProxyServer proxyServer;
    private ProviderManager providerManager;
    private int currentPort;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        createNotificationChannel();
        providerManager = new ProviderManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            currentPort = intent.getIntExtra(EXTRA_PORT, 8080);
            startProxy();
        } else if (ACTION_STOP.equals(action)) {
            stopProxy();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startProxy() {
        if (proxyServer != null && proxyServer.isRunning()) {
            Log.d(TAG, "Proxy already running");
            return;
        }

        proxyServer = new ProxyServer(currentPort);
        proxyServer.setProviderManager(providerManager);
        
        // 设置日志回调
        proxyServer.setLogCallback(message -> {
            Intent broadcast = new Intent(MainActivity.ACTION_LOG);
            broadcast.putExtra(MainActivity.EXTRA_LOG_MESSAGE, message);
            sendBroadcast(broadcast);
        });

        if (proxyServer.start()) {
            Log.d(TAG, "Proxy started on port " + currentPort);
            updateNotification(true, currentPort);
            
            // 发送状态广播
            Intent broadcast = new Intent(MainActivity.ACTION_PROXY_STATUS);
            broadcast.putExtra(MainActivity.EXTRA_PROXY_RUNNING, true);
            broadcast.putExtra(MainActivity.EXTRA_PORT, currentPort);
            sendBroadcast(broadcast);
        } else {
            Log.e(TAG, "Failed to start proxy");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void stopProxy() {
        if (proxyServer != null) {
            proxyServer.stop();
            proxyServer = null;
            
            // 发送状态广播
            Intent broadcast = new Intent(MainActivity.ACTION_PROXY_STATUS);
            broadcast.putExtra(MainActivity.EXTRA_PROXY_RUNNING, false);
            sendBroadcast(broadcast);
            
            Log.d(TAG, "Proxy stopped");
        }
    }

    public boolean isProxyRunning() {
        return proxyServer != null && proxyServer.isRunning();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(boolean running, int port) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        // 停止按钮
        Intent stopIntent = new Intent(this, ProxyService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags);

        String title = running ? getString(R.string.notification_title_running) 
                               : getString(R.string.notification_title_stopped);
        String text = running ? getString(R.string.notification_text_running, port) 
                              : getString(R.string.notification_text_stopped);

        int icon = running ? R.drawable.ic_play : R.drawable.ic_stop;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }
}
