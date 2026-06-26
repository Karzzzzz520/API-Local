package com.apiproxy.local;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HtmlLoaderActivity extends AppCompatActivity {
    private EditText urlInput;
    private TextView titleBar;
    private WebView webView;
    private File sandboxRoot;

    private final ActivityResultLauncher<Uri> directoryPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                importDirectoryToSandbox(uri);
            }
    );

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_loader);

        sandboxRoot = new File(getFilesDir(), "html_sandbox");
        if (!sandboxRoot.exists()) sandboxRoot.mkdirs();

        urlInput = findViewById(R.id.url_input);
        Button btnLoad = findViewById(R.id.btn_load);
        Button btnImport = findViewById(R.id.btn_import);
        titleBar = findViewById(R.id.title_bar);
        webView = findViewById(R.id.webview);

        configureWebView();
        btnLoad.setOnClickListener(v -> loadInput());
        btnImport.setOnClickListener(v -> openDirectoryPicker());

        String url = getIntent().getStringExtra("url");
        if (url == null && getIntent().getData() != null) {
            url = getIntent().getData().toString();
        }
        if (url != null && !url.isEmpty()) {
            urlInput.setText(url);
            loadInput();
        } else {
            File index = new File(sandboxRoot, "index.html");
            if (index.exists()) {
                urlInput.setText(index.getAbsolutePath());
                loadHtmlFile(index);
            }
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

    private void openDirectoryPicker() {
        directoryPicker.launch(null);
    }

    private void importDirectoryToSandbox(Uri uri) {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, uri);
            if (root == null || !root.isDirectory()) {
                Toast.makeText(this, "Invalid directory", Toast.LENGTH_SHORT).show();
                return;
            }
            deleteRecursively(sandboxRoot);
            sandboxRoot.mkdirs();
            copyDocumentTree(root, sandboxRoot);

            File index = findIndexFile(sandboxRoot);
            if (index != null) {
                urlInput.setText(index.getAbsolutePath());
                loadHtmlFile(index);
                Toast.makeText(this, "Imported to sandbox", Toast.LENGTH_SHORT).show();
            } else {
                titleBar.setText("Imported, but index.html not found");
                Toast.makeText(this, "Imported, but index.html not found", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDocumentTree(DocumentFile source, File targetDir) throws Exception {
        DocumentFile[] files = source.listFiles();
        for (DocumentFile item : files) {
            String name = safeName(item.getName());
            if (name.isEmpty()) continue;
            File target = new File(targetDir, name);
            if (item.isDirectory()) {
                target.mkdirs();
                copyDocumentTree(item, target);
            } else if (item.isFile()) {
                copyDocumentFile(item, target);
            }
        }
    }

    private void copyDocumentFile(DocumentFile source, File target) throws Exception {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        InputStream input = getContentResolver().openInputStream(source.getUri());
        if (input == null) return;
        OutputStream output = new FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }
        output.close();
        input.close();
    }

    private void loadInput() {
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter URL, sandbox path, or HTML", Toast.LENGTH_SHORT).show();
            return;
        }

        clearWebCache();

        if (input.startsWith("http://") || input.startsWith("https://")) {
            webView.loadUrl(appendNoCache(input));
            return;
        }

        File file = resolveSandboxFile(input);
        if (file.exists()) {
            if (file.isDirectory()) {
                File index = findIndexFile(file);
                if (index != null) loadHtmlFile(index);
                else Toast.makeText(this, "Directory has no index.html", Toast.LENGTH_SHORT).show();
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

    private File resolveSandboxFile(String input) {
        if (input.startsWith("file://")) input = Uri.parse(input).getPath();
        File file = new File(input);
        if (file.isAbsolute()) return file;
        return new File(sandboxRoot, input);
    }

    private void loadHtmlFile(File file) {
        try {
            byte[] bytes = readAll(file);
            String baseUrl = "file://" + file.getParentFile().getAbsolutePath() + "/";
            String html = new String(bytes, StandardCharsets.UTF_8);
            html = injectBase(injectNoCache(html), baseUrl);
            webView.loadDataWithBaseURL(baseUrl + "?t=" + System.currentTimeMillis(), html, "text/html", "UTF-8", null);
            titleBar.setText("Sandbox: " + file.getAbsolutePath() + " @ " + now());
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAll(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream inputStream = new FileInputStream(file);
        int ignored = inputStream.read(bytes);
        inputStream.close();
        return bytes;
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

    private File findIndexFile(File directory) {
        File exact = new File(directory, "index.html");
        if (exact.exists()) return exact;
        File htm = new File(directory, "index.htm");
        if (htm.exists()) return htm;
        File[] files = directory.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findIndexFile(file);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private String safeName(String name) {
        if (name == null) return "";
        return name.replace("/", "_").replace("\\", "_");
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
