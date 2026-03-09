package com.dbgid.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONException;
import org.json.JSONObject;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        if (!isTaskRoot() && intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && Intent.ACTION_MAIN.equals(intent.getAction())) {
            finish();
            return;
        }

        Uri uriData = intent != null ? intent.getData() : null;
        if (uriData != null) {
            String stringData = uriData.toString();
            try {
                JSONObject jsonData = LaunchPayloadParser.parseIntent(intent);
                if (jsonData == null) {
                    throw new JSONException("Unable to parse launch payload");
                }
                Intent targetIntent = jsonData.optBoolean("state")
                        ? new Intent(this, MainActivity.class)
                        : new Intent(this, MainService.class);
                if (intent.getAction() != null) {
                    targetIntent.setAction(intent.getAction());
                }
                targetIntent.setData(uriData);
                targetIntent.putExtra(MainActivity.EXTRA_WEBDRIVER_MODE, jsonData.optBoolean("state"));
                if (jsonData.optBoolean("state")) {
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    startActivity(targetIntent);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(targetIntent);
                    } else {
                        startService(targetIntent);
                    }
                }
            } catch (JSONException ignored) {
                Intent fallbackIntent = new Intent(this, MainActivity.class);
                if (intent.getAction() != null) {
                    fallbackIntent.setAction(intent.getAction());
                } else {
                    fallbackIntent.setAction(Intent.ACTION_VIEW);
                }
                fallbackIntent.setData(uriData);
                startActivity(fallbackIntent);
            }
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }

        finish();
    }
}
