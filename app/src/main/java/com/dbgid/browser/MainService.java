package com.dbgid.browser;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Proxy;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

public class MainService extends Service {
    private JSONObject data;
    private String command;
    private String importantScript;
    private String overrideScript;
    private boolean taskDone = false;
    private boolean skipFlag = false;
    private boolean close = false;
    private int webViewLoaded = 0;
    private JSONObject webViewLoadedObject = new JSONObject();
    private WebView mWebView;
    private Thread thread;
    private CookieManager mCookieManager;
    private final Map<String, String> extraHeaders = new HashMap<String, String>();
    private Socket mSocket;
    private int serviceID;
    private SetRandomUserAgent.DeviceProfile activeProfile;

    private void applyDefaultHeaders(String userAgent) {
        extraHeaders.clear();
        if (userAgent != null && !userAgent.trim().isEmpty()) {
            extraHeaders.put("User-Agent", userAgent);
        }
    }

    private void applyModernWebGlOverride(WebView view) {
        if (view == null || overrideScript == null) {
            return;
        }
        view.evaluateJavascript(overrideScript, null);
    }

    public void createNotification(String content) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    String.valueOf(serviceID),
                    String.valueOf(serviceID),
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setSound(null, null);
            serviceChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(serviceChannel);
        }

        Intent notificationIntent = new Intent(this, StopServiceBroadCast.class);
        notificationIntent.putExtra("action", "exit");

        int pendingFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, serviceID, notificationIntent, pendingFlags);

        Notification.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, String.valueOf(serviceID));
        } else {
            notificationBuilder = new Notification.Builder(this);
        }
        notificationBuilder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .addAction(0, "Exit", pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true);
        Notification notification = notificationBuilder.build();

        notificationManager.notify(serviceID, notification);
        startForeground(serviceID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        serviceID = (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE);
        createNotification("Init Successfully");

        Uri uriData = intent != null ? intent.getData() : null;
        if (uriData != null) {
            String stringData = uriData.toString();
            if (stringData.startsWith("webdriver")) {
                String dataParameter = extractDataParameter(stringData);
                if (dataParameter != null) {
                    stringData = base64Decode(dataParameter);
                }
            }
            try {
                data = new JSONObject(stringData);
                Locale locale = new Locale(data.get("lang").toString());
                setLocale(locale);
            } catch (JSONException ignored) {
            }
        }

        mCookieManager = CookieManager.getInstance();
        mWebView = new WebView(this);
        activeProfile = SetRandomUserAgent.randomProfile();
        overrideScript = SetWebGl.buildOverrideJavascript(activeProfile);
        mWebView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(final WebView view, String url, Bitmap icon) {
                applyModernWebGlOverride(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!webViewLoadedObject.has("onPageFinished")) {
                    webViewLoaded += 1;
                    runScriptAfterLoaded(view);
                    try {
                        webViewLoadedObject.put("onPageFinished", true);
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public void onLoadResource(final WebView view, String url) {
                super.onLoadResource(view, url);
                applyModernWebGlOverride(view);
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, int process) {
                if (process == 100) {
                    if (!webViewLoadedObject.has("onProgressChanged")) {
                        webViewLoaded += 1;
                        runScriptAfterLoaded(view);
                        try {
                            webViewLoadedObject.put("onProgressChanged", true);
                        } catch (JSONException ignored) {
                        }
                    }
                }
            }
        });
        mWebView.addJavascriptInterface(this, "android");
        mWebView.setFocusable(true);
        mWebView.setFocusableInTouchMode(true);
        mWebView.setScrollbarFadingEnabled(true);
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setAllowContentAccess(true);
        mWebView.getSettings().setBuiltInZoomControls(false);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setUseWideViewPort(false);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setGeolocationEnabled(true);
        mWebView.getSettings().setDatabaseEnabled(true);
        mWebView.getSettings().setSaveFormData(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mWebView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        mWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mWebView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        mWebView.getSettings().setAllowFileAccessFromFileURLs(true);
        mWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        String startupUserAgent = activeProfile.buildUserAgent();
        mWebView.getSettings().setUserAgentString(startupUserAgent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCookieManager.setAcceptThirdPartyCookies(mWebView, true);
        }

        applyDefaultHeaders(startupUserAgent);

        if (uriData == null) {
            loadUrl(mWebView, "about:blank");
        } else {
            String stringData = uriData.toString();
            try {
                data = LaunchPayloadParser.parseIntent(intent);
                if (data == null) {
                    throw new JSONException("Unable to parse launch payload");
                }
                command = data.optString("command");
            } catch (JSONException ex) {
                Toast.makeText(getApplicationContext(), "Error parsing JSONObject", Toast.LENGTH_LONG).show();
            }

            if ("init".equals(command)) {
                final Handler handler = new Handler();
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String host = data.get("host").toString();
                            int port = data.getInt("port");
                            mSocket = new Socket(host, port);
                            OutputStream output = mSocket.getOutputStream();
                            final PrintWriter writer = new PrintWriter(output, true);
                            InputStream input = mSocket.getInputStream();
                            final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                            String response;
                            while ((response = reader.readLine()) != null) {
                                final String finalResponse = response;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        receiver(finalResponse);
                                        while (true) {
                                            if (data.length() != 0) {
                                                taskDone = true;
                                                break;
                                            }
                                        }
                                    }
                                });
                                while (true) {
                                    if (taskDone && (skipFlag || isWebLoaded()) && data.has("result")) {
                                        writer.print(data.toString().length() + data.toString());
                                        writer.flush();
                                        data = new JSONObject();
                                        taskDone = false;
                                        skipFlag = false;
                                        importantScript = null;
                                        if (close) {
                                            if (mSocket != null) {
                                                mSocket.close();
                                            }
                                            deleteAllCookie();
                                            clearAppData();
                                            stopSelf(startId);
                                        }
                                        break;
                                    }
                                }
                            }
                        } catch (JSONException | IOException ignored) {
                        }
                    }
                });
                thread.start();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        deleteAllCookie();
        clearAppData();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String extractDataParameter(String url) {
        int dataParamStartIndex = url.indexOf("data=");
        if (dataParamStartIndex != -1) {
            String dataParameterValue = url.substring(dataParamStartIndex + 5);
            try {
                return URLDecoder.decode(dataParameterValue, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return null;
    }

    private String base64Encode(String data) {
        return new String(Base64.encode(data.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
    }

    private String base64Decode(String data) {
        return new String(Base64.decode(data.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
    }

    public void clearCookies(String url) {
        String cookiestring = mCookieManager.getCookie(url);
        if (cookiestring != null) {
            for (String cookie : cookiestring.split(";")) {
                String scriptClearCookies = String.format("%1$s=; Max-Age=-999999", cookie.split("=")[0].trim());
                mCookieManager.setCookie(url, scriptClearCookies);
            }
        }
    }

    private float convertDpToPixel(float dp) {
        return dp * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    private void clearAppData() {
        try {
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
        } catch (Exception ignored) {
        }
    }

    private void click(float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        long upTime = SystemClock.uptimeMillis();
        MotionEvent keyDown = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent keyUp = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0);
        mWebView.dispatchTouchEvent(keyDown);
        mWebView.dispatchTouchEvent(keyUp);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void deleteAllCookie() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCookieManager.removeAllCookies(null);
            mCookieManager.flush();
        } else {
            CookieSyncManager.createInstance(this);
            mCookieManager.removeAllCookie();
            CookieSyncManager.getInstance().sync();
        }
        if (mWebView != null) {
            mWebView.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.clearFormData();
                    mWebView.clearSslPreferences();
                    mWebView.clearCache(true);
                    mWebView.clearHistory();
                }
            });
        }
        WebStorage.getInstance().deleteAllData();
    }

    private void executeScript(String script) {
        while (true) {
            if (isWebLoaded()) {
                mWebView.evaluateJavascript(
                        "(function() { return " + script + "; })();",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String result) {
                                result = result.replaceAll("^\"|\"$", "");
                                try {
                                    data.put("result", base64Encode(result));
                                } catch (JSONException ignored) {
                                }
                            }
                        }
                );
                break;
            }
        }
    }

    private void executeScript(String script, boolean promise) {
        while (true) {
            if (isWebLoaded()) {
                mWebView.evaluateJavascript("(function() { return " + script + "; })();", null);
                break;
            }
        }
    }

    private boolean isWebLoaded() {
        return webViewLoaded >= 2;
    }

    private <T> Iterable<T> iteratorToIterable(final Iterator<T> iterator) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return iterator;
            }
        };
    }

    private String getAttribute(String name, JSONObject recv) throws JSONException {
        if (recv.has(name)) {
            return base64Decode(recv.get(name).toString());
        }
        return "";
    }

    private String getAttribute(String name, JSONObject recv, String elseReturn) throws JSONException {
        if (recv.has(name)) {
            return base64Decode(recv.get(name).toString());
        }
        return elseReturn;
    }

    private void loadUrl(WebView view, String url) {
        createNotification(url);
        if (url.startsWith("javascript")) {
            executeScript(url.split(":", 2)[1], true);
        } else {
            webViewLoaded = 0;
            webViewLoadedObject = new JSONObject();
            if (overrideScript != null) {
                mWebView.setVisibility(View.INVISIBLE);
            }
            if (extraHeaders.isEmpty()) {
                view.loadUrl(url);
            } else {
                view.loadUrl(url, new HashMap<String, String>(extraHeaders));
            }
        }
    }

    private String title(String string) {
        StringBuilder result = new StringBuilder();
        for (String str : string.split("-")) {
            if (result.length() > 0) {
                result.append("-");
            }
            result.append(capitalize(str));
        }
        return result.toString();
    }

    @JavascriptInterface
    public void returnAwait(String result) {
        result = result.replaceAll("^\"|\"$", "");
        try {
            data.put("result", base64Encode(result));
        } catch (JSONException ignored) {
        }
    }

    private void runScriptAfterLoaded(final WebView view) {
        if (isWebLoaded()) {
            if (importantScript != null) {
                executeScript(importantScript, true);
            }
            mCookieManager.setAcceptCookie(true);
            mCookieManager.acceptCookie();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCookieManager.flush();
            } else {
                CookieSyncManager.createInstance(this);
                CookieSyncManager.getInstance().sync();
            }
            if (overrideScript != null) {
                view.evaluateJavascript(
                        "(function() { return " + overrideScript + "; })();",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String result) {
                                view.setVisibility(View.VISIBLE);
                            }
                        }
                );
            }
        }
    }

    private void setLocale(Locale newLocale) {
        Resources resources = getResources();
        final Configuration config = resources.getConfiguration();
        final Locale curLocale = getLocale(config);
        if (!curLocale.equals(newLocale)) {
            Locale.setDefault(newLocale);
            final Configuration conf = new Configuration(config);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                conf.setLocale(newLocale);
            } else {
                conf.locale = newLocale;
            }
            resources.updateConfiguration(conf, resources.getDisplayMetrics());
        }
    }

    private static Locale getLocale(Configuration config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return config.getLocales().get(0);
        }
        return config.locale;
    }

    private void sendKeyEvent(int keyCode) {
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        mWebView.dispatchKeyEvent(event);
    }

    private void swipe(final float xStart, final float yStart, final float xEnd, final float yEnd, final int speed) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int xFlag = 0;
                int yFlag = 0;
                float xMove = xStart;
                float yMove = yStart;
                long downTime = SystemClock.uptimeMillis();
                long upTime = SystemClock.uptimeMillis();
                if ((xStart - xEnd) < 0) {
                    xFlag = 1;
                }
                if ((xStart - xEnd) > 0) {
                    xFlag = 2;
                }
                if ((yStart - yEnd) < 0) {
                    yFlag = 1;
                }
                if ((yStart - yEnd) > 0) {
                    yFlag = 2;
                }
                mWebView.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_DOWN, xStart, yStart, 0));
                int count = 0;
                while (true) {
                    if (xMove != xEnd) {
                        if (xFlag == 1) {
                            if ((xMove + count) <= xEnd) {
                                xMove += count;
                            } else {
                                xFlag = 0;
                            }
                        }
                        if (xFlag == 2) {
                            if ((xMove - count) >= xEnd) {
                                xMove -= count;
                            } else {
                                xFlag = 0;
                            }
                        }
                    }
                    if (yMove != yEnd) {
                        if (yFlag == 1) {
                            if ((yMove + count) <= yEnd) {
                                yMove += count;
                            } else {
                                yFlag = 0;
                            }
                        }
                        if (yFlag == 2) {
                            if ((yMove - count) >= yEnd) {
                                yMove -= count;
                            } else {
                                yFlag = 0;
                            }
                        }
                    }
                    if (xFlag == 0 && yFlag == 0) {
                        break;
                    }
                    mWebView.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_MOVE, xMove, yMove, 0));
                    count += (new Random().nextInt(speed + 1)) + (speed % 2 == 0 ? speed / 2 : (speed - 1) / 2);
                }
                mWebView.dispatchTouchEvent(MotionEvent.obtain(downTime, ++upTime, MotionEvent.ACTION_UP, xEnd, yEnd, 0));
            }
        });
        thread.start();
    }

    private void setProxy(WebView webView, String host, String port) {
        Context appContext = webView.getContext().getApplicationContext();
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", port);
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", port);
        try {
            Class applictionCls = Class.forName("android.app.Application");
            Field loadedApkField = applictionCls.getField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(appContext);
            Class loadedApkCls = Class.forName("android.app.LoadedApk");
            Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
            receiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object rec : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = rec.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
                        onReceiveMethod.invoke(rec, appContext, intent);
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException |
                 IllegalArgumentException | NoSuchMethodException |
                 InvocationTargetException ignored) {
        }
    }

    private void receiver(String response) {
        try {
            data = LaunchPayloadParser.parseRaw(response);
            if (data == null) {
                throw new JSONException("Unable to parse webdriver command");
            }
            command = getAttribute("command", data);
            String none = base64Encode("None");

            switch (command) {
                case "close": {
                    skipFlag = true;
                    close = true;
                    data.put("result", none);
                    break;
                }
                case "clear cookie": {
                    skipFlag = true;
                    String url = getAttribute("url", data);
                    String cookieName = getAttribute("cookie_name", data);
                    String scriptClearCookie = String.format("%1$s=; Max-Age=-999999", cookieName);
                    if (url.isEmpty()) {
                        mCookieManager.setCookie(mWebView.getUrl(), scriptClearCookie);
                    } else {
                        mCookieManager.setCookie(url, scriptClearCookie);
                    }
                    data.put("result", none);
                    break;
                }
                case "clear cookies": {
                    skipFlag = true;
                    String url = getAttribute("url", data);
                    if (url.isEmpty()) {
                        clearCookies(mWebView.getUrl());
                    } else {
                        clearCookies(url);
                    }
                    data.put("result", none);
                    break;
                }
                case "current url": {
                    skipFlag = true;
                    String url = mWebView.getUrl();
                    if (url == null) {
                        data.put("result", "");
                    } else {
                        data.put("result", base64Encode(url));
                    }
                    break;
                }
                case "click java": {
                    skipFlag = true;
                    String[] position = getAttribute("position", data).split(" ");
                    float x = convertDpToPixel(Float.parseFloat(position[0]));
                    float y = convertDpToPixel(Float.parseFloat(position[1]));
                    click(x, y);
                    data.put("result", none);
                    break;
                }
                case "clear local storage": {
                    skipFlag = false;
                    executeScript("window.localStorage.clear()");
                    break;
                }
                case "clear session storage": {
                    skipFlag = false;
                    executeScript("window.sessionStorage.clear()");
                    break;
                }
                case "delete all cookie": {
                    skipFlag = true;
                    deleteAllCookie();
                    data.put("result", none);
                    break;
                }
                case "execute script": {
                    skipFlag = false;
                    String script = getAttribute("script", data);
                    executeScript(script);
                    break;
                }
                case "find element": {
                    skipFlag = false;
                    String script = "";
                    String request = getAttribute("request", data);
                    String path = getAttribute("path", data, "document");
                    String by = getAttribute("by", data);
                    String value = getAttribute("value", data).replace("\"", "'").replace("\\\"", "\\'");
                    List<String> except = new ArrayList<String>();
                    except.add("wait until element");
                    except.add("wait until not element");
                    if (request.isEmpty() || except.contains(request)) {
                        switch (by) {
                            case "id":
                                script = String.format("%s.querySelector(\"#%s\")", path, value);
                                break;
                            case "xpath":
                                script = String.format("%s.evaluate(\"%s\", %s, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue", path, value, path);
                                break;
                            case "link text":
                                script = String.format("%s.querySelector(\"[href=%s]\")", path, value);
                                break;
                            case "partial link text":
                                script = String.format("%s.querySelector(\"[href*=%s]\")", path, value);
                                break;
                            case "name":
                                script = String.format("%s.querySelector(\"[name=%s]\")", path, value);
                                break;
                            case "tag name":
                            case "css selector":
                                script = String.format("%s.querySelector(\"%s\")", path, value);
                                break;
                            case "class name":
                                script = String.format("%s.querySelector(\".%s\")", path, value);
                                break;
                        }
                    } else {
                        script = path;
                    }
                    data.put("path", base64Encode(script));
                    switch (request) {
                        case "get attribute":
                            String scriptGetAttribute = String.format("(function() { var element = %1$s; return element.%2$s })()", script, getAttribute("attribute_name", data));
                            executeScript(scriptGetAttribute);
                            break;
                        case "remove attribute":
                            String scriptRemoveAttribute = String.format("(function() { var element = %1$s; return element.removeAttribute(\"%2$s\") })()", script, getAttribute("attribute_name", data));
                            executeScript(scriptRemoveAttribute);
                            break;
                        case "send key":
                            sendKeyEvent(Integer.parseInt(getAttribute("key", data)));
                            data.put("result", none);
                            break;
                        case "send text":
                            char[] charArray = getAttribute("text", data).toCharArray();
                            KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
                            KeyEvent[] events = charMap.getEvents(charArray);
                            for (KeyEvent event : events) {
                                mWebView.dispatchKeyEvent(event);
                            }
                            data.put("result", none);
                            break;
                        case "set attribute":
                            String attributeValue = getAttribute("attribute_value", data);
                            if (getAttribute("is_string", data).equals("true")) {
                                attributeValue = String.format("\"%1$s\"", attributeValue);
                            }
                            String scriptSetAttribute = String.format("(function() { var element = %1$s; element.%2$s = %3$s })()", script, getAttribute("attribute_name", data), attributeValue);
                            executeScript(scriptSetAttribute);
                            break;
                        case "wait until element":
                            String funcWaitUntil = String.format("function wait() { return new Promise(resolve => { if (%1$s) { return resolve(%1$s); } const observer = new MutationObserver(mutations => { if (%1$s) { resolve(%1$s); observer.disconnect(); } }); observer.observe(document.body, { childList: true, subtree: true }); }); }", script);
                            String scriptWaitUntilElement = String.format("(function() { %1$s; return wait() })().then((element) => {android.returnAwait(element.outerHTML)})", funcWaitUntil);
                            importantScript = scriptWaitUntilElement;
                            executeScript(scriptWaitUntilElement, true);
                            break;
                        case "wait until not element":
                            String funcWaitUntilNot = String.format("function wait() { return new Promise(resolve => { if (%1$s) { return resolve(); } const observer = new MutationObserver(mutations => { if (%1$s) { resolve(); observer.disconnect(); } }); observer.observe(document.body, { childList: true, subtree: true }); }); }", "!" + script);
                            String scriptWaitUntilNotElement = String.format("(function() { %1$s; return wait() })().then(() => {android.returnAwait(\"%2$s\")})", funcWaitUntilNot, "True");
                            importantScript = scriptWaitUntilNotElement;
                            executeScript(scriptWaitUntilNotElement, true);
                            break;
                        default:
                            data.put("path", base64Encode(script));
                            executeScript(String.format("%s%s", script, (!request.isEmpty() ? request : ".outerHTML")));
                            break;
                    }
                    break;
                }
                case "find elements": {
                    skipFlag = false;
                    String script = "";
                    String document = "";
                    String path = getAttribute("path", data, "document");
                    String by = getAttribute("by", data);
                    String value = getAttribute("value", data).replace("\"", "'").replace("\\\"", "\\'");
                    switch (by) {
                        case "id":
                            document = String.format("%1$s.querySelectorAll(\"#%2$s\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                        case "xpath":
                            document = String.format("%1$s.evaluate(\"%2$s\", %1$s, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null)", path, value);
                            script = String.format("(function() { var elements = %1$s; return [...Array(elements.snapshotLength)].map((_, index) => [index, elements.snapshotItem(index).outerHTML])})()", document);
                            break;
                        case "link text":
                            document = String.format("%1$s.querySelectorAll(\"[href=%2$s]\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                        case "partial link text":
                            document = String.format("%1$s.querySelectorAll(\"[href*=%2$s]\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                        case "name":
                            document = String.format("%1$s.querySelectorAll(\"[name=%2$s]\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                        case "tag name":
                        case "css selector":
                            document = String.format("%1$s.querySelectorAll(\"%2$s\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                        case "class name":
                            document = String.format("%1$s.querySelectorAll(\".%2$s\")", path, value);
                            script = String.format("(function() { var index = 0, data = []; %1$s.forEach((element) => { data.push([index++, element.outerHTML.replace(/\"/g, \"'\")]); }); return data })()", document);
                            break;
                    }
                    data.put("path", base64Encode(document));
                    executeScript(script);
                    break;
                }
                case "get": {
                    skipFlag = false;
                    String url = getAttribute("url", data);
                    loadUrl(mWebView, url);
                    data.put("result", none);
                    break;
                }
                case "get cookie": {
                    skipFlag = true;
                    String url = getAttribute("url", data);
                    String cookieName = getAttribute("cookie_name", data);
                    String cookies = "";
                    if (url.isEmpty()) {
                        cookies = mCookieManager.getCookie(mWebView.getUrl());
                    } else {
                        cookies = mCookieManager.getCookie(url);
                    }
                    String scriptGetCookie = String.format("%1$s=", cookieName);
                    if (cookies == null || !cookies.contains(scriptGetCookie)) {
                        data.put("result", "");
                    } else {
                        data.put("result", base64Encode(scriptGetCookie + cookies.split(scriptGetCookie)[1].split(";")[0]));
                    }
                    break;
                }
                case "get cookies": {
                    skipFlag = true;
                    String url = getAttribute("url", data);
                    String cookies = "";
                    if (url.isEmpty()) {
                        cookies = mCookieManager.getCookie(mWebView.getUrl());
                    } else {
                        cookies = mCookieManager.getCookie(url);
                    }
                    if (cookies == null) {
                        data.put("result", "");
                    } else {
                        data.put("result", base64Encode(cookies));
                    }
                    break;
                }
                case "get user agent": {
                    skipFlag = true;
                    String userAgent = extraHeaders.get("User-Agent");
                    if (userAgent == null) {
                        data.put("result", "");
                    } else {
                        data.put("result", base64Encode(userAgent));
                    }
                    break;
                }
                case "get headers": {
                    skipFlag = true;
                    data.put("result", new JSONObject(extraHeaders));
                    break;
                }
                case "get local storage": {
                    skipFlag = false;
                    executeScript("JSON.stringify(window.localStorage)");
                    break;
                }
                case "get session storage": {
                    skipFlag = false;
                    executeScript("JSON.stringify(window.sessionStorage)");
                    break;
                }
                case "get recaptcha v3 token": {
                    skipFlag = false;
                    String siteKey = getAttribute("site_key", data);
                    String action = getAttribute("action", data);
                    String scriptGetRecaptchaV3Token = "";
                    if (!action.isEmpty()) {
                        scriptGetRecaptchaV3Token = String.format("\"%1$s\", { action: \"%2$s\" }", siteKey, action);
                    } else {
                        scriptGetRecaptchaV3Token = String.format("\"%1$s\"", siteKey);
                    }
                    executeScript(String.format("grecaptcha.execute(%1$s).then(function(token) { android.returnAwait(token) });", scriptGetRecaptchaV3Token), true);
                    break;
                }
                case "override js function": {
                    skipFlag = true;
                    String script = getAttribute("script", data);
                    if (overrideScript != null) {
                        if (overrideScript.endsWith(";")) {
                            overrideScript += script;
                        } else {
                            overrideScript += ";" + script;
                        }
                    } else {
                        overrideScript = script;
                    }
                    data.put("result", none);
                    break;
                }
                case "page source": {
                    skipFlag = true;
                    if (isWebLoaded()) {
                        executeScript("document.querySelector(\"html\").outerHTML");
                    } else {
                        data.put("result", "");
                    }
                    break;
                }
                case "swipe": {
                    skipFlag = true;
                    String[] position = getAttribute("position", data).split(" ");
                    int speed = Integer.parseInt(getAttribute("speed", data));
                    swipe(Float.parseFloat(position[0]), Float.parseFloat(position[1]), Float.parseFloat(position[2]), Float.parseFloat(position[3]), speed);
                    data.put("result", none);
                    break;
                }
                case "swipe down": {
                    skipFlag = true;
                    DisplayMetrics size = Resources.getSystem().getDisplayMetrics();
                    swipe(size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 8, size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 2, 15);
                    data.put("result", none);
                    break;
                }
                case "swipe up": {
                    skipFlag = true;
                    DisplayMetrics size = Resources.getSystem().getDisplayMetrics();
                    swipe(size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 2, size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 8, 15);
                    data.put("result", none);
                    break;
                }
                case "set cookie": {
                    skipFlag = true;
                    String url = getAttribute("url", data);
                    String cookieName = getAttribute("cookie_name", data);
                    String value = getAttribute("value", data);
                    String scriptSetCookie = String.format("%1$s=%2$s", cookieName, value);
                    if (url.isEmpty()) {
                        mCookieManager.setCookie(mWebView.getUrl(), scriptSetCookie);
                    } else {
                        mCookieManager.setCookie(url, scriptSetCookie);
                    }
                    data.put("result", none);
                    break;
                }
                case "set user agent": {
                    skipFlag = true;
                    String userAgent = getAttribute("user_agent", data);
                    applyDefaultHeaders(userAgent);
                    mWebView.getSettings().setUserAgentString(userAgent);
                    data.put("result", none);
                    break;
                }
                case "set proxy": {
                    skipFlag = true;
                    String[] proxy = getAttribute("proxy", data).split(" ");
                    setProxy(mWebView, proxy[0], proxy[1]);
                    data.put("result", none);
                    break;
                }
                case "scroll to": {
                    skipFlag = true;
                    String[] position = getAttribute("position", data).split(" ");
                    mWebView.scrollTo(Integer.parseInt(position[0]), Integer.parseInt(position[1]));
                    data.put("result", none);
                    break;
                }
                case "set headers": {
                    skipFlag = true;
                    JSONObject headers = new JSONObject(getAttribute("headers", data));
                    for (String key : iteratorToIterable(headers.keys())) {
                        String value = headers.get(key).toString();
                        extraHeaders.put(key, value);
                        if ("user-agent".equals(key.toLowerCase())) {
                            mWebView.getSettings().setUserAgentString(value);
                        }
                    }
                    data.put("result", none);
                    break;
                }
                case "set local storage": {
                    skipFlag = false;
                    String value = getAttribute("value", data);
                    if (getAttribute("is_string", data).equals("true")) {
                        value = String.format("\"%1$s\"", value);
                    }
                    executeScript(String.format("window.localStorage.setItem(\"%1$s\", %2$s)", getAttribute("key", data), value));
                    break;
                }
                case "set session storage": {
                    skipFlag = false;
                    String value = getAttribute("value", data);
                    if (getAttribute("is_string", data).equals("true")) {
                        value = String.format("\"%1$s\"", value);
                    }
                    executeScript(String.format("window.sessionStorage.setItem(\"%1$s\", %2$s)", getAttribute("key", data), value));
                    break;
                }
                case "title": {
                    skipFlag = true;
                    if (isWebLoaded()) {
                        executeScript("document.title");
                    } else {
                        data.put("result", "");
                    }
                    break;
                }
            }
        } catch (JSONException ex) {
            Toast.makeText(getApplicationContext(), "Error parsing JSONObject", Toast.LENGTH_LONG).show();
        }
    }
}
