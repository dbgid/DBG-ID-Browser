package com.dbgid.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

public class ShowErrorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.error_layout);
        Intent intent = getIntent();
        String errorMessage = intent != null ? intent.getStringExtra("errorMessage") : "";
        WebView webview = findViewById(R.id.webview);
        if (errorMessage == null) {
            errorMessage = "";
        }
        webview.loadDataWithBaseURL(null, errorMessage, "text/html", "UTF-8", null);
    }
}
