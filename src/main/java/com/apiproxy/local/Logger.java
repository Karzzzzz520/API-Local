package com.apiproxy.local;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "APIProxy";
    private static File logDir = null;
    private static boolean initialized = false;

    public static void init(File externalFilesDir) {
        if (externalFilesDir == null) return;
        logDir = new File(externalFilesDir, "logs");
        if (!logDir.exists()) logDir.mkdirs();
        initialized = true;
        i("Logger initialized at " + logDir.getAbsolutePath());
    }

    public static void d(String msg) { Log.d(TAG, msg); write("DEBUG", msg); }
    public static void i(String msg) { Log.i(TAG, msg); write("INFO", msg); }
    public static void w(String msg) { Log.w(TAG, msg); write("WARN", msg); }
    public static void e(String msg) { Log.e(TAG, msg); write("ERROR", msg); }
    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
        write("ERROR", msg + " | " + Log.getStackTraceString(tr));
    }

    private static void write(String level, String msg) {
        if (!initialized || logDir == null) return;
        try {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            File logFile = new File(logDir, "app-" + date + ".log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(time + " [" + level + "] " + msg + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }

    public static File getLogFile() {
        if (!initialized || logDir == null) return null;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return new File(logDir, "app-" + date + ".log");
    }
}