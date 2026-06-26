package com.apiproxy.local;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HtmlLoaderActivity extends AppCompatActivity {
    private EditText urlInput;
    private TextView titleBar;
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_loader);

        urlInput = findViewById(R.id.url_input);
        Button btnLoad = findViewById(R.id.btn_load);
        titleBar = findViewById(R.id.title_bar);
        webView = findViewById(R.id.webview);

        configureWebView();
        btnLoad.setOnClickListener(v -> loadInput());

        String url = getIntent().getStringExtra("url");
        if (url == null && getIntent().getData() != null) {
            url = getIntent().getData().toString();
        }
        if (url != null && !url.isEmpty()) {
            urlInput.setText(url);
            loadInput();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        clearWebCache();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    return openLocalFile(uri.getPath());
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                titleBar.setText("Loaded: " + url + " @ " + now());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100) {
                    titleBar.setText("Loading... " + progress + "%");
                }
            }
        });
    }

    private void loadInput() {
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter URL, file path, or HTML", Toast.LENGTH_SHORT).show();
            return;
        }

        clearWebCache();

        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://") || input.startsWith("content://")) {
            webView.loadUrl(appendNoCache(input));
            return;
        }

        File file = new File(input);
        if (file.exists()) {
            if (file.isDirectory()) {
                Toast.makeText(this, "Path is directory", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isHtml(file.getName())) {
                loadHtmlFile(file);
            } else {
                webView.loadUrl(appendNoCache("file://" + file.getAbsolutePath()));
            }
            return;
        }

        webView.loadDataWithBaseURL(null, injectNoCache(input), "text/html", "UTF-8", null);
    }

    private void loadHtmlFile(File file) {
        try {
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream inputStream = new FileInputStream(file);
            int ignored = inputStream.read(bytes);
            inputStream.close();

            String baseUrl = "file://" + file.getParentFile().getAbsolutePath() + "/";
            String html = new String(bytes, StandardCharsets.UTF_8);
            html = injectBase(injectNoCache(html), baseUrl);
            webView.loadDataWithBaseURL(baseUrl + "?t=" + System.currentTimeMillis(), html, "text/html", "UTF-8", null);
            titleBar.setText("Loaded: " + file.getAbsolutePath() + " @ " + now());
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearWebCache() {
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
    }

    private WebResourceResponse openLocalFile(String path) {
        try {
            if (path == null) return null;
            File file = new File(path);
            if (!file.exists() || file.isDirectory()) return null;
            InputStream inputStream = new FileInputStream(file);
            return new WebResourceResponse(mime(path), "UTF-8", inputStream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String appendNoCache(String url) {
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "_t=" + System.currentTimeMillis();
    }

    private String injectNoCache(String html) {
        String meta = "<meta http-equiv=\"Cache-Control\" content=\"no-cache, no-store, must-revalidate\"><meta http-equiv=\"Pragma\" content=\"no-cache\"><meta http-equiv=\"Expires\" content=\"0\">";
        return injectHead(html, meta);
    }

    private String injectBase(String html, String baseUrl) {
        return injectHead(html, "<base href=\"" + baseUrl + "\">");
    }

    private String injectHead(String html, String tag) {
        String lower = html.toLowerCase(Locale.ROOT);
        int head = lower.indexOf("<head>");
        if (head >= 0) {
            int insert = head + 6;
            return html.substring(0, insert) + tag + html.substring(insert);
        }
        return "<head>" + tag + "</head>" + html;
    }

    private boolean isHtml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private String mime(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            clearWebCache();
            webView.destroy();
        }
        super.onDestroy();
    }
}
