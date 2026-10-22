package io.github.osintcheatsheet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/** Built-in browser so web tools (including image uploads) work without leaving the app. */
public class BrowserActivity extends Activity {

    static final String EXTRA_URL = "url";
    private static final int FILE_CHOOSER = 1;

    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private ValueCallback<Uri[]> pendingUpload;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((v, actionId, event) -> {
            String input = address.getText().toString().trim();
            if (input.isEmpty()) return false;
            web.loadUrl(input.contains(" ") || !input.contains(".")
                    ? "https://duckduckgo.com/?q=" + Uri.encode(input)
                    : CheatSheet.normalizeUrl(input));
            web.requestFocus();
            return true;
        });
        root.addView(address, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 3)));

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                // intent:, mailto:, tg:, etc. belong to other apps.
                try {
                    Intent intent = "intent".equals(scheme)
                            ? Intent.parseUri(request.getUrl().toString(), Intent.URI_INTENT_SCHEME)
                            : new Intent(Intent.ACTION_VIEW, request.getUrl());
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(null);
                    startActivity(intent);
                } catch (Exception e) {
                    Ui.toast(BrowserActivity.this, "No app can open " + scheme + " links");
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                address.setText(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                setTitle(title);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingUpload != null) pendingUpload.onReceiveValue(null);
                pendingUpload = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    pendingUpload = null;
                    return false;
                }
                return true;
            }
        });
        // Downloads are handed to the system browser / download manager.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                Ui.openExternal(this, url));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            String url = getIntent().getStringExtra(EXTRA_URL);
            web.loadUrl(url == null ? "https://duckduckgo.com" : url);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && pendingUpload != null) {
            pendingUpload.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            pendingUpload = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Reload");
        menu.add(0, 2, 0, "Forward");
        menu.add(0, 3, 0, "Wayback Machine copy");
        menu.add(0, 4, 0, "Open in external browser");
        menu.add(0, 5, 0, "Copy link");
        menu.add(0, 6, 0, "Share");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String url = web.getUrl() == null ? "" : web.getUrl();
        switch (item.getItemId()) {
            case 1: web.reload(); return true;
            case 2: if (web.canGoForward()) web.goForward(); return true;
            case 3: web.loadUrl(Ui.waybackUrl(url)); return true;
            case 4: Ui.openExternal(this, url); return true;
            case 5: Ui.copy(this, url); return true;
            case 6: Ui.share(this, web.getTitle(), url); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
