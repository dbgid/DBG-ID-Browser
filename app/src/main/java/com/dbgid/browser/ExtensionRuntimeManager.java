package com.dbgid.browser;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.File;
import java.util.ArrayList;

public class ExtensionRuntimeManager {

    private final Activity activity;
    private final FrameLayout container;
    private final ArrayList<WebView> runtimeViews = new ArrayList<WebView>();

    public ExtensionRuntimeManager(Activity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
    }

    public void restart(ArrayList<BrowserStore.ExtensionPackage> extensions) {
        stop();
        if (extensions == null) {
            return;
        }
        for (int i = 0; i < extensions.size(); i++) {
            BrowserStore.ExtensionPackage extension = extensions.get(i);
            if (extension != null && extension.enabled) {
                startExtension(extension);
            }
        }
    }

    public void stop() {
        for (int i = 0; i < runtimeViews.size(); i++) {
            WebView webView = runtimeViews.get(i);
            if (container != null && webView.getParent() == container) {
                container.removeView(webView);
            }
            webView.destroy();
        }
        runtimeViews.clear();
    }

    private void startExtension(BrowserStore.ExtensionPackage extension) {
        if (TextUtils.isEmpty(extension.backgroundPage)
            && extension.backgroundScripts.isEmpty()
            && TextUtils.isEmpty(extension.backgroundServiceWorker)) {
            return;
        }

        WebView webView = new WebView(activity);
        webView.setVisibility(View.GONE);
        webView.setBackgroundColor(Color.TRANSPARENT);
        configure(webView);

        if (container != null) {
            container.addView(webView, new FrameLayout.LayoutParams(1, 1));
        }
        runtimeViews.add(webView);

        try {
            if (!TextUtils.isEmpty(extension.backgroundPage)) {
                File page = new File(extension.directoryPath, extension.backgroundPage);
                if (page.exists()) {
                    webView.loadUrl("file://" + page.getAbsolutePath());
                    return;
                }
            }

            if (!extension.backgroundScripts.isEmpty()) {
                webView.loadDataWithBaseURL(
                    "file://" + ensureSlash(extension.directoryPath),
                    buildBackgroundScriptsHtml(extension),
                    "text/html",
                    "UTF-8",
                    null
                );
                return;
            }

            if (!TextUtils.isEmpty(extension.backgroundServiceWorker)) {
                webView.loadDataWithBaseURL(
                    "file://" + ensureSlash(extension.directoryPath),
                    buildServiceWorkerShimHtml(extension),
                    "text/html",
                    "UTF-8",
                    null
                );
            }
        } catch (Throwable ignored) {}
    }

    private void configure(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= 16) {
            try {
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
            } catch (Throwable ignored) {}
        }
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
    }

    private String buildBackgroundScriptsHtml(BrowserStore.ExtensionPackage extension) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset='utf-8'></head><body>");
        builder.append("<script>");
        builder.append(buildExtensionApiShim(extension));
        builder.append("</script>");
        for (int i = 0; i < extension.backgroundScripts.size(); i++) {
            try {
                builder.append("<script>\n");
                builder.append(BrowserStore.readText(new File(extension.directoryPath, extension.backgroundScripts.get(i))));
                builder.append("\n</script>");
            } catch (Throwable ignored) {}
        }
        builder.append("</body></html>");
        return builder.toString();
    }

    private String buildServiceWorkerShimHtml(BrowserStore.ExtensionPackage extension) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset='utf-8'></head><body><script>");
        builder.append("var self=window;");
        builder.append(buildExtensionApiShim(extension));
        try {
            builder.append(BrowserStore.readText(new File(extension.directoryPath, extension.backgroundServiceWorker)));
        } catch (Throwable ignored) {}
        builder.append("</script></body></html>");
        return builder.toString();
    }

    private String buildExtensionApiShim(BrowserStore.ExtensionPackage extension) {
        String id = extension.webStoreId;
        if (TextUtils.isEmpty(id)) {
            id = extension.id;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("window.chrome=window.chrome||{};");
        builder.append("chrome.runtime=chrome.runtime||{};");
        builder.append("chrome.runtime.id='").append(js(id)).append("';");
        builder.append("chrome.runtime.getURL=function(path){return 'file://").append(js(ensureSlash(extension.directoryPath))).append("'+(path||'');};");
        builder.append("chrome.runtime.sendMessage=function(){console.log('DBG runtime sendMessage', arguments);};");
        builder.append("chrome.runtime.onMessage={addListener:function(){}};");
        builder.append("chrome.storage=chrome.storage||{};");
        builder.append("chrome.storage.local={get:function(k,cb){var raw=localStorage.getItem('dbg_ext_storage')||'{}';var obj=JSON.parse(raw);if(cb)cb(obj);},set:function(v,cb){var raw=localStorage.getItem('dbg_ext_storage')||'{}';var obj=JSON.parse(raw);for(var k in v){obj[k]=v[k];}localStorage.setItem('dbg_ext_storage',JSON.stringify(obj));if(cb)cb();}};");
        return builder.toString();
    }

    private String ensureSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String js(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
