package com.dbgid.browser;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.net.Proxy;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    public static final String EXTRA_WEBDRIVER_MODE = "com.dbgid.browser.extra.WEBDRIVER_MODE";
    private static final String HOME_URL = "dbgid://home";
    private static final String DEVTOOLS_URL = "dbgid://devtools";
    private static final String NETWORK_URL = "dbgid://network";
    private static final String USERSCRIPTS_URL = "dbgid://userscripts";
    private static final String HOME_RENDER_URL = "https://browser.dbgid/";
    private static final String PREF_COOKIES_ENABLED = "cookiesEnabled";
    private static final String PREF_NETWORK_OVERLAY = "networkInspectorEnabled";
    private static final String PREF_DOH_ENABLED = "dohEnabled";
    private static final String PREF_DOH_PROVIDER = "dohProvider";
    private static final String PREF_DOH_ENDPOINT = "dohEndpoint";
    private static final String PREF_SELECTED_IP = "selectedIpDisplay";

    private EditText urlInput;
    private Button clearUrlButton;
    private TextView statusText;
    private TextView pageMetaText;
    private ProgressBar pageProgress;
    private LinearLayout pullRefreshIndicator;
    private TextView pullRefreshText;
    private FrameLayout webContainer;
    private FrameLayout runtimeContainer;
    private LinearLayout tabStrip;
    private HorizontalScrollView tabScroll;
    private Button backButton;
    private Button forwardButton;
    private Button homeButton;
    private Button reloadButton;
    private Button newTabButton;
    private Button tabsButton;
    private Button bookmarksButton;
    private Button scriptsButton;
    private Button downloadsButton;
    private Button menuButton;
    private LinearLayout turnstileKeyPanel;
    private LinearLayout turnstileSolvePanel;
    private TextView turnstileKeyBody;
    private TextView turnstileSolveBody;

    private final ArrayList<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private int currentTabIndex = -1;

    private BrowserStore store;
    private ArrayList<BrowserStore.Bookmark> bookmarks;
    private ArrayList<BrowserStore.UserScript> scripts;
    private ArrayList<BrowserStore.DownloadRecord> downloads;
    private java.util.LinkedHashMap<String, BrowserStore.SiteConfig> siteConfigs;
    private SharedPreferences preferences;
    private CookieManager cookieManager;
    private final Map<String, String> extraHeaders = new HashMap<String, String>();
    private boolean cookiesEnabled = true;
    private boolean networkInspectorEnabled = false;
    private boolean dohEnabled = false;
    private String dohProvider = DoHResolver.PROVIDER_CLOUDFLARE;
    private String dohEndpoint = DoHResolver.ENDPOINT_CLOUDFLARE;
    private String selectedIpDisplay = "";
    private SetIps.Entry selectedIpEntry;
    private final SetRandomUserAgent.DeviceProfile browserProfile = SetRandomUserAgent.randomProfile();
    private final String automationUserAgent = browserProfile.buildUserAgent();
    private final String desktopUserAgent = SetRandomUserAgent.latestDesktopChromeUserAgent();
    private final String webGlOverrideScript = SetWebGl.buildOverrideJavascript(browserProfile);
    private String normalMobileUserAgent;
    private JSONObject webDriverLaunchPayload;
    private JSONObject webDriverData = new JSONObject();
    private String webDriverCommand;
    private String webDriverImportantScript;
    private String webDriverOverrideScript;
    private boolean webDriverTaskDone;
    private boolean webDriverSkipFlag;
    private boolean webDriverClose;
    private int webDriverLoaded;
    private JSONObject webDriverLoadedObject = new JSONObject();
    private Thread webDriverThread;
    private Socket webDriverSocket;
    private BrowserTab webDriverTab;
    private boolean webDriverSessionActive;
    private boolean turnstileKeyPanelMinimized;
    private boolean turnstileKeyPanelExpanded;
    private boolean turnstileSolvePanelMinimized;
    private boolean turnstileSolvePanelExpanded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        setContentView(R.layout.activity_main);
        webDriverLaunchPayload = LaunchPayloadParser.parseIntent(getIntent());
        applyLaunchLocale(webDriverLaunchPayload);

        store = new BrowserStore(this);
        bookmarks = store.loadBookmarks();
        scripts = store.loadScripts();
        downloads = store.loadDownloads();
        siteConfigs = store.loadSiteConfigs();
        preferences = getSharedPreferences("browser_runtime", MODE_PRIVATE);
        cookiesEnabled = preferences.getBoolean(PREF_COOKIES_ENABLED, true);
        networkInspectorEnabled = preferences.getBoolean(PREF_NETWORK_OVERLAY, false);
        dohEnabled = preferences.getBoolean(PREF_DOH_ENABLED, false);
        dohProvider = preferences.getString(PREF_DOH_PROVIDER, DoHResolver.PROVIDER_CLOUDFLARE);
        dohEndpoint = preferences.getString(PREF_DOH_ENDPOINT, DoHResolver.defaultEndpointForProvider(dohProvider));
        selectedIpDisplay = preferences.getString(PREF_SELECTED_IP, "");
        selectedIpEntry = SetIps.Entry.fromDisplayValue(selectedIpDisplay);
        if (TextUtils.isEmpty(dohEndpoint)) {
            dohEndpoint = DoHResolver.defaultEndpointForProvider(dohProvider);
        }
        cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(cookiesEnabled);
        normalMobileUserAgent = automationUserAgent;
        applyDefaultHeaders(normalMobileUserAgent);

        bindViews();
        bindOverlayControls();
        wireControls();
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        webDriverLaunchPayload = LaunchPayloadParser.parseIntent(intent);
        applyLaunchLocale(webDriverLaunchPayload);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        stopWebDriverSession();
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            webContainer.removeView(tab.webView);
            tab.webView.destroy();
        }
        tabs.clear();
        super.onDestroy();
    }

    private void applyDefaultHeaders(String userAgent) {
        extraHeaders.clear();
        if (!TextUtils.isEmpty(userAgent)) {
            extraHeaders.put("User-Agent", userAgent);
        }
        if (!webDriverSessionActive && selectedIpEntry != null && !TextUtils.isEmpty(selectedIpEntry.ip)) {
            extraHeaders.put("X-Forwarded-For", selectedIpEntry.ip);
            extraHeaders.put("X-Real-IP", selectedIpEntry.ip);
            extraHeaders.put("Client-IP", selectedIpEntry.ip);
            extraHeaders.put("Forwarded", "for=" + selectedIpEntry.ip);
        }
    }

    private void applyLaunchLocale(JSONObject payload) {
        if (payload == null) {
            return;
        }
        String language = payload.optString("lang");
        if (TextUtils.isEmpty(language)) {
            return;
        }
        setLocale(new Locale(language));
    }

    private void startWebDriverSession(final JSONObject payload) {
        if (!isWebDriverInitPayload(payload)) {
            return;
        }
        stopWebDriverSession();
        webDriverSessionActive = true;
        applyDefaultHeaders(automationUserAgent);
        webDriverData = payload;
        webDriverCommand = payload.optString("command");
        webDriverTaskDone = false;
        webDriverSkipFlag = false;
        webDriverClose = false;
        webDriverImportantScript = null;
        webDriverOverrideScript = null;
        webDriverLoaded = 0;
        webDriverLoadedObject = new JSONObject();
        if (webDriverTab == null) {
            webDriverTab = getCurrentTab();
        }
        final Handler handler = new Handler();
        webDriverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String host = payload.optString("host");
                    int port = payload.optInt("port");
                    webDriverSocket = new Socket(host, port);
                    OutputStream output = webDriverSocket.getOutputStream();
                    final PrintWriter writer = new PrintWriter(output, true);
                    InputStream input = webDriverSocket.getInputStream();
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                    String response;
                    while ((response = reader.readLine()) != null) {
                        final String finalResponse = response;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                receiveWebDriverCommand(finalResponse);
                                while (true) {
                                    if (webDriverData.length() != 0) {
                                        webDriverTaskDone = true;
                                        break;
                                    }
                                }
                            }
                        });
                        while (true) {
                            if (webDriverTaskDone && (webDriverSkipFlag || isWebDriverLoaded()) && webDriverData.has("result")) {
                                writer.print(webDriverData.toString().length() + webDriverData.toString());
                                writer.flush();
                                webDriverData = new JSONObject();
                                webDriverTaskDone = false;
                                webDriverSkipFlag = false;
                                webDriverImportantScript = null;
                                if (webDriverClose) {
                                    stopWebDriverSession();
                                    deleteAllCookie();
                                    clearAppData();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            finish();
                                        }
                                    });
                                    return;
                                }
                                break;
                            }
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }, "dbgid-webdriver-activity");
        webDriverThread.start();
    }

    private void stopWebDriverSession() {
        webDriverSessionActive = false;
        applyDefaultHeaders(normalMobileUserAgent);
        if (webDriverSocket != null) {
            try {
                webDriverSocket.close();
            } catch (IOException ignored) {
            }
            webDriverSocket = null;
        }
        if (webDriverThread != null) {
            webDriverThread.interrupt();
            webDriverThread = null;
        }
    }

    private boolean isAutomationTab(BrowserTab tab) {
        return tab != null && webDriverSessionActive && tab == webDriverTab;
    }

    private BrowserTab ensureWebDriverTab() {
        if (webDriverTab != null) {
            return webDriverTab;
        }
        if (tabs.isEmpty()) {
            webDriverTab = createTab("about:blank", true, true);
        } else {
            webDriverTab = getCurrentTab();
        }
        return webDriverTab;
    }

    private WebView getWebDriverWebView() {
        BrowserTab tab = ensureWebDriverTab();
        return tab == null ? null : tab.webView;
    }

    private String base64Encode(String data) {
        return new String(Base64.encode(data.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    private String base64Decode(String data) {
        return new String(Base64.decode(data.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    public void clearCookies(String url) {
        String cookieString = cookieManager.getCookie(url);
        if (cookieString != null) {
            for (String cookie : cookieString.split(";")) {
                String scriptClearCookies = String.format("%1$s=; Max-Age=-999999", cookie.split("=")[0].trim());
                cookieManager.setCookie(url, scriptClearCookies);
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
        WebView webView = getWebDriverWebView();
        if (webView == null) {
            return;
        }
        long downTime = SystemClock.uptimeMillis();
        long upTime = SystemClock.uptimeMillis();
        MotionEvent keyDown = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent keyUp = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(keyDown);
        webView.dispatchTouchEvent(keyUp);
    }

    private void deleteAllCookie() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            CookieSyncManager.createInstance(this);
            cookieManager.removeAllCookie();
            CookieSyncManager.getInstance().sync();
        }
        final WebView webView = getWebDriverWebView();
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    WebView active = getWebDriverWebView();
                    if (active != null) {
                        active.clearFormData();
                        active.clearSslPreferences();
                        active.clearCache(true);
                        active.clearHistory();
                    }
                }
            });
        }
        WebStorage.getInstance().deleteAllData();
    }

    private void executeWebDriverScript(final String script) {
        while (true) {
            if (isWebDriverLoaded()) {
                final WebView webView = getWebDriverWebView();
                if (webView == null) {
                    break;
                }
                webView.evaluateJavascript("(function() { return " + script + "; })();", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String result) {
                        result = result.replaceAll("^\"|\"$", "");
                        try {
                            webDriverData.put("result", base64Encode(result));
                        } catch (JSONException ignored) {
                        }
                    }
                });
                break;
            }
        }
    }

    private void executeWebDriverScript(String script, boolean promise) {
        while (true) {
            if (isWebDriverLoaded()) {
                WebView webView = getWebDriverWebView();
                if (webView != null) {
                    webView.evaluateJavascript("(function() { return " + script + "; })();", null);
                }
                break;
            }
        }
    }

    private boolean isWebDriverLoaded() {
        return webDriverLoaded >= 2;
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

    private void loadWebDriverUrl(String url) {
        BrowserTab tab = ensureWebDriverTab();
        if (tab == null) {
            return;
        }
        if (tab != getCurrentTab()) {
            int index = tabs.indexOf(tab);
            if (index >= 0) {
                selectTab(index);
            }
        }
        if (url.startsWith("javascript")) {
            executeWebDriverScript(url.split(":", 2)[1], true);
            return;
        }
        String normalized = normalizeInput(url);
        webDriverLoaded = 0;
        webDriverLoadedObject = new JSONObject();
        tab.isHome = false;
        tab.displayUrl = normalized;
        applySiteConfig(tab, normalized);
        if (extraHeaders.isEmpty()) {
            tab.webView.loadUrl(normalized);
        } else {
            tab.webView.loadUrl(normalized, new HashMap<String, String>(extraHeaders));
        }
        updateChrome();
    }

    @JavascriptInterface
    public void returnAwait(String result) {
        result = result.replaceAll("^\"|\"$", "");
        try {
            webDriverData.put("result", base64Encode(result));
        } catch (JSONException ignored) {
        }
    }

    private void runWebDriverScriptAfterLoaded(final WebView view) {
        if (!isWebDriverLoaded()) {
            return;
        }
        if (webDriverImportantScript != null) {
            executeWebDriverScript(webDriverImportantScript, true);
        }
        cookieManager.setAcceptCookie(true);
        cookieManager.acceptCookie();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        } else {
            CookieSyncManager.createInstance(this);
            CookieSyncManager.getInstance().sync();
        }
        if (webDriverOverrideScript != null) {
            view.evaluateJavascript("(function() { return " + webDriverOverrideScript + "; })();", null);
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
        WebView webView = getWebDriverWebView();
        if (webView == null) {
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        webView.dispatchKeyEvent(event);
    }

    private void swipe(final float xStart, final float yStart, final float xEnd, final float yEnd, final int speed) {
        Thread swipeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                WebView webView = getWebDriverWebView();
                if (webView == null) {
                    return;
                }
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
                webView.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_DOWN, xStart, yStart, 0));
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
                    webView.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_MOVE, xMove, yMove, 0));
                    count += (new Random().nextInt(speed + 1)) + (speed % 2 == 0 ? speed / 2 : (speed - 1) / 2);
                }
                webView.dispatchTouchEvent(MotionEvent.obtain(downTime, ++upTime, MotionEvent.ACTION_UP, xEnd, yEnd, 0));
            }
        }, "dbgid-webdriver-swipe");
        swipeThread.start();
    }

    private void setProxy(WebView webView, String host, String port) {
        if (webView == null) {
            return;
        }
        Context appContext = webView.getContext().getApplicationContext();
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", port);
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", port);
        try {
            Class applicationCls = Class.forName("android.app.Application");
            Field loadedApkField = applicationCls.getField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(appContext);
            Class loadedApkCls = Class.forName("android.app.LoadedApk");
            Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
            receiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object receiver : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = receiver.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
                        onReceiveMethod.invoke(receiver, appContext, intent);
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException
                 | IllegalArgumentException | NoSuchMethodException
                 | InvocationTargetException ignored) {
        }
    }

    private void receiveWebDriverCommand(String response) {
        try {
            webDriverData = LaunchPayloadParser.parseRaw(response);
            if (webDriverData == null) {
                throw new JSONException("Unable to parse webdriver command");
            }
            webDriverCommand = getAttribute("command", webDriverData);
            String none = base64Encode("None");
            WebView webView = getWebDriverWebView();
            if (webView == null) {
                webDriverData.put("result", none);
                webDriverSkipFlag = true;
                return;
            }

            switch (webDriverCommand) {
                case "close": {
                    webDriverSkipFlag = true;
                    webDriverClose = true;
                    webDriverData.put("result", none);
                    break;
                }
                case "clear cookie": {
                    webDriverSkipFlag = true;
                    String url = getAttribute("url", webDriverData);
                    String cookieName = getAttribute("cookie_name", webDriverData);
                    String scriptClearCookie = String.format("%1$s=; Max-Age=-999999", cookieName);
                    cookieManager.setCookie(url.isEmpty() ? webView.getUrl() : url, scriptClearCookie);
                    webDriverData.put("result", none);
                    break;
                }
                case "clear cookies": {
                    webDriverSkipFlag = true;
                    String url = getAttribute("url", webDriverData);
                    clearCookies(url.isEmpty() ? webView.getUrl() : url);
                    webDriverData.put("result", none);
                    break;
                }
                case "current url": {
                    webDriverSkipFlag = true;
                    String url = webView.getUrl();
                    webDriverData.put("result", url == null ? "" : base64Encode(url));
                    break;
                }
                case "click java": {
                    webDriverSkipFlag = true;
                    String[] position = getAttribute("position", webDriverData).split(" ");
                    float x = convertDpToPixel(Float.parseFloat(position[0]));
                    float y = convertDpToPixel(Float.parseFloat(position[1]));
                    click(x, y);
                    webDriverData.put("result", none);
                    break;
                }
                case "clear local storage": {
                    webDriverSkipFlag = false;
                    executeWebDriverScript("window.localStorage.clear()");
                    break;
                }
                case "clear session storage": {
                    webDriverSkipFlag = false;
                    executeWebDriverScript("window.sessionStorage.clear()");
                    break;
                }
                case "delete all cookie": {
                    webDriverSkipFlag = true;
                    deleteAllCookie();
                    webDriverData.put("result", none);
                    break;
                }
                case "execute script": {
                    webDriverSkipFlag = false;
                    executeWebDriverScript(getAttribute("script", webDriverData));
                    break;
                }
                case "find element": {
                    webDriverSkipFlag = false;
                    String script = "";
                    String request = getAttribute("request", webDriverData);
                    String path = getAttribute("path", webDriverData, "document");
                    String by = getAttribute("by", webDriverData);
                    String value = getAttribute("value", webDriverData).replace("\"", "'").replace("\\\"", "\\'");
                    List<String> deferredRequests = new ArrayList<String>();
                    deferredRequests.add("wait until element");
                    deferredRequests.add("wait until not element");
                    if (request.isEmpty() || deferredRequests.contains(request)) {
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
                    webDriverData.put("path", base64Encode(script));
                    switch (request) {
                        case "get attribute":
                            executeWebDriverScript(String.format("(function() { var element = %1$s; return element.%2$s })()", script, getAttribute("attribute_name", webDriverData)));
                            break;
                        case "remove attribute":
                            executeWebDriverScript(String.format("(function() { var element = %1$s; return element.removeAttribute(\"%2$s\") })()", script, getAttribute("attribute_name", webDriverData)));
                            break;
                        case "send key":
                            sendKeyEvent(Integer.parseInt(getAttribute("key", webDriverData)));
                            webDriverData.put("result", none);
                            break;
                        case "send text":
                            char[] chars = getAttribute("text", webDriverData).toCharArray();
                            KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
                            KeyEvent[] events = charMap.getEvents(chars);
                            if (events != null) {
                                for (KeyEvent event : events) {
                                    webView.dispatchKeyEvent(event);
                                }
                            }
                            webDriverData.put("result", none);
                            break;
                        case "set attribute":
                            String attributeValue = getAttribute("attribute_value", webDriverData);
                            if ("true".equals(getAttribute("is_string", webDriverData))) {
                                attributeValue = String.format("\"%1$s\"", attributeValue);
                            }
                            executeWebDriverScript(String.format("(function() { var element = %1$s; element.%2$s = %3$s })()", script, getAttribute("attribute_name", webDriverData), attributeValue));
                            break;
                        case "wait until element":
                            String waitUntil = String.format("function wait() { return new Promise(resolve => { if (%1$s) { return resolve(%1$s); } const observer = new MutationObserver(mutations => { if (%1$s) { resolve(%1$s); observer.disconnect(); } }); observer.observe(document.body, { childList: true, subtree: true }); }); }", script);
                            String waitUntilElementScript = String.format("(function() { %1$s; return wait() })().then((element) => {android.returnAwait(element.outerHTML)})", waitUntil);
                            webDriverImportantScript = waitUntilElementScript;
                            executeWebDriverScript(waitUntilElementScript, true);
                            break;
                        case "wait until not element":
                            String waitUntilNot = String.format("function wait() { return new Promise(resolve => { if (%1$s) { return resolve(); } const observer = new MutationObserver(mutations => { if (%1$s) { resolve(); observer.disconnect(); } }); observer.observe(document.body, { childList: true, subtree: true }); }); }", "!" + script);
                            String waitUntilNotElementScript = String.format("(function() { %1$s; return wait() })().then(() => {android.returnAwait(\"%2$s\")})", waitUntilNot, "True");
                            webDriverImportantScript = waitUntilNotElementScript;
                            executeWebDriverScript(waitUntilNotElementScript, true);
                            break;
                        default:
                            executeWebDriverScript(String.format("%s%s", script, (!request.isEmpty() ? request : ".outerHTML")));
                            break;
                    }
                    break;
                }
                case "find elements": {
                    webDriverSkipFlag = false;
                    String script = "";
                    String document = "";
                    String path = getAttribute("path", webDriverData, "document");
                    String by = getAttribute("by", webDriverData);
                    String value = getAttribute("value", webDriverData).replace("\"", "'").replace("\\\"", "\\'");
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
                    webDriverData.put("path", base64Encode(document));
                    executeWebDriverScript(script);
                    break;
                }
                case "get": {
                    webDriverSkipFlag = false;
                    String url = getAttribute("url", webDriverData);
                    if (url.isEmpty()) {
                        url = getAttribute("value", webDriverData);
                    }
                    loadWebDriverUrl(url);
                    webDriverData.put("result", none);
                    break;
                }
                case "get cookie": {
                    webDriverSkipFlag = true;
                    String url = getAttribute("url", webDriverData);
                    String cookieName = getAttribute("cookie_name", webDriverData);
                    String cookies = cookieManager.getCookie(url.isEmpty() ? webView.getUrl() : url);
                    String scriptGetCookie = String.format("%1$s=", cookieName);
                    if (cookies == null || !cookies.contains(scriptGetCookie)) {
                        webDriverData.put("result", "");
                    } else {
                        webDriverData.put("result", base64Encode(scriptGetCookie + cookies.split(scriptGetCookie)[1].split(";")[0]));
                    }
                    break;
                }
                case "get cookies": {
                    webDriverSkipFlag = true;
                    String url = getAttribute("url", webDriverData);
                    String cookies = cookieManager.getCookie(url.isEmpty() ? webView.getUrl() : url);
                    webDriverData.put("result", cookies == null ? "" : base64Encode(cookies));
                    break;
                }
                case "get user agent": {
                    webDriverSkipFlag = true;
                    String userAgent = extraHeaders.get("User-Agent");
                    webDriverData.put("result", userAgent == null ? "" : base64Encode(userAgent));
                    break;
                }
                case "get headers": {
                    webDriverSkipFlag = true;
                    webDriverData.put("result", new JSONObject(extraHeaders));
                    break;
                }
                case "get local storage": {
                    webDriverSkipFlag = false;
                    executeWebDriverScript("JSON.stringify(window.localStorage)");
                    break;
                }
                case "get session storage": {
                    webDriverSkipFlag = false;
                    executeWebDriverScript("JSON.stringify(window.sessionStorage)");
                    break;
                }
                case "get recaptcha v3 token": {
                    webDriverSkipFlag = false;
                    String siteKey = getAttribute("site_key", webDriverData);
                    String action = getAttribute("action", webDriverData);
                    String arguments = action.isEmpty()
                            ? String.format("\"%1$s\"", siteKey)
                            : String.format("\"%1$s\", { action: \"%2$s\" }", siteKey, action);
                    executeWebDriverScript(String.format("grecaptcha.execute(%1$s).then(function(token) { android.returnAwait(token) });", arguments), true);
                    break;
                }
                case "override js function": {
                    webDriverSkipFlag = true;
                    String script = getAttribute("script", webDriverData);
                    if (webDriverOverrideScript != null) {
                        webDriverOverrideScript = webDriverOverrideScript.endsWith(";") ? webDriverOverrideScript + script : webDriverOverrideScript + ";" + script;
                    } else {
                        webDriverOverrideScript = script;
                    }
                    webDriverData.put("result", none);
                    break;
                }
                case "page source": {
                    webDriverSkipFlag = true;
                    if (isWebDriverLoaded()) {
                        executeWebDriverScript("document.querySelector(\"html\").outerHTML");
                    } else {
                        webDriverData.put("result", "");
                    }
                    break;
                }
                case "swipe": {
                    webDriverSkipFlag = true;
                    String[] position = getAttribute("position", webDriverData).split(" ");
                    int speed = Integer.parseInt(getAttribute("speed", webDriverData));
                    swipe(Float.parseFloat(position[0]), Float.parseFloat(position[1]), Float.parseFloat(position[2]), Float.parseFloat(position[3]), speed);
                    webDriverData.put("result", none);
                    break;
                }
                case "swipe down": {
                    webDriverSkipFlag = true;
                    DisplayMetrics size = Resources.getSystem().getDisplayMetrics();
                    swipe(size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 8, size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 2, 15);
                    webDriverData.put("result", none);
                    break;
                }
                case "swipe up": {
                    webDriverSkipFlag = true;
                    DisplayMetrics size = Resources.getSystem().getDisplayMetrics();
                    swipe(size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 2, size.widthPixels / 2, size.heightPixels / 4 + size.heightPixels / 8, 15);
                    webDriverData.put("result", none);
                    break;
                }
                case "set cookie": {
                    webDriverSkipFlag = true;
                    String url = getAttribute("url", webDriverData);
                    String cookieName = getAttribute("cookie_name", webDriverData);
                    String value = getAttribute("value", webDriverData);
                    String scriptSetCookie = String.format("%1$s=%2$s", cookieName, value);
                    cookieManager.setCookie(url.isEmpty() ? webView.getUrl() : url, scriptSetCookie);
                    webDriverData.put("result", none);
                    break;
                }
                case "set user agent": {
                    webDriverSkipFlag = true;
                    String userAgent = getAttribute("user_agent", webDriverData);
                    applyDefaultHeaders(userAgent);
                    webView.getSettings().setUserAgentString(userAgent);
                    webDriverData.put("result", none);
                    break;
                }
                case "set proxy": {
                    webDriverSkipFlag = true;
                    String[] proxy = getAttribute("proxy", webDriverData).split(" ");
                    if (proxy.length >= 2) {
                        setProxy(webView, proxy[0], proxy[1]);
                    }
                    webDriverData.put("result", none);
                    break;
                }
                case "scroll to": {
                    webDriverSkipFlag = true;
                    String[] position = getAttribute("position", webDriverData).split(" ");
                    webView.scrollTo(Integer.parseInt(position[0]), Integer.parseInt(position[1]));
                    webDriverData.put("result", none);
                    break;
                }
                case "set headers": {
                    webDriverSkipFlag = true;
                    JSONObject headers = new JSONObject(getAttribute("headers", webDriverData));
                    for (String key : iteratorToIterable(headers.keys())) {
                        String value = headers.get(key).toString();
                        extraHeaders.put(key, value);
                        if ("user-agent".equals(key.toLowerCase())) {
                            webView.getSettings().setUserAgentString(value);
                        }
                    }
                    webDriverData.put("result", none);
                    break;
                }
                case "set local storage": {
                    webDriverSkipFlag = false;
                    String value = getAttribute("value", webDriverData);
                    if ("true".equals(getAttribute("is_string", webDriverData))) {
                        value = String.format("\"%1$s\"", value);
                    }
                    executeWebDriverScript(String.format("window.localStorage.setItem(\"%1$s\", %2$s)", getAttribute("key", webDriverData), value));
                    break;
                }
                case "set session storage": {
                    webDriverSkipFlag = false;
                    String value = getAttribute("value", webDriverData);
                    if ("true".equals(getAttribute("is_string", webDriverData))) {
                        value = String.format("\"%1$s\"", value);
                    }
                    executeWebDriverScript(String.format("window.sessionStorage.setItem(\"%1$s\", %2$s)", getAttribute("key", webDriverData), value));
                    break;
                }
                case "title": {
                    webDriverSkipFlag = true;
                    if (isWebDriverLoaded()) {
                        executeWebDriverScript("document.title");
                    } else {
                        webDriverData.put("result", "");
                    }
                    break;
                }
                default: {
                    webDriverSkipFlag = true;
                    webDriverData.put("result", "");
                    break;
                }
            }
        } catch (JSONException ignored) {
            try {
                webDriverData.put("result", "");
            } catch (JSONException ignoredAgain) {
            }
            webDriverSkipFlag = true;
        }
    }

    @Override
    public void onBackPressed() {
        BrowserTab current = getCurrentTab();
        if (current != null && current.webView.canGoBack()) {
            current.webView.goBack();
            return;
        }
        if (tabs.size() > 1 && currentTabIndex > 0) {
            selectTab(currentTabIndex - 1);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
    }

    private void bindViews() {
        statusText = (TextView) findViewById(R.id.statusText);
        pageMetaText = (TextView) findViewById(R.id.pageMetaText);
        pageProgress = (ProgressBar) findViewById(R.id.pageProgress);
        pullRefreshIndicator = (LinearLayout) findViewById(R.id.pullRefreshIndicator);
        pullRefreshText = (TextView) findViewById(R.id.pullRefreshText);
        webContainer = (FrameLayout) findViewById(R.id.webContainer);
        runtimeContainer = (FrameLayout) findViewById(R.id.runtimeContainer);
        tabStrip = (LinearLayout) findViewById(R.id.tabStrip);
        tabScroll = (HorizontalScrollView) findViewById(R.id.tabScroll);
        urlInput = (EditText) findViewById(R.id.urlInput);
        clearUrlButton = (Button) findViewById(R.id.clearUrlButton);
        backButton = (Button) findViewById(R.id.backButton);
        forwardButton = (Button) findViewById(R.id.forwardButton);
        homeButton = (Button) findViewById(R.id.homeButton);
        reloadButton = (Button) findViewById(R.id.reloadButton);
        newTabButton = (Button) findViewById(R.id.newTabButton);
        tabsButton = (Button) findViewById(R.id.tabsButton);
        bookmarksButton = (Button) findViewById(R.id.bookmarksButton);
        scriptsButton = (Button) findViewById(R.id.scriptsButton);
        downloadsButton = (Button) findViewById(R.id.downloadsButton);
        menuButton = (Button) findViewById(R.id.menuButton);
        turnstileKeyPanel = (LinearLayout) findViewById(R.id.turnstileKeyPanel);
        turnstileSolvePanel = (LinearLayout) findViewById(R.id.turnstileSolvePanel);
        turnstileKeyBody = (TextView) findViewById(R.id.turnstileKeyBody);
        turnstileSolveBody = (TextView) findViewById(R.id.turnstileSolveBody);
    }

    private void bindOverlayControls() {
        Button keyCopyButton = (Button) findViewById(R.id.turnstileKeyCopyButton);
        Button keyMinimizeButton = (Button) findViewById(R.id.turnstileKeyMinimizeButton);
        Button keyMaximizeButton = (Button) findViewById(R.id.turnstileKeyMaximizeButton);
        Button solveCopyButton = (Button) findViewById(R.id.turnstileSolveCopyButton);
        Button solveMinimizeButton = (Button) findViewById(R.id.turnstileSolveMinimizeButton);
        Button solveMaximizeButton = (Button) findViewById(R.id.turnstileSolveMaximizeButton);
        TextView keyTitle = (TextView) findViewById(R.id.turnstileKeyTitle);
        TextView solveTitle = (TextView) findViewById(R.id.turnstileSolveTitle);
        keyCopyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserTab current = getCurrentTab();
                copyToClipboard("turnstile_sitekey", current == null ? "" : current.turnstileSiteKey);
            }
        });
        keyMinimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnstileKeyPanelMinimized = !turnstileKeyPanelMinimized;
                if (turnstileKeyPanelMinimized) {
                    turnstileKeyPanelExpanded = false;
                }
                updateTurnstilePanels();
            }
        });
        keyMaximizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnstileKeyPanelExpanded = !turnstileKeyPanelExpanded;
                if (turnstileKeyPanelExpanded) {
                    turnstileKeyPanelMinimized = false;
                }
                updateTurnstilePanels();
            }
        });
        solveCopyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserTab current = getCurrentTab();
                copyToClipboard("turnstile_token", current == null ? "" : current.turnstileToken);
            }
        });
        solveMinimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnstileSolvePanelMinimized = !turnstileSolvePanelMinimized;
                if (turnstileSolvePanelMinimized) {
                    turnstileSolvePanelExpanded = false;
                }
                updateTurnstilePanels();
            }
        });
        solveMaximizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnstileSolvePanelExpanded = !turnstileSolvePanelExpanded;
                if (turnstileSolvePanelExpanded) {
                    turnstileSolvePanelMinimized = false;
                }
                updateTurnstilePanels();
            }
        });
        attachPanelDragHandle(keyTitle, turnstileKeyPanel);
        attachPanelDragHandle(solveTitle, turnstileSolvePanel);
        setPullRefreshIndicator(false, false, 0f);
        updateTurnstilePanels();
    }

    private void wireControls() {
        newTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTab(HOME_URL, true, true);
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserTab current = getCurrentTab();
                if (current != null && current.webView.canGoBack()) {
                    current.webView.goBack();
                }
            }
        });
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserTab current = getCurrentTab();
                if (current != null && current.webView.canGoForward()) {
                    current.webView.goForward();
                }
            }
        });
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserTab current = getCurrentTab();
                if (current != null) {
                    loadIntoTab(current, HOME_URL);
                }
            }
        });
        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showReloadMenu(view);
            }
        });
        tabsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTabsDialog();
            }
        });
        bookmarksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBookmarksDialog();
            }
        });
        scriptsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScriptsDialog();
            }
        });
        downloadsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDownloadsDialog();
            }
        });
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOverflowMenu(view);
            }
        });
        urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                boolean submit = actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN);
                if (submit) {
                    BrowserTab current = getCurrentTab();
                    if (current != null) {
                        loadIntoTab(current, urlInput.getText().toString());
                    }
                    return true;
                }
                return false;
            }
        });
        clearUrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                urlInput.setText("");
            }
        });
    }

    private void handleLaunchIntent(Intent intent) {
        JSONObject payload = LaunchPayloadParser.parseIntent(intent);
        webDriverLaunchPayload = payload;
        String requestedUrl = extractRequestedUrl(intent, payload);
        boolean automationInit = intent != null
                && intent.getBooleanExtra(EXTRA_WEBDRIVER_MODE, false)
                && isWebDriverInitPayload(payload);
        if (tabs.isEmpty()) {
            createTab(automationInit ? "about:blank" : (TextUtils.isEmpty(requestedUrl) ? HOME_URL : requestedUrl), true, true);
        } else if (!TextUtils.isEmpty(requestedUrl)) {
            createTab(requestedUrl, true, true);
        } else {
            updateChrome();
        }
        if (automationInit) {
            webDriverTab = getCurrentTab();
            webDriverOverrideScript = null;
            startWebDriverSession(payload);
        }
    }

    private boolean isWebDriverInitPayload(JSONObject payload) {
        return payload != null
                && payload.optBoolean("state")
                && "init".equalsIgnoreCase(payload.optString("command"));
    }

    private String extractRequestedUrl(Intent intent, JSONObject payload) {
        Uri dataUri = intent == null ? null : intent.getData();
        if (payload != null || (dataUri != null && "webdriver".equalsIgnoreCase(dataUri.getScheme()))) {
            if (payload == null) {
                return null;
            }
            String requestedUrl = payload.optString("url");
            if (TextUtils.isEmpty(requestedUrl)) {
                requestedUrl = payload.optString("href");
            }
            if (TextUtils.isEmpty(requestedUrl)) {
                requestedUrl = payload.optString("start_url");
            }
            if (TextUtils.isEmpty(requestedUrl)) {
                requestedUrl = payload.optString("target_url");
            }
            if (TextUtils.isEmpty(requestedUrl)) {
                requestedUrl = payload.optString("current_url");
            }
            if (TextUtils.isEmpty(requestedUrl) && "get".equalsIgnoreCase(payload.optString("command"))) {
                requestedUrl = payload.optString("value");
            }
            return TextUtils.isEmpty(requestedUrl) ? null : requestedUrl;
        }
        if (dataUri == null) {
            return null;
        }
        return dataUri.toString();
    }

    private BrowserTab createTab(String initialUrl, boolean activate, boolean loadNow) {
        final BrowserTab tab = new BrowserTab();
        tab.title = "New Tab";
        tab.displayUrl = HOME_URL;
        tab.webView = buildWebView(tab);
        tabs.add(tab);
        renderTabStrip();
        int index = tabs.size() - 1;
        if (activate) {
            selectTab(index);
        }
        if (loadNow) {
            loadIntoTab(tab, TextUtils.isEmpty(initialUrl) ? HOME_URL : initialUrl);
        }
        return tab;
    }

    private WebView buildWebView(final BrowserTab tab) {
        final WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 16) {
            try {
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
            } catch (Throwable ignored) {}
        }
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(normalMobileUserAgent);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, cookiesEnabled);
        }
        webView.addJavascriptInterface(this, "android");
        webView.setWebViewClient(new BrowserClient(tab));
        webView.setWebChromeClient(new BrowserChrome(tab));
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                requestDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
        attachSwipeReloadHandler(webView, tab);
        return webView;
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        currentTabIndex = index;
        BrowserTab tab = tabs.get(index);
        if (tab.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        webContainer.removeAllViews();
        webContainer.addView(tab.webView);
        renderTabStrip();
        updateChrome();
        tabScroll.post(new Runnable() {
            @Override
            public void run() {
                tabScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
            }
        });
    }

    private BrowserTab getCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            return null;
        }
        return tabs.get(currentTabIndex);
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        BrowserTab tab = tabs.remove(index);
        if (tab.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        tab.webView.destroy();
        if (tabs.isEmpty()) {
            currentTabIndex = -1;
            createTab(HOME_URL, true, true);
            return;
        }
        if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
        }
        if (index <= currentTabIndex) {
            currentTabIndex = Math.max(0, currentTabIndex - 1);
        }
        selectTab(currentTabIndex);
    }

    private void renderTabStrip() {
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            final BrowserTab tab = tabs.get(i);

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackgroundResource(i == currentTabIndex ? R.drawable.bg_tab_chip_active : R.drawable.bg_tab_chip);
            chip.setPadding(dp(14), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = dp(8);
            chip.setLayoutParams(chipParams);

            TextView titleView = new TextView(this);
            titleView.setText(tab.titleForStrip());
            titleView.setTextColor(i == currentTabIndex ? Color.WHITE : getResources().getColor(R.color.colorPrimary));
            titleView.setMaxEms(11);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setSingleLine(true);
            titleView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectTab(index);
                }
            });

            TextView closeView = new TextView(this);
            closeView.setText(" x ");
            closeView.setTextColor(i == currentTabIndex ? Color.WHITE : getResources().getColor(R.color.colorPrimary));
            closeView.setPadding(dp(6), 0, 0, 0);
            closeView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    closeTab(index);
                }
            });

            chip.addView(titleView);
            chip.addView(closeView);
            tabStrip.addView(chip);
        }
        tabsButton.setText("Tabs " + tabs.size());
    }

    private void loadIntoTab(BrowserTab tab, String input) {
        if (tab == null) {
            return;
        }
        String normalized = normalizeInput(input);
        if (HOME_URL.equals(normalized)) {
            loadHome(tab);
            return;
        }
        if (normalized.startsWith("dbgid://")) {
            loadInternalPage(tab, normalized);
            return;
        }
        applySiteConfig(tab, normalized);
        tab.isHome = false;
        tab.displayUrl = normalized;
        tab.webView.loadUrl(normalized);
        updateChrome();
    }

    private String normalizeInput(String input) {
        String value = input == null ? "" : input.trim();
        if (TextUtils.isEmpty(value) || HOME_URL.equalsIgnoreCase(value)) {
            return HOME_URL;
        }
        if (value.startsWith("dbgid://")) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("file://") || value.startsWith("about:")) {
            return value;
        }
        if (value.startsWith("javascript:")) {
            return value;
        }
        if (value.contains("://")) {
            return value;
        }
        if (value.contains(" ") || (!value.contains(".") && !value.startsWith("localhost"))) {
            try {
                return "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8");
            } catch (Exception ignored) {
                return "https://www.google.com/search?q=" + value.replace(" ", "+");
            }
        }
        return "https://" + value;
    }

    private void loadHome(BrowserTab tab) {
        tab.isHome = true;
        tab.title = "Start";
        tab.displayUrl = HOME_URL;
        tab.webView.getSettings().setJavaScriptEnabled(true);
        tab.webView.getSettings().setUserAgentString(normalMobileUserAgent);
        String html = buildHomePageHtml();
        tab.webView.loadDataWithBaseURL(HOME_RENDER_URL, html, "text/html", "UTF-8", null);
        updateChrome();
    }

    private void loadInternalPage(BrowserTab tab, String url) {
        Uri uri = Uri.parse(url);
        if (handleInternalAction(tab, uri)) {
            return;
        }

        tab.isHome = true;
        tab.displayUrl = url;
        tab.webView.getSettings().setJavaScriptEnabled(true);
        tab.webView.getSettings().setUserAgentString(normalMobileUserAgent);

        String host = uri.getHost();
        String html;
        if ("devtools".equals(host)) {
            tab.title = "DevTools";
            html = buildDevToolsPageHtml();
        } else if ("network".equals(host)) {
            tab.title = "Network";
            html = buildNetworkPageHtml();
        } else if ("userscripts".equals(host)) {
            tab.title = "User Scripts";
            html = buildUserScriptsPageHtml();
        } else {
            tab.title = "Start";
            html = buildHomePageHtml();
        }
        tab.webView.loadDataWithBaseURL(HOME_RENDER_URL, html, "text/html", "UTF-8", null);
        updateChrome();
    }

    private boolean handleInternalAction(BrowserTab tab, Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        String action = uri.getQueryParameter("action");
        if (TextUtils.isEmpty(action)) {
            return false;
        }

        if ("network".equals(host)) {
            if ("toggle_overlay".equals(action)) {
                networkInspectorEnabled = !networkInspectorEnabled;
                preferences.edit().putBoolean(PREF_NETWORK_OVERLAY, networkInspectorEnabled).apply();
                BrowserTab current = getCurrentTab();
                if (current != null && !current.isHome && current.displayUrl.startsWith("http")) {
                    current.webView.reload();
                }
                loadInternalPage(tab, NETWORK_URL);
                return true;
            }
            if ("open_current".equals(action)) {
                BrowserTab current = getCurrentTab();
                if (current != null && !current.isHome) {
                    injectDevToolsOverlay(current.webView);
                }
                return true;
            }
        }

        if ("userscripts".equals(host)) {
            if ("new".equals(action)) {
                showScriptEditor(null);
                return true;
            }
            if ("manage".equals(action)) {
                showScriptsDialog();
                return true;
            }
        }
        return false;
    }

    private String buildHomePageHtml() {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>");
        builder.append("<style>");
        builder.append("body{margin:0;font-family:sans-serif;background:linear-gradient(145deg,#0f3d3e,#1d5d5f 48%,#f7f3ec 48%,#f7f3ec);color:#1d2628;}");
        builder.append(".wrap{padding:28px 20px 40px;}.hero{color:#fff;max-width:520px;}.eyebrow{letter-spacing:.14em;font-size:12px;text-transform:uppercase;opacity:.8;}");
        builder.append("h1{margin:10px 0 8px;font-size:38px;line-height:1.05;}p{line-height:1.5;}.panel{margin-top:26px;background:#fffdf8;border-radius:24px;padding:18px;box-shadow:0 10px 30px rgba(15,61,62,.12);}");
        builder.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px;margin-top:14px;}");
        builder.append(".card{display:block;text-decoration:none;background:#f1e7d6;color:#0f3d3e;padding:16px;border-radius:18px;font-weight:700;}");
        builder.append(".muted{color:#667275;font-size:13px;}.pill{display:inline-block;padding:8px 12px;background:#f7d6cc;color:#0f3d3e;border-radius:999px;margin:6px 6px 0 0;font-size:12px;font-weight:700;}");
        builder.append("</style></head><body><div class='wrap'>");
        builder.append("<div class='hero'><div class='eyebrow'>DBG Internal Browser Runtime</div><h1>DBG ID Browser</h1>");
        builder.append("<p>Multi-tab browsing with bookmark and download controls, user script injection, IP header selection, and an in-page network/devtools overlay.</p>");
        builder.append("<span class='pill'>dbgid://network</span><span class='pill'>dbgid://userscripts</span><span class='pill'>Network Overlay</span></div>");
        builder.append("<div class='panel'><strong>Quick Launch</strong><div class='grid'>");
        builder.append("<a class='card' href='").append(NETWORK_URL).append("'>Network</a>");
        builder.append("<a class='card' href='").append(DEVTOOLS_URL).append("'>DevTools</a>");
        builder.append("<a class='card' href='").append(USERSCRIPTS_URL).append("'>User Scripts</a>");
        if (bookmarks.isEmpty()) {
            builder.append("<a class='card' href='https://www.google.com'>Google</a>");
            builder.append("<a class='card' href='https://github.com/dbgid'>GitHub</a>");
        } else {
            for (int i = 0; i < bookmarks.size(); i++) {
                BrowserStore.Bookmark bookmark = bookmarks.get(i);
                builder.append("<a class='card' href='").append(html(bookmark.url)).append("'>");
                builder.append(html(TextUtils.isEmpty(bookmark.title) ? bookmark.url : bookmark.title));
                builder.append("</a>");
            }
        }
        builder.append("</div><p class='muted'>Use the top bar to search, open tabs, manage scripts, and adjust browser settings directly on-device.</p></div>");
        builder.append("</div></body></html>");
        return builder.toString();
    }

    private String buildDevToolsPageHtml() {
        StringBuilder builder = new StringBuilder();
        builder.append(buildInternalPageStart("DevTools", "Kiwi-style built-in Chromium DevTools is not available in this AIDE project because Kiwi implements that inside a full Chromium browser engine, not a small Android app wrapper. Use the network page for in-tab inspection and chrome://inspect for remote page debugging."));
        builder.append("<div class='card'><div class='title'>Available here</div><div class='sub'>Use ").append(NETWORK_URL).append(" for request sniffing and copy actions inside the current page.</div></div>");
        builder.append("<div class='card'><div class='title'>Remote inspection</div><div class='sub'>WebView remote debugging remains available through chrome://inspect on a desktop Chromium browser.</div></div>");
        builder.append(buildInternalPageEnd());
        return builder.toString();
    }

    private String buildNetworkPageHtml() {
        StringBuilder builder = new StringBuilder();
        builder.append(buildInternalPageStart("Network", "Separate network sniffing overlay for the current page. The overlay starts enabled by default, can be dragged anywhere, and keeps request headers plus payload copy actions."));
        builder.append("<div class='card'><div class='title'>Overlay status</div><div class='sub'>");
        builder.append(networkInspectorEnabled ? "enabled by default" : "disabled");
        builder.append("</div><div class='actions'>");
        builder.append("<a class='action' href='").append(NETWORK_URL).append("?action=toggle_overlay'>Toggle Overlay</a>");
        builder.append("<a class='action' href='").append(NETWORK_URL).append("?action=open_current'>Inject Current Page</a>");
        builder.append("</div></div>");
        builder.append("<div class='card'><div class='title'>Captured traffic</div><div class='sub'>Fetch and XHR requests are captured in-page with method, URL, headers, and payload body. Each record has a copy button in the overlay itself.</div></div>");
        builder.append("<div class='card'><div class='title'>Route</div><div class='sub'>Type ").append(NETWORK_URL).append(" to re-open this control page at any time.</div></div>");
        builder.append(buildInternalPageEnd());
        return builder.toString();
    }

    private String buildUserScriptsPageHtml() {
        StringBuilder builder = new StringBuilder();
        builder.append(buildInternalPageStart("User Scripts", "Scripts are injected on matching pages. The editor now opens with a working example placeholder."));
        builder.append("<div class='actions'>");
        builder.append("<a class='action' href='").append(USERSCRIPTS_URL).append("?action=new'>New Script</a>");
        builder.append("<a class='action' href='").append(USERSCRIPTS_URL).append("?action=manage'>Manage Scripts</a>");
        builder.append("</div>");
        if (scripts.isEmpty()) {
            builder.append("<div class='card'><div class='title'>No user scripts</div><div class='sub'>Create one and use <all_urls> or a Chrome-style match pattern.</div></div>");
        }
        for (int i = 0; i < scripts.size(); i++) {
            BrowserStore.UserScript script = scripts.get(i);
            builder.append("<div class='card'><div class='title'>").append(html(script.name)).append("</div><div class='sub'>");
            builder.append(html(script.matchPattern)).append(" • ").append(script.enabled ? "enabled" : "disabled").append("</div></div>");
        }
        builder.append(buildInternalPageEnd());
        return builder.toString();
    }

    private String buildInternalPageStart(String title, String subtitle) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>");
        builder.append("<style>");
        builder.append("body{margin:0;padding:20px;background:#f7f3ec;color:#1d2628;font-family:sans-serif;}h1{margin:0 0 8px;font-size:32px;color:#0f3d3e;}p{margin:0 0 16px;line-height:1.5;color:#667275;}");
        builder.append(".card{background:#fffdf8;border:1px solid #d8d1c4;border-radius:20px;padding:16px;margin:0 0 12px;box-shadow:0 8px 22px rgba(15,61,62,.08);}");
        builder.append(".title{font-weight:700;color:#0f3d3e;margin-bottom:6px;}.sub{color:#5e6c70;line-height:1.45;word-break:break-word;}.actions{margin:12px 0;}");
        builder.append(".action{display:inline-block;margin:0 8px 8px 0;padding:10px 14px;border-radius:999px;background:#0f3d3e;color:#fff;text-decoration:none;font-weight:700;}.warn{background:#f26b4b;}");
        builder.append("</style></head><body><h1>").append(html(title)).append("</h1><p>").append(html(subtitle)).append("</p>");
        return builder.toString();
    }

    private String buildInternalPageEnd() {
        return "</body></html>";
    }

    private void injectDevToolsOverlay(WebView webView) {
        String script = readAssetText("dbg_network_overlay.js");
        if (!TextUtils.isEmpty(script)) {
            webView.evaluateJavascript(script, null);
        }
    }

    private String getDefaultUserScriptTemplate() {
        String sample = readAssetText("dbg_sample_user_script.js");
        return TextUtils.isEmpty(sample) ? "(function(){ console.log('DBG user script active'); })();" : sample;
    }

    private String readAssetText(String assetName) {
        InputStream input = null;
        BufferedReader reader = null;
        try {
            input = getAssets().open(assetName);
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return "";
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Throwable ignored) {}
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {}
        }
    }

    private void updateChrome() {
        BrowserTab current = getCurrentTab();
        boolean hasCurrent = current != null;
        backButton.setEnabled(hasCurrent && current.webView.canGoBack());
        forwardButton.setEnabled(hasCurrent && current.webView.canGoForward());
        reloadButton.setEnabled(hasCurrent);
        homeButton.setEnabled(hasCurrent);
        setButtonVisualState(backButton);
        setButtonVisualState(forwardButton);
        setButtonVisualState(homeButton);
        setButtonVisualState(reloadButton);
        setButtonVisualState(tabsButton);
        if (!hasCurrent) {
            urlInput.setText("");
            pageMetaText.setText(getString(R.string.page_meta_placeholder));
            statusText.setText(getString(R.string.browser_subtitle));
            setPullRefreshIndicator(false, false, 0f);
            updateTurnstilePanels();
            return;
        }

        String meta = current.isHome ? "Start page" : safeTitle(current.title, "Untitled") + "  •  " + hostLabel(current.displayUrl);
        if (current.turnstileDetected) {
            String turnstileMeta = "Turnstile " + (TextUtils.isEmpty(current.turnstileStatus) ? "detected" : current.turnstileStatus);
            if (!TextUtils.isEmpty(current.turnstileSiteKey)) {
                turnstileMeta = turnstileMeta + "  •  key " + abbreviateText(current.turnstileSiteKey, 18);
            }
            meta = meta + "  •  " + turnstileMeta;
        }
        pageMetaText.setText(meta);
        String runtimeStatus = tabs.size() + " tabs  •  cookies " + (cookiesEnabled ? "on" : "off") + "  •  doh " + (dohEnabled ? "on" : "off");
        if (current.turnstileDetected) {
            runtimeStatus = "turnstile " + (TextUtils.isEmpty(current.turnstileStatus) ? "detected" : current.turnstileStatus) + "  •  " + runtimeStatus;
        }
        statusText.setText(runtimeStatus);
        if (!TextUtils.equals(urlInput.getText().toString(), current.displayUrl)) {
            urlInput.setText(current.displayUrl);
            urlInput.setSelection(urlInput.getText().length());
        }
        setPullRefreshIndicator(current.pullRefreshVisible, current.pullRefreshing, current.pullRefreshProgress);
        updateTurnstilePanels();
    }

    private void updateTurnstilePanels() {
        BrowserTab current = getCurrentTab();
        if (current == null) {
            setTurnstilePanelState(turnstileKeyPanel, turnstileKeyBody, "", false, false, false, 180);
            setTurnstilePanelState(turnstileSolvePanel, turnstileSolveBody, "", false, false, false, 180);
            return;
        }

        boolean showKey = !TextUtils.isEmpty(current.turnstileSiteKey);
        String keyBody = "sitekey=" + current.turnstileSiteKey;
        if (!TextUtils.isEmpty(current.displayUrl)) {
            keyBody = keyBody + "\nurl=" + current.displayUrl;
        }
        setTurnstilePanelState(turnstileKeyPanel, turnstileKeyBody, keyBody, showKey, turnstileKeyPanelMinimized, turnstileKeyPanelExpanded, 180);

        boolean showSolve = !TextUtils.isEmpty(current.turnstileToken)
                || (!TextUtils.isEmpty(current.turnstileStatus) && current.turnstileDetected);
        String solveBody = "status=" + (TextUtils.isEmpty(current.turnstileStatus) ? "detected" : current.turnstileStatus);
        if (!TextUtils.isEmpty(current.turnstileToken)) {
            solveBody = solveBody + "\ntoken=" + current.turnstileToken;
        }
        if (!TextUtils.isEmpty(current.displayUrl)) {
            solveBody = solveBody + "\nurl=" + current.displayUrl;
        }
        setTurnstilePanelState(turnstileSolvePanel, turnstileSolveBody, solveBody, showSolve, turnstileSolvePanelMinimized, turnstileSolvePanelExpanded, 180);
    }

    private void setTurnstilePanelState(LinearLayout panel, TextView body, String content, boolean visible, boolean minimized, boolean expanded, int normalWidthDp) {
        if (panel == null || body == null) {
            return;
        }
        panel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        body.setText(content);
        body.setVisibility(minimized ? View.GONE : View.VISIBLE);
        body.setMaxLines(expanded ? 10 : 3);
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params != null) {
            params.width = dp(expanded ? 280 : (minimized ? 144 : normalWidthDp));
            panel.setLayoutParams(params);
        }
    }

    private void attachPanelDragHandle(View handle, final View panel) {
        if (handle == null || panel == null) {
            return;
        }
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startX;
            private float startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event == null) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = panel.getX();
                        startY = panel.getY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (!dragging && (Math.abs(deltaX) > dp(6) || Math.abs(deltaY) > dp(6))) {
                            dragging = true;
                        }
                        if (dragging) {
                            View parent = (View) panel.getParent();
                            float maxX = parent == null ? startX + deltaX : Math.max(0f, parent.getWidth() - panel.getWidth());
                            float maxY = parent == null ? startY + deltaY : Math.max(0f, parent.getHeight() - panel.getHeight());
                            panel.setX(Math.max(0f, Math.min(startX + deltaX, maxX)));
                            panel.setY(Math.max(0f, Math.min(startY + deltaY, maxY)));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void setPullRefreshIndicator(boolean visible, boolean refreshing, float progress) {
        if (pullRefreshIndicator == null || pullRefreshText == null) {
            return;
        }
        pullRefreshIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
        pullRefreshIndicator.setAlpha(refreshing ? 1f : Math.max(0.35f, Math.min(1f, progress)));
        String label = "Pull to refresh";
        if (refreshing) {
            label = "Refreshing page...";
        } else if (progress >= 1f) {
            label = "Release to refresh";
        }
        pullRefreshText.setText(label);
    }

    private void triggerPullRefresh(BrowserTab tab) {
        if (tab == null) {
            return;
        }
        tab.pullRefreshVisible = true;
        tab.pullRefreshing = true;
        tab.pullRefreshProgress = 1f;
        setPullRefreshIndicator(true, true, 1f);
        if (tab.isHome) {
            loadIntoTab(tab, HOME_URL);
            return;
        }
        tab.webView.clearCache(true);
        if (extraHeaders.isEmpty()) {
            tab.webView.loadUrl(tab.displayUrl);
        } else {
            tab.webView.loadUrl(tab.displayUrl, new HashMap<String, String>(extraHeaders));
        }
    }

    private String abbreviateText(String value, int maxLength) {
        if (TextUtils.isEmpty(value) || value.length() <= maxLength) {
            return value;
        }
        if (maxLength < 8) {
            return value.substring(0, maxLength);
        }
        int prefixLength = maxLength - 4;
        return value.substring(0, prefixLength) + "...";
    }

    private void resetTurnstileState(BrowserTab tab) {
        if (tab == null) {
            return;
        }
        tab.turnstileDetected = false;
        tab.turnstileStatus = "";
        tab.turnstileSiteKey = "";
        tab.turnstileToken = "";
    }

    private void installTurnstileMonitor(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(isWasSolve.installMonitorJavascript(), null);
    }

    private void detectTurnstileState(final BrowserTab tab, final WebView webView, final String url) {
        detectTurnstileState(tab, webView, url, 0);
    }

    private void detectTurnstileState(final BrowserTab tab, final WebView webView, final String url, final int attempt) {
        if (tab == null || webView == null || TextUtils.isEmpty(url) || HOME_RENDER_URL.equals(url) || HOME_URL.equals(url) || url.startsWith("dbgid://")) {
            return;
        }
        final boolean automationTab = isAutomationTab(tab);
        if (automationTab) {
            installTurnstileMonitor(webView);
        }
        webView.evaluateJavascript(IsTurnstilePage.detectionJavascript(), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String pageValue) {
                final IsTurnstilePage.Result pageResult = IsTurnstilePage.parse(pageValue);
                webView.evaluateJavascript(GetTurnstileSiteKey.detectionJavascript(), new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String siteKeyValue) {
                        final String siteKey = GetTurnstileSiteKey.parse(siteKeyValue);
                        webView.evaluateJavascript(isWasSolve.detectionJavascript(), new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String solveValue) {
                                if (webView.getUrl() != null && !TextUtils.equals(webView.getUrl(), url)) {
                                    return;
                                }
                                isWasSolve.Result solveResult = isWasSolve.parse(solveValue);
                                String normalizedToken = isWasSolve.normalizeTokenForCopy(solveResult);
                                boolean detected = pageResult.isDetected()
                                        || solveResult.hasTurnstileSignal()
                                        || !TextUtils.isEmpty(siteKey)
                                        || !TextUtils.isEmpty(normalizedToken)
                                        || IsTurnstilePage.isLikelyByUrl(url);
                                tab.turnstileDetected = detected;
                                if (!TextUtils.isEmpty(siteKey)) {
                                    tab.turnstileSiteKey = siteKey;
                                }
                                if (!TextUtils.isEmpty(normalizedToken)) {
                                    tab.turnstileToken = normalizedToken;
                                }
                                if (solveResult.solved || !TextUtils.isEmpty(tab.turnstileToken)) {
                                    tab.turnstileStatus = "solved";
                                    if (automationTab) {
                                        webView.evaluateJavascript(isWasSolve.continueAfterSolveJavascript(), null);
                                    }
                                } else if (pageResult.cloudflareChallenge || solveResult.challengePage) {
                                    tab.turnstileStatus = "challenge";
                                } else if (detected) {
                                    tab.turnstileStatus = "detected";
                                } else {
                                    tab.turnstileStatus = "";
                                }
                                if (tab == getCurrentTab()) {
                                    updateChrome();
                                }
                                boolean needsFollowUp = detected
                                        || pageResult.cloudflareChallenge
                                        || solveResult.challengePage
                                        || IsTurnstilePage.isLikelyByUrl(url);
                                if (attempt < 12
                                        && needsFollowUp
                                        && (TextUtils.isEmpty(tab.turnstileSiteKey) || TextUtils.isEmpty(tab.turnstileToken) || !"solved".equals(tab.turnstileStatus))
                                        && TextUtils.equals(webView.getUrl(), url)) {
                                    webView.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            detectTurnstileState(tab, webView, url, attempt + 1);
                                        }
                                    }, attempt < 4 ? 450L : 900L);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void setButtonVisualState(Button button) {
        if (button == null) {
            return;
        }
        button.setAlpha(button.isEnabled() ? 1.0f : 0.45f);
    }

    private String hostLabel(String url) {
        String host = MatchUtils.hostFromUrl(url);
        return TextUtils.isEmpty(host) ? url : host;
    }

    private void applySiteConfig(BrowserTab tab, String url) {
        WebSettings settings = tab.webView.getSettings();
        BrowserStore.SiteConfig config = getSiteConfig(url);
        settings.setJavaScriptEnabled(config.javascriptEnabled);
        String configuredUserAgent = config.desktopMode ? desktopUserAgent : normalMobileUserAgent;
        String overrideUserAgent = extraHeaders.get("User-Agent");
        if (tab == webDriverTab && !TextUtils.isEmpty(overrideUserAgent)) {
            configuredUserAgent = overrideUserAgent;
        }
        settings.setUserAgentString(configuredUserAgent);
        tab.desktopMode = config.desktopMode;
        cookieManager.setAcceptCookie(cookiesEnabled);
        if (Build.VERSION.SDK_INT >= 21) {
            cookieManager.setAcceptThirdPartyCookies(tab.webView, cookiesEnabled);
        }
    }

    private BrowserStore.SiteConfig getSiteConfig(String url) {
        String host = MatchUtils.hostFromUrl(url);
        BrowserStore.SiteConfig config = siteConfigs.get(host);
        if (config == null) {
            config = new BrowserStore.SiteConfig();
            config.host = host;
        }
        return config;
    }

    private void saveSiteConfig(BrowserStore.SiteConfig config) {
        if (config == null || TextUtils.isEmpty(config.host)) {
            return;
        }
        siteConfigs.put(config.host, config);
        store.saveSiteConfigs(siteConfigs);
    }

    private void showReloadMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Reload");
        menu.getMenu().add(0, 2, 1, "Force Reload");
        menu.getMenu().add(0, 3, 2, "Stop");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                BrowserTab current = getCurrentTab();
                if (current == null) {
                    return true;
                }
                if (item.getItemId() == 1) {
                    current.webView.reload();
                } else if (item.getItemId() == 2) {
                    current.webView.clearCache(true);
                    current.webView.reload();
                } else if (item.getItemId() == 3) {
                    current.webView.stopLoading();
                }
                return true;
            }
        });
        menu.show();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 10, 0, "Bookmark Current Page");
        menu.getMenu().add(0, 11, 1, "Site Settings");
        menu.getMenu().add(0, 12, 2, "Settings");
        menu.getMenu().add(0, 13, 3, "Developer Tools");
        menu.getMenu().add(0, 14, 4, "Copy URL");
        menu.getMenu().add(0, 15, 5, "Close Tab");
        menu.getMenu().add(0, 16, 6, "Open Start Page");
        menu.getMenu().add(0, 17, 7, "Zoom In");
        menu.getMenu().add(0, 18, 8, "Zoom Out");
        menu.getMenu().add(0, 19, 9, "Open in External Browser");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 10:
                        addCurrentPageToBookmarks();
                        return true;
                    case 11:
                        showSiteSettingsDialog();
                        return true;
                    case 12:
                        showAppSettingsDialog();
                        return true;
                    case 13:
                        showDevToolsDialog();
                        return true;
                    case 14:
                        copyToClipboard("url", getCurrentTab() == null ? "" : getCurrentTab().displayUrl);
                        return true;
                    case 15:
                        closeTab(currentTabIndex);
                        return true;
                    case 16:
                        BrowserTab current = getCurrentTab();
                        if (current != null) {
                            loadIntoTab(current, HOME_URL);
                        }
                        return true;
                    case 17:
                        if (getCurrentTab() != null) {
                            getCurrentTab().webView.zoomIn();
                        }
                        return true;
                    case 18:
                        if (getCurrentTab() != null) {
                            getCurrentTab().webView.zoomOut();
                        }
                        return true;
                    case 19:
                        BrowserTab active = getCurrentTab();
                        if (active != null) {
                            openInExternalBrowser(active.displayUrl);
                        }
                        return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void openInExternalBrowser(String url) {
        if (TextUtils.isEmpty(url) || HOME_URL.equals(url) || url.startsWith("dbgid://")) {
            toast("Open a website first.");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
            toast("No external browser found.");
        }
    }

    private void addCurrentPageToBookmarks() {
        BrowserTab current = getCurrentTab();
        if (current == null || current.isHome || TextUtils.isEmpty(current.displayUrl)) {
            toast("Open a site before adding a bookmark.");
            return;
        }
        for (int i = 0; i < bookmarks.size(); i++) {
            if (TextUtils.equals(bookmarks.get(i).url, current.displayUrl)) {
                toast("Bookmark already saved.");
                return;
            }
        }
        BrowserStore.Bookmark bookmark = new BrowserStore.Bookmark();
        bookmark.title = current.title;
        bookmark.url = current.displayUrl;
        bookmarks.add(0, bookmark);
        store.saveBookmarks(bookmarks);
        if (current.isHome) {
            loadHome(current);
        }
        toast("Bookmark added.");
    }

    private void showTabsDialog() {
        DialogFrame frame = createDialogFrame("Tabs");
        final AlertDialog dialog = frame.dialog;
        LinearLayout content = frame.content;

        Button newButton = createDialogButton("New Tab");
        newButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                createTab(HOME_URL, true, true);
            }
        });
        content.addView(newButton);

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            final BrowserTab tab = tabs.get(i);
            LinearLayout card = createCard();
            addCardText(card, tab.titleForStrip(), tab.displayUrl);

            LinearLayout actions = createHorizontalActions();
            Button openButton = createDialogButton(i == currentTabIndex ? "Active" : "Open");
            openButton.setEnabled(i != currentTabIndex);
            openButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    selectTab(index);
                }
            });
            Button closeButton = createDialogButton("Close");
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    closeTab(index);
                }
            });
            actions.addView(openButton);
            actions.addView(closeButton);
            card.addView(actions);
            content.addView(card);
        }
        dialog.show();
    }

    private void showBookmarksDialog() {
        DialogFrame frame = createDialogFrame("Bookmarks");
        final AlertDialog dialog = frame.dialog;
        LinearLayout content = frame.content;

        Button saveCurrentButton = createDialogButton("Save Current Page");
        saveCurrentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addCurrentPageToBookmarks();
                dialog.dismiss();
            }
        });
        content.addView(saveCurrentButton);

        if (bookmarks.isEmpty()) {
            content.addView(createMutedText("No bookmarks saved yet."));
        }

        for (int i = 0; i < bookmarks.size(); i++) {
            final BrowserStore.Bookmark bookmark = bookmarks.get(i);
            final int index = i;
            LinearLayout card = createCard();
            addCardText(card, safeTitle(bookmark.title, bookmark.url), bookmark.url);

            LinearLayout actions = createHorizontalActions();
            Button openButton = createDialogButton("Open");
            openButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    BrowserTab current = getCurrentTab();
                    if (current != null) {
                        loadIntoTab(current, bookmark.url);
                    }
                }
            });
            Button newTab = createDialogButton("New Tab");
            newTab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    createTab(bookmark.url, true, true);
                }
            });
            Button deleteButton = createDialogButton("Delete");
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    bookmarks.remove(index);
                    store.saveBookmarks(bookmarks);
                    dialog.dismiss();
                    BrowserTab current = getCurrentTab();
                    if (current != null && current.isHome) {
                        loadHome(current);
                    }
                    showBookmarksDialog();
                }
            });
            actions.addView(openButton);
            actions.addView(newTab);
            actions.addView(deleteButton);
            card.addView(actions);
            content.addView(card);
        }
        dialog.show();
    }

    private void showScriptsDialog() {
        DialogFrame frame = createDialogFrame("User Scripts");
        final AlertDialog dialog = frame.dialog;
        LinearLayout content = frame.content;

        Button addButton = createDialogButton("New Script");
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showScriptEditor(null);
            }
        });
        content.addView(addButton);
        content.addView(createMutedText("Patterns accept Chrome-style entries like *://*.example.com/* or <all_urls>."));

        if (scripts.isEmpty()) {
            content.addView(createMutedText("No user scripts configured."));
        }

        for (int i = 0; i < scripts.size(); i++) {
            final BrowserStore.UserScript script = scripts.get(i);
            final int index = i;
            LinearLayout card = createCard();
            addCardText(card, script.name, script.matchPattern + (script.enabled ? "  •  enabled" : "  •  disabled"));

            LinearLayout actions = createHorizontalActions();
            Button toggle = createDialogButton(script.enabled ? "Disable" : "Enable");
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    script.enabled = !script.enabled;
                    store.saveScripts(scripts);
                    dialog.dismiss();
                    showScriptsDialog();
                }
            });
            Button edit = createDialogButton("Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    showScriptEditor(script);
                }
            });
            Button delete = createDialogButton("Delete");
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    scripts.remove(index);
                    store.saveScripts(scripts);
                    dialog.dismiss();
                    showScriptsDialog();
                }
            });
            actions.addView(toggle);
            actions.addView(edit);
            actions.addView(delete);
            card.addView(actions);
            content.addView(card);
        }
        dialog.show();
    }

    private void showScriptEditor(final BrowserStore.UserScript existing) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existing == null ? "New Script" : "Edit Script");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        layout.setPadding(padding, padding, padding, padding);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        nameInput.setText(existing == null ? "" : existing.name);
        layout.addView(nameInput);

        final EditText matchInput = new EditText(this);
        matchInput.setHint("<all_urls>");
        matchInput.setText(existing == null ? "<all_urls>" : existing.matchPattern);
        layout.addView(matchInput);

        final EditText scriptInput = new EditText(this);
        scriptInput.setHint("JavaScript code");
        scriptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        scriptInput.setMinLines(10);
        scriptInput.setGravity(Gravity.TOP | Gravity.START);
        scriptInput.setText(existing == null ? getDefaultUserScriptTemplate() : existing.script);
        layout.addView(scriptInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final CheckBox enabledBox = new CheckBox(this);
        enabledBox.setText("Enabled");
        enabledBox.setChecked(existing == null || existing.enabled);
        layout.addView(enabledBox);

        builder.setView(layout);
        builder.setPositiveButton(existing == null ? "Save" : "Update", null);
        builder.setNegativeButton("Cancel", null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = nameInput.getText().toString().trim();
                String pattern = matchInput.getText().toString().trim();
                String code = scriptInput.getText().toString();
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(pattern) || TextUtils.isEmpty(code.trim())) {
                    toast("Name, pattern, and script are required.");
                    return;
                }
                BrowserStore.UserScript target = existing == null ? new BrowserStore.UserScript() : existing;
                target.name = name;
                target.matchPattern = pattern;
                target.script = code;
                target.enabled = enabledBox.isChecked();
                if (existing == null) {
                    scripts.add(0, target);
                }
                store.saveScripts(scripts);
                dialog.dismiss();
                toast("Script saved.");
            }
        });
    }

    private void showDownloadsDialog() {
        DialogFrame frame = createDialogFrame("Downloads");
        final AlertDialog dialog = frame.dialog;
        LinearLayout content = frame.content;

        Button openSystem = createDialogButton("Open System Downloads");
        openSystem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                } catch (Throwable e) {
                    toast("No downloads app available.");
                }
            }
        });
        content.addView(openSystem);

        if (downloads.isEmpty()) {
            content.addView(createMutedText("Downloads started from the browser will appear here."));
        }

        for (int i = 0; i < downloads.size(); i++) {
            BrowserStore.DownloadRecord record = downloads.get(i);
            LinearLayout card = createCard();
            String subtitle = DateFormat.format("yyyy-MM-dd HH:mm", record.createdAt).toString() + "  •  " + record.url;
            addCardText(card, record.fileName, subtitle);
            content.addView(card);
        }
        dialog.show();
    }

    private void showSiteSettingsDialog() {
        final BrowserTab current = getCurrentTab();
        if (current == null || current.isHome) {
            toast("Open a site before changing site settings.");
            return;
        }
        final BrowserStore.SiteConfig config = getSiteConfig(current.displayUrl);
        final String host = MatchUtils.hostFromUrl(current.displayUrl);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Site Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        layout.setPadding(padding, padding, padding, padding);

        TextView hostView = new TextView(this);
        hostView.setText(host);
        hostView.setTextSize(16f);
        hostView.setTextColor(getResources().getColor(R.color.colorPrimary));
        layout.addView(hostView);

        TextView noteView = createMutedText("JavaScript and desktop mode are stored per host. Cookie control is global because Android WebView does not expose a reliable per-site cookie toggle.");
        layout.addView(noteView);

        final CheckBox jsBox = new CheckBox(this);
        jsBox.setText("Enable JavaScript for this site");
        jsBox.setChecked(config.javascriptEnabled);
        layout.addView(jsBox);

        final CheckBox desktopBox = new CheckBox(this);
        desktopBox.setText("Request desktop mode for this site");
        desktopBox.setChecked(config.desktopMode);
        layout.addView(desktopBox);

        final CheckBox cookiesBox = new CheckBox(this);
        cookiesBox.setText("Accept cookies globally");
        cookiesBox.setChecked(cookiesEnabled);
        layout.addView(cookiesBox);

        builder.setView(layout);
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Clear Cookies", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                clearAllCookies();
            }
        });
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                config.host = host;
                config.javascriptEnabled = jsBox.isChecked();
                config.desktopMode = desktopBox.isChecked();
                saveSiteConfig(config);
                cookiesEnabled = cookiesBox.isChecked();
                preferences.edit().putBoolean(PREF_COOKIES_ENABLED, cookiesEnabled).apply();
                applyCookieSettingToAllTabs();
                applySiteConfig(current, current.displayUrl);
                current.webView.reload();
                updateChrome();
            }
        });
        builder.show();
    }

    private void showAppSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        layout.setPadding(padding, padding, padding, padding);

        layout.addView(createMutedText("Global app settings. DNS over HTTPS here controls app-level DNS lookup tools. Android WebView page loads still use the system or Private DNS stack."));

        final CheckBox overlayBox = new CheckBox(this);
        overlayBox.setText("Enable network overlay by default");
        overlayBox.setChecked(networkInspectorEnabled);
        layout.addView(overlayBox);

        final CheckBox dohBox = new CheckBox(this);
        dohBox.setText("Enable DNS over HTTPS query tools");
        dohBox.setChecked(dohEnabled);
        layout.addView(dohBox);

        TextView providerView = createMutedText("DNS provider: " + dohProvider + "  •  " + dohEndpoint);
        layout.addView(providerView);

        final TextView ipView = createMutedText("Selected IP header: " + (TextUtils.isEmpty(selectedIpDisplay) ? "none" : selectedIpDisplay));
        layout.addView(ipView);

        Button setIpButton = createDialogButton("Set IP");
        setIpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSetIpDialog(new Runnable() {
                    @Override
                    public void run() {
                        ipView.setText("Selected IP header: " + (TextUtils.isEmpty(selectedIpDisplay) ? "none" : selectedIpDisplay));
                    }
                });
            }
        });
        layout.addView(setIpButton);

        builder.setView(layout);
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("DNS Settings", null);
        builder.setPositiveButton("Save", null);

        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDnsSettingsDialog();
            }
        });
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                networkInspectorEnabled = overlayBox.isChecked();
                dohEnabled = dohBox.isChecked();
                preferences.edit()
                    .putBoolean(PREF_NETWORK_OVERLAY, networkInspectorEnabled)
                    .putBoolean(PREF_DOH_ENABLED, dohEnabled)
                    .apply();
                updateChrome();
                dialog.dismiss();
                toast("Settings saved.");
            }
        });
    }

    private void showSetIpDialog(final Runnable onApplied) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set IP");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);

        final TextView infoView = createMutedText("Generate random public IP headers from the ASN dataset. Format: <ip>|<country>|<asn-name>");
        layout.addView(infoView);

        final TextView realIpView = createMutedText("Real IP: tap Check Real IP");
        layout.addView(realIpView);

        final TextView selectedView = createMutedText("Selected: " + (TextUtils.isEmpty(selectedIpDisplay) ? "none" : selectedIpDisplay));
        layout.addView(selectedView);

        LinearLayout actions = createHorizontalActions();
        Button checkButton = createDialogButton("Check Real IP");
        Button regenerateButton = createDialogButton("Generate 10 IPs");
        Button clearButton = createDialogButton("Clear");
        actions.addView(checkButton);
        actions.addView(regenerateButton);
        actions.addView(clearButton);
        layout.addView(actions);

        final LinearLayout optionsLayout = new LinearLayout(this);
        optionsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(optionsLayout);

        final SetIps.Entry[] selectedHolder = new SetIps.Entry[] { selectedIpEntry };

        final Runnable renderOptions = new Runnable() {
            @Override
            public void run() {
                optionsLayout.removeAllViews();
                optionsLayout.addView(createMutedText("Generating IP list..."));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<SetIps.Entry> entries = SetIps.generate(MainActivity.this, 10);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    optionsLayout.removeAllViews();
                                    for (int i = 0; i < entries.size(); i++) {
                                        final SetIps.Entry entry = entries.get(i);
                                        Button option = createDialogButton(entry.displayValue());
                                        option.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                selectedHolder[0] = entry;
                                                selectedView.setText("Selected: " + entry.displayValue());
                                            }
                                        });
                                        optionsLayout.addView(option);
                                    }
                                }
                            });
                        } catch (final IOException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    optionsLayout.removeAllViews();
                                    optionsLayout.addView(createMutedText("Failed to load IP dataset: " + e.getMessage()));
                                }
                            });
                        }
                    }
                }, "dbgid-generate-ips").start();
            }
        };

        renderOptions.run();

        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                realIpView.setText("Real IP: checking...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String result = fetchRealPublicIp();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                realIpView.setText("Real IP: " + result);
                                view.setEnabled(true);
                            }
                        });
                    }
                }, "dbgid-ipify-check").start();
            }
        });
        regenerateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderOptions.run();
            }
        });
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedHolder[0] = null;
                selectedView.setText("Selected: none");
            }
        });

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Apply", null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applySelectedIp(selectedHolder[0]);
                if (onApplied != null) {
                    onApplied.run();
                }
                dialog.dismiss();
            }
        });
    }

    private void applySelectedIp(SetIps.Entry entry) {
        selectedIpEntry = entry;
        selectedIpDisplay = entry == null ? "" : entry.displayValue();
        preferences.edit().putString(PREF_SELECTED_IP, selectedIpDisplay).apply();
        applyDefaultHeaders(normalMobileUserAgent);
        BrowserTab current = getCurrentTab();
        if (current != null && !current.isHome && !TextUtils.isEmpty(current.displayUrl) && !isAutomationTab(current)) {
            current.webView.reload();
        }
        updateChrome();
        toast(TextUtils.isEmpty(selectedIpDisplay) ? "IP header cleared." : "IP header applied.");
    }

    private String fetchRealPublicIp() {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", normalMobileUserAgent);
            connection.connect();
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            return TextUtils.isEmpty(line) ? "unavailable" : line.trim();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showDnsSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("DNS Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        layout.setPadding(padding, padding, padding, padding);

        layout.addView(createMutedText("Provider presets follow public JSON DoH endpoints from Cloudflare and Google. Custom endpoints should support Google-style JSON DoH responses."));

        final TextView providerLabel = new TextView(this);
        providerLabel.setText("Provider: " + dohProvider);
        providerLabel.setTextColor(getResources().getColor(R.color.colorPrimary));
        providerLabel.setTextSize(16f);
        layout.addView(providerLabel);

        final EditText endpointInput = new EditText(this);
        endpointInput.setHint("https://resolver.example/dns-query");
        endpointInput.setText(dohEndpoint);
        layout.addView(endpointInput);

        final EditText hostInput = new EditText(this);
        hostInput.setHint("example.com");
        BrowserTab current = getCurrentTab();
        String defaultHost = current == null ? "" : MatchUtils.hostFromUrl(current.displayUrl);
        hostInput.setText(TextUtils.isEmpty(defaultHost) ? "example.com" : defaultHost);
        layout.addView(hostInput);

        final CheckBox enabledBox = new CheckBox(this);
        enabledBox.setText("Enable DNS over HTTPS query");
        enabledBox.setChecked(dohEnabled);
        layout.addView(enabledBox);

        Button testButton = createDialogButton("Test Query");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String endpoint = endpointInput.getText().toString().trim();
                String host = hostInput.getText().toString().trim();
                if (TextUtils.isEmpty(endpoint)) {
                    toast("Enter a DoH endpoint.");
                    return;
                }
                if (TextUtils.isEmpty(host)) {
                    toast("Enter a host.");
                    return;
                }
                runDnsLookup(endpoint, host);
            }
        });
        layout.addView(testButton);

        builder.setView(layout);
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Provider", null);
        builder.setPositiveButton("Save", null);

        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDnsProviderChooser(providerLabel, endpointInput);
            }
        });
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String endpoint = endpointInput.getText().toString().trim();
                if (TextUtils.isEmpty(endpoint) && !DoHResolver.PROVIDER_CUSTOM.equals(dohProvider)) {
                    endpoint = DoHResolver.defaultEndpointForProvider(dohProvider);
                    endpointInput.setText(endpoint);
                }
                if (TextUtils.isEmpty(endpoint)) {
                    toast("Enter a DoH endpoint.");
                    return;
                }
                dohEnabled = enabledBox.isChecked();
                dohEndpoint = endpoint;
                preferences.edit()
                    .putBoolean(PREF_DOH_ENABLED, dohEnabled)
                    .putString(PREF_DOH_PROVIDER, dohProvider)
                    .putString(PREF_DOH_ENDPOINT, dohEndpoint)
                    .apply();
                dialog.dismiss();
                toast("DNS settings saved.");
            }
        });
    }

    private void showDnsProviderChooser(final TextView providerLabel, final EditText endpointInput) {
        final String[] providers = new String[] {
            DoHResolver.PROVIDER_CLOUDFLARE,
            DoHResolver.PROVIDER_GOOGLE,
            DoHResolver.PROVIDER_CUSTOM
        };
        int selected = 0;
        for (int i = 0; i < providers.length; i++) {
            if (TextUtils.equals(providers[i], dohProvider)) {
                selected = i;
                break;
            }
        }
        AlertDialog.Builder chooser = new AlertDialog.Builder(this);
        chooser.setTitle("Select DNS Provider");
        chooser.setSingleChoiceItems(providers, selected, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                dohProvider = providers[which];
                providerLabel.setText("Provider: " + dohProvider);
                if (!DoHResolver.PROVIDER_CUSTOM.equals(dohProvider)) {
                    endpointInput.setText(DoHResolver.defaultEndpointForProvider(dohProvider));
                }
                dialogInterface.dismiss();
            }
        });
        chooser.setNegativeButton("Cancel", null);
        chooser.show();
    }

    private void runDnsLookup(final String endpoint, final String host) {
        toast("Resolving " + host + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final DoHResolver.LookupResult result = DoHResolver.resolve(endpoint, host);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showDnsLookupResult(host, endpoint, result);
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast("DNS query failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showDnsLookupResult(String host, String endpoint, DoHResolver.LookupResult result) {
        DialogFrame frame = createDialogFrame("DoH Result");
        AlertDialog dialog = frame.dialog;
        LinearLayout content = frame.content;

        content.addView(createMutedText("Host: " + host));
        content.addView(createMutedText("Endpoint: " + endpoint));
        content.addView(createMutedText("HTTP " + result.httpCode + "  •  DNS Status " + result.status));

        if (result.answers.isEmpty()) {
            content.addView(createMutedText("No answer records returned."));
        } else {
            for (int i = 0; i < result.answers.size(); i++) {
                DoHResolver.AnswerRecord record = result.answers.get(i);
                LinearLayout card = createCard();
                addCardText(card, safeTitle(record.data, "(empty)"), "type " + record.type + "  •  ttl " + record.ttl + "  •  " + record.name);
                content.addView(card);
            }
        }

        Button copyRaw = createDialogButton("Copy Raw JSON");
        final String rawJson = result.rawJson == null ? "" : result.rawJson;
        copyRaw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipboard("doh", rawJson);
            }
        });
        content.addView(copyRaw);
        dialog.show();
    }

    private void showDevToolsDialog() {
        BrowserTab current = getCurrentTab();
        if (current != null) {
            loadIntoTab(current, DEVTOOLS_URL);
        }
    }

    private void requestDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        PendingDownload download = new PendingDownload();
        download.url = url;
        download.userAgent = userAgent;
        download.contentDisposition = contentDisposition;
        download.mimeType = mimetype;
        enqueueDownload(download);
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) {
                toast("Download service unavailable.");
                return;
            }
            String fileName = URLUtil.guessFileName(download.url, download.contentDisposition, download.mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url));
            request.setTitle(fileName);
            request.setDescription(download.url);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.allowScanningByMediaScanner();
            if (!TextUtils.isEmpty(download.mimeType)) {
                request.setMimeType(download.mimeType);
            }
            if (!TextUtils.isEmpty(download.userAgent)) {
                request.addRequestHeader("User-Agent", download.userAgent);
            }
            String cookieHeader = cookieManager.getCookie(download.url);
            if (!TextUtils.isEmpty(cookieHeader)) {
                request.addRequestHeader("Cookie", cookieHeader);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            } else {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
            }
            manager.enqueue(request);

            BrowserStore.DownloadRecord record = new BrowserStore.DownloadRecord();
            record.fileName = fileName;
            record.url = download.url;
            record.createdAt = System.currentTimeMillis();
            downloads.add(0, record);
            store.saveDownloads(downloads);
            toast("Download started: " + fileName);
        } catch (Throwable e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private void applyCookieSettingToAllTabs() {
        cookieManager.setAcceptCookie(cookiesEnabled);
        if (Build.VERSION.SDK_INT >= 21) {
            for (int i = 0; i < tabs.size(); i++) {
                cookieManager.setAcceptThirdPartyCookies(tabs.get(i).webView, cookiesEnabled);
            }
            cookieManager.flush();
        }
    }

    private void clearAllCookies() {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
            } catch (Throwable ignored) {}
        } else {
            cookieManager.removeAllCookie();
        }
        toast("Cookies cleared.");
    }

    private void injectScripts(BrowserTab tab, String url) {
        if (tab == null || tab.isHome || TextUtils.isEmpty(url)) {
            return;
        }
        if (networkInspectorEnabled) {
            injectDevToolsOverlay(tab.webView);
        }
        for (int i = 0; i < scripts.size(); i++) {
            BrowserStore.UserScript script = scripts.get(i);
            if (script.enabled && MatchUtils.matchesUrl(script.matchPattern, url)) {
                evaluateScript(tab.webView, script.script);
            }
        }
    }

    private void evaluateScript(WebView webView, String source) {
        if (TextUtils.isEmpty(source)) {
            return;
        }
        String wrapped = "(function(){try{" + source + "}catch(e){console.log('DBG ID Browser script error', e);}})();";
        webView.evaluateJavascript(wrapped, null);
    }

    private void attachSwipeReloadHandler(final WebView webView, final BrowserTab tab) {
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event == null) {
                    return false;
                }
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    tab.swipeStartX = event.getX();
                    tab.swipeStartY = event.getY();
                    tab.swipeReloadArmed = webView.getScrollY() <= 0;
                    tab.pullRefreshVisible = tab.swipeReloadArmed;
                    tab.pullRefreshProgress = 0f;
                    if (tab == getCurrentTab()) {
                        setPullRefreshIndicator(tab.pullRefreshVisible, false, 0f);
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getX() - tab.swipeStartX) > dp(90)) {
                        tab.swipeReloadArmed = false;
                    }
                    if (event.getY() < tab.swipeStartY) {
                        tab.swipeReloadArmed = false;
                    }
                    if (tab.swipeReloadArmed && webView.getScrollY() <= dp(12)) {
                        float deltaY = Math.max(0f, event.getY() - tab.swipeStartY);
                        tab.pullRefreshVisible = true;
                        tab.pullRefreshProgress = Math.min(1f, deltaY / (float) dp(120));
                        if (tab == getCurrentTab()) {
                            setPullRefreshIndicator(true, false, tab.pullRefreshProgress);
                        }
                    } else {
                        tab.pullRefreshVisible = false;
                        tab.pullRefreshProgress = 0f;
                        if (tab == getCurrentTab() && !tab.pullRefreshing) {
                            setPullRefreshIndicator(false, false, 0f);
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    float deltaY = event.getY() - tab.swipeStartY;
                    float deltaX = Math.abs(event.getX() - tab.swipeStartX);
                    long now = System.currentTimeMillis();
                    if (action == MotionEvent.ACTION_UP
                        && tab.swipeReloadArmed
                        && deltaY > dp(110)
                        && deltaX < dp(90)
                        && webView.getScrollY() <= dp(12)
                        && now - tab.lastSwipeReloadAt > 1200L) {
                        tab.lastSwipeReloadAt = now;
                        triggerPullRefresh(tab);
                        toast("Refreshing page.");
                    } else {
                        tab.pullRefreshVisible = tab.pullRefreshing;
                        tab.pullRefreshProgress = 0f;
                        if (tab == getCurrentTab() && !tab.pullRefreshing) {
                            setPullRefreshIndicator(false, false, 0f);
                        }
                    }
                    tab.swipeReloadArmed = false;
                }
                return false;
            }
        });
    }

    private DialogFrame createDialogFrame(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);
        builder.setView(scrollView);
        builder.setNegativeButton("Close", null);
        DialogFrame frame = new DialogFrame();
        frame.dialog = builder.create();
        frame.content = content;
        return frame;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(R.drawable.bg_surface);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private void addCardText(LinearLayout card, String title, String subtitle) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getResources().getColor(R.color.colorPrimary));
        titleView.setTextSize(16f);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(titleView);

        TextView subtitleView = createMutedText(subtitle);
        subtitleView.setMaxLines(3);
        card.addView(subtitleView);
    }

    private LinearLayout createHorizontalActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        return actions;
    }

    private Button createDialogButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        try {
            button.setAllCaps(false);
        } catch (Throwable ignored) {}
        button.setBackgroundResource(R.drawable.bg_secondary_button);
        button.setTextColor(getResources().getColor(R.color.colorPrimary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private TextView createMutedText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getResources().getColor(R.color.colorMuted));
        view.setTextSize(13f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        view.setLayoutParams(params);
        return view;
    }

    private String safeTitle(String title, String fallback) {
        return TextUtils.isEmpty(title) ? fallback : title;
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(label, value));
            toast("Copied to clipboard.");
        }
    }

    private String html(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void applyModernWebGlOverride(WebView view) {
        if (view == null) {
            return;
        }
        view.evaluateJavascript(webGlOverrideScript, null);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private class BrowserClient extends WebViewClient {

        private final BrowserTab tab;

        BrowserClient(BrowserTab tab) {
            this.tab = tab;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrlOverride(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            applyModernWebGlOverride(view);
            resetTurnstileState(tab);
            if (isAutomationTab(tab)) {
                installTurnstileMonitor(view);
            }
            if (tab == webDriverTab) {
                webDriverLoaded = 0;
                webDriverLoadedObject = new JSONObject();
            }
            if (HOME_RENDER_URL.equals(url) || HOME_URL.equals(url)) {
                tab.isHome = true;
                tab.displayUrl = HOME_URL;
            } else {
                tab.isHome = false;
                tab.displayUrl = url;
                applySiteConfig(tab, url);
            }
            if (tab.pullRefreshing) {
                tab.pullRefreshVisible = true;
                tab.pullRefreshProgress = 1f;
            }
            if (tab == getCurrentTab()) {
                pageProgress.setVisibility(View.VISIBLE);
                pageProgress.setProgress(15);
                if (tab.pullRefreshing) {
                    setPullRefreshIndicator(true, true, 1f);
                }
                updateChrome();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            applyModernWebGlOverride(view);
            if (HOME_RENDER_URL.equals(url)) {
                tab.isHome = true;
                tab.displayUrl = HOME_URL;
            } else if (!tab.isHome) {
                tab.displayUrl = url;
            }
            injectScripts(tab, url);
            detectTurnstileState(tab, view, url);
            if (tab == webDriverTab && !webDriverLoadedObject.has("onPageFinished")) {
                webDriverLoaded += 1;
                runWebDriverScriptAfterLoaded(view);
                try {
                    webDriverLoadedObject.put("onPageFinished", true);
                } catch (JSONException ignored) {
                }
            }
            if (tab.pullRefreshing) {
                tab.pullRefreshing = false;
                tab.pullRefreshVisible = false;
                tab.pullRefreshProgress = 0f;
                if (tab == getCurrentTab()) {
                    setPullRefreshIndicator(false, false, 0f);
                }
            }
            if (tab == getCurrentTab()) {
                pageProgress.setProgress(100);
                pageProgress.setVisibility(View.GONE);
                updateChrome();
            }
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            if (isAutomationTab(tab) && IsTurnstilePage.isLikelyByUrl(url)) {
                installTurnstileMonitor(view);
            }
            if (tab == webDriverTab && webDriverOverrideScript != null) {
                view.evaluateJavascript(webDriverOverrideScript, null);
            }
        }

        private boolean handleUrlOverride(String url) {
            if (TextUtils.isEmpty(url)) {
                return false;
            }
            if (url.startsWith("dbgid://")) {
                loadIntoTab(tab, url);
                return true;
            }
            if (HOME_RENDER_URL.equals(url)) {
                loadIntoTab(tab, HOME_URL);
                return true;
            }
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (!TextUtils.isEmpty(scheme)
                && !"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)
                && !"file".equalsIgnoreCase(scheme)) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Throwable e) {
                    toast("No handler for " + scheme + " links.");
                }
                return true;
            }
            return false;
        }
    }

    private class BrowserChrome extends WebChromeClient {

        private final BrowserTab tab;

        BrowserChrome(BrowserTab tab) {
            this.tab = tab;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (tab == webDriverTab && newProgress == 100 && !webDriverLoadedObject.has("onProgressChanged")) {
                webDriverLoaded += 1;
                runWebDriverScriptAfterLoaded(view);
                try {
                    webDriverLoadedObject.put("onProgressChanged", true);
                } catch (JSONException ignored) {
                }
            }
            if (tab.pullRefreshing && newProgress >= 100) {
                tab.pullRefreshing = false;
                tab.pullRefreshVisible = false;
                tab.pullRefreshProgress = 0f;
                if (tab == getCurrentTab()) {
                    setPullRefreshIndicator(false, false, 0f);
                }
            }
            if (tab == getCurrentTab()) {
                pageProgress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                pageProgress.setProgress(newProgress);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (!TextUtils.isEmpty(title)) {
                tab.title = title;
                renderTabStrip();
                if (tab == getCurrentTab()) {
                    updateChrome();
                }
            }
        }

        @Override
        public void onCloseWindow(WebView window) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).webView == window) {
                    closeTab(i);
                    break;
                }
            }
        }
    }

    private static class BrowserTab {
        public String title;
        public String displayUrl;
        public boolean isHome = true;
        public boolean desktopMode = false;
        public WebView webView;
        public float swipeStartX;
        public float swipeStartY;
        public boolean swipeReloadArmed;
        public long lastSwipeReloadAt;
        public boolean pullRefreshing;
        public boolean pullRefreshVisible;
        public float pullRefreshProgress;
        public boolean turnstileDetected;
        public String turnstileStatus = "";
        public String turnstileSiteKey = "";
        public String turnstileToken = "";

        public String titleForStrip() {
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            if (!TextUtils.isEmpty(displayUrl)) {
                return displayUrl;
            }
            return "Tab";
        }
    }

    private static class PendingDownload {
        public String url;
        public String userAgent;
        public String contentDisposition;
        public String mimeType;
    }

    private static class DialogFrame {
        public AlertDialog dialog;
        public LinearLayout content;
    }
}
