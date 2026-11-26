package com.ausgram1.arqms.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PORTAL_URL =
            "https://script.google.com/macros/s/AKfycbx6j-gS7p807DJLwf6AlMl-7x1NKiUjq80mJMZ5271UzLRy6lG4i-0gHXJkalAMM94z/exec";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private long lastBackPressMs = 0L;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && (data.getData() != null || data.getClipData() != null)) {
                        if (data.getClipData() != null) {
                            ClipData clip = data.getClipData();
                            results = new Uri[clip.getItemCount()];
                            for (int i = 0; i < clip.getItemCount(); i++) {
                                results[i] = clip.getItemAt(i).getUri();
                            }
                        } else if (data.getData() != null) {
                            results = new Uri[]{data.getData()};
                        }
                    } else if (cameraImageUri != null) {
                        results = new Uri[]{cameraImageUri};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
                cameraImageUri = null;
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF004D28);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                10,
                Gravity.TOP
        );
        progressBar.setLayoutParams(pbParams);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        root.addView(webView);
        root.addView(progressBar);
        setContentView(root);

        configureWebView(webView);

        // Clear only stale HTTP cache on launch so Apps Script New Version deploys reflect immediately
        webView.clearCache(true);

        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        headers.put("Pragma", "no-cache");
        webView.loadUrl(PORTAL_URL, headers);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String url = uri.toString();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : "";

                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")
                        || host.contains("wa.me") || host.contains("api.whatsapp.com")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "App not found to open link", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                if (host.equals("script.google.com")
                        || host.endsWith(".googleusercontent.com")
                        || host.equals("drive.google.com")
                        || host.equals("docs.google.com")
                        || host.equals("accounts.google.com")) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                if (url != null && url.contains("script.google.com")) {
                    injectCleanBannerRemover(view);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    String offlineHtml = "<html><body style='font-family:sans-serif;text-align:center;padding:48px 20px;background:#f1f5f9;color:#0f172a;'>"
                            + "<h2 style='color:#004d28;'>ARQMS Portal — Ausgram-I</h2>"
                            + "<p style='color:#475569;'>Internet connection unavailable. Please check mobile data/Wi-Fi and tap Retry.</p>"
                            + "<button onclick=\"window.location.href='" + PORTAL_URL + "'\" "
                            + "style='background:#004d28;color:#fff;border:none;padding:12px 24px;border-radius:8px;font-size:16px;font-weight:bold;'>Retry Connection</button>"
                            + "</body></html>";
                    view.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null);
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                List<Intent> extraIntents = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 101);
                } else {
                    try {
                        Intent camIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        File photoFile = createTempPhotoFile();
                        cameraImageUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                photoFile
                        );
                        camIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                        extraIntents.add(camIntent);
                    } catch (Exception ignored) {
                        cameraImageUri = null;
                    }
                }

                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("*/*");
                fileIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf", "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});
                fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                Intent chooser = Intent.createChooser(fileIntent, "Select Document or Capture Photo");
                if (!extraIntents.isEmpty()) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Intent[0]));
                }
                fileChooserLauncher.launch(chooser);
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Support print popups (about:blank / blob:) and Drive links seamlessly
                WebView popupWebView = new WebView(MainActivity.this);
                popupWebView.getSettings().setJavaScriptEnabled(true);
                popupWebView.getSettings().setDomStorageEnabled(true);
                popupWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView pv, String url) {
                        if (url != null && (url.startsWith("about:blank") || url.startsWith("blob:"))) {
                            printWebViewDocument(pv, pv.getTitle());
                        } else if (url != null && url.startsWith("http")) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (Exception ignored) {}
                        }
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        wv.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimetype);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) req.addRequestHeader("cookie", cookies);
                    req.addRequestHeader("User-Agent", userAgent);
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    req.setTitle(filename);
                    req.setDescription("Downloading ARQMS file...");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(MainActivity.this, "Downloading " + filename, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private File createTempPhotoFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("ARQMS_SCAN_" + ts + "_", ".jpg", getCacheDir());
    }

    private void printWebViewDocument(WebView targetWebView, String title) {
        try {
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pm == null) return;
            String jobName = (title != null && !title.trim().isEmpty()) ? title.trim() : "ARQMS_Document";
            PrintDocumentAdapter adapter = targetWebView.createPrintDocumentAdapter(jobName);
            pm.print(jobName, adapter, new PrintAttributes.Builder().build());
        } catch (Exception ignored) {}
    }

    // Safely hide Google Apps Script warning banner while keeping #sandboxFrame full-screen
    private void injectCleanBannerRemover(WebView view) {
        String js = "(function(){"
                + "function fix(){"
                + "  try{"
                + "    var f=document.getElementById('sandboxFrame')||document.querySelector('iframe');"
                + "    if(!f) return;"
                + "    f.style.setProperty('position','fixed','important');"
                + "    f.style.setProperty('top','0','important');"
                + "    f.style.setProperty('left','0','important');"
                + "    f.style.setProperty('width','100vw','important');"
                + "    f.style.setProperty('height','100vh','important');"
                + "    f.style.setProperty('border','0','important');"
                + "    f.style.setProperty('z-index','2147483647','important');"
                + "    var ns=document.querySelectorAll('tr,div,section,header');"
                + "    for(var i=0;i<ns.length;i++){"
                + "      var el=ns[i]; if(el===f||el.contains(f)) continue;"
                + "      var tx=(el.innerText||'');"
                + "      if(tx.indexOf('Google Apps Script user')!==-1||tx.indexOf('Report abuse')!==-1){"
                + "        el.style.setProperty('display','none','important');"
                + "      }"
                + "    }"
                + "  }catch(e){}"
                + "}"
                + "fix(); setTimeout(fix,400); setTimeout(fix,1400);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 2000) {
            super.onBackPressed();
        } else {
            lastBackPressMs = now;
            Toast.makeText(this, "Press Back again to exit ARQMS", Toast.LENGTH_SHORT).show();
        }
    }
}
