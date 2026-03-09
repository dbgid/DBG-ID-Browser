package com.dbgid.browser;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ExtensionInstaller {

    private ExtensionInstaller() {}

    public static InstallResult install(Context context, Uri uri) throws Exception {
        byte[] raw = readAll(context.getContentResolver().openInputStream(uri));
        return installFromRaw(context, raw, createExtensionId(uri), null);
    }

    public static InstallResult installFromChromeWebStore(Context context, String input) throws Exception {
        String webStoreId = parseChromeWebStoreId(input);
        if (TextUtils.isEmpty(webStoreId)) {
            throw new IOException("Enter a Chrome Web Store URL or 32-character extension ID.");
        }
        byte[] raw = downloadBytes(buildChromeWebStoreCrxUrl(webStoreId));
        return installFromRaw(context, raw, "cws_" + webStoreId, webStoreId);
    }

    public static class InstallResult {
        public BrowserStore.ExtensionPackage extension;
        public String warning;
    }

    private static InstallResult installFromRaw(Context context, byte[] raw, String extensionId, String webStoreId) throws Exception {
        BrowserStore store = new BrowserStore(context);
        byte[] archive = extractArchivePayload(raw);

        File extractDir = new File(store.getExtensionsRoot(), extensionId);
        BrowserStore.deleteRecursive(extractDir);
        extractDir.mkdirs();

        unzip(archive, extractDir);
        File manifestRoot = resolveManifestRoot(extractDir);
        File manifestFile = new File(manifestRoot, "manifest.json");
        JSONObject manifest = new JSONObject(BrowserStore.readText(manifestFile));

        BrowserStore.ExtensionPackage extension = new BrowserStore.ExtensionPackage();
        extension.id = extensionId;
        extension.webStoreId = webStoreId;
        extension.name = cleanManifestName(manifest.optString("name", extensionId));
        extension.version = manifest.optString("version", "0");
        extension.directoryPath = manifestRoot.getAbsolutePath();
        extension.manifestVersion = manifest.optInt("manifest_version", 2);
        extension.optionsPage = readOptionsPage(manifest);
        extension.popupPage = readPopupPage(manifest);
        readBackgroundEntries(extension, manifest);
        readContentScripts(extension, manifest);

        InstallResult result = new InstallResult();
        result.extension = extension;
        if (extension.contentScripts.isEmpty()
            && TextUtils.isEmpty(extension.backgroundPage)
            && extension.backgroundScripts.isEmpty()
            && TextUtils.isEmpty(extension.backgroundServiceWorker)
            && TextUtils.isEmpty(extension.optionsPage)
            && TextUtils.isEmpty(extension.popupPage)) {
            result.warning = "Imported manifest, but this extension has no supported page, background, or content script entry points.";
        }
        return result;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Unable to open selected file");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
            output.close();
        }
    }

    private static byte[] downloadBytes(String urlString) throws IOException {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "DBG-ID-Browser");
            connection.connect();
            input = connection.getInputStream();
            return readAll(input);
        } finally {
            if (input != null) {
                input.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] extractArchivePayload(byte[] raw) throws IOException {
        if (raw.length < 4) {
            throw new IOException("Extension archive is too small");
        }
        if (raw[0] == 'P' && raw[1] == 'K') {
            return raw;
        }
        if (raw[0] == 'C' && raw[1] == 'r' && raw[2] == '2' && raw[3] == '4') {
            int version = readLittleEndian(raw, 4);
            int headerLength;
            if (version == 2) {
                int publicKeyLength = readLittleEndian(raw, 8);
                int signatureLength = readLittleEndian(raw, 12);
                headerLength = 16 + publicKeyLength + signatureLength;
            } else if (version == 3) {
                int protobufLength = readLittleEndian(raw, 8);
                headerLength = 12 + protobufLength;
            } else {
                throw new IOException("Unsupported CRX version: " + version);
            }
            if (headerLength >= raw.length) {
                throw new IOException("Invalid CRX header");
            }
            byte[] payload = new byte[raw.length - headerLength];
            System.arraycopy(raw, headerLength, payload, 0, payload.length);
            return payload;
        }
        throw new IOException("Only ZIP and CRX archives are supported");
    }

    private static int readLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static void unzip(byte[] archive, File targetDirectory) throws IOException {
        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File outputFile = safeFile(targetDirectory, entry.getName());
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    BrowserStore.writeBytes(outputFile, output.toByteArray());
                }
                input.closeEntry();
            }
        } finally {
            input.close();
        }
    }

    private static File safeFile(File directory, String entryName) throws IOException {
        File output = new File(directory, entryName);
        String rootPath = directory.getCanonicalPath();
        String outputPath = output.getCanonicalPath();
        if (!outputPath.startsWith(rootPath + File.separator) && !outputPath.equals(rootPath)) {
            throw new IOException("Archive entry escaped target directory");
        }
        return output;
    }

    private static File resolveManifestRoot(File extractDir) throws IOException {
        File manifest = new File(extractDir, "manifest.json");
        if (manifest.exists()) {
            return extractDir;
        }
        File[] files = extractDir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory() && new File(files[i], "manifest.json").exists()) {
                    return files[i];
                }
            }
        }
        throw new IOException("manifest.json not found in selected extension");
    }

    private static String cleanManifestName(String name) {
        if (TextUtils.isEmpty(name) || name.startsWith("__MSG_")) {
            return "Imported Extension";
        }
        return name;
    }

    private static void readContentScripts(BrowserStore.ExtensionPackage extension, JSONObject manifest) {
        JSONArray contentScripts = manifest.optJSONArray("content_scripts");
        if (contentScripts == null) {
            return;
        }
        for (int i = 0; i < contentScripts.length(); i++) {
            JSONObject item = contentScripts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            BrowserStore.ExtensionContentScript script = new BrowserStore.ExtensionContentScript();
            JSONArray matches = item.optJSONArray("matches");
            JSONArray jsFiles = item.optJSONArray("js");
            if (matches != null) {
                for (int j = 0; j < matches.length(); j++) {
                    script.matches.add(matches.optString(j));
                }
            }
            if (jsFiles != null) {
                for (int j = 0; j < jsFiles.length(); j++) {
                    script.jsFiles.add(jsFiles.optString(j));
                }
            }
            if (!script.matches.isEmpty() && !script.jsFiles.isEmpty()) {
                extension.contentScripts.add(script);
            }
        }
    }

    private static void readBackgroundEntries(BrowserStore.ExtensionPackage extension, JSONObject manifest) {
        JSONObject background = manifest.optJSONObject("background");
        if (background == null) {
            return;
        }
        extension.backgroundPage = background.optString("page");
        extension.backgroundServiceWorker = background.optString("service_worker");
        JSONArray scripts = background.optJSONArray("scripts");
        if (scripts != null) {
            for (int i = 0; i < scripts.length(); i++) {
                extension.backgroundScripts.add(scripts.optString(i));
            }
        }
    }

    private static String readOptionsPage(JSONObject manifest) {
        JSONObject optionsUi = manifest.optJSONObject("options_ui");
        if (optionsUi != null && !TextUtils.isEmpty(optionsUi.optString("page"))) {
            return optionsUi.optString("page");
        }
        return manifest.optString("options_page");
    }

    private static String readPopupPage(JSONObject manifest) {
        JSONObject action = manifest.optJSONObject("action");
        if (action != null && !TextUtils.isEmpty(action.optString("default_popup"))) {
            return action.optString("default_popup");
        }
        JSONObject browserAction = manifest.optJSONObject("browser_action");
        if (browserAction != null && !TextUtils.isEmpty(browserAction.optString("default_popup"))) {
            return browserAction.optString("default_popup");
        }
        JSONObject pageAction = manifest.optJSONObject("page_action");
        if (pageAction != null && !TextUtils.isEmpty(pageAction.optString("default_popup"))) {
            return pageAction.optString("default_popup");
        }
        return "";
    }

    private static String parseChromeWebStoreId(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.matches("[a-z]{32}")) {
            return trimmed;
        }
        int detailIndex = trimmed.indexOf("/detail/");
        if (detailIndex >= 0) {
            String tail = trimmed.substring(detailIndex + "/detail/".length());
            String[] segments = tail.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String segment = segments[i];
                if (!TextUtils.isEmpty(segment) && segment.matches("[a-z]{32}")) {
                    return segment;
                }
            }
        }
        return "";
    }

    private static String buildChromeWebStoreCrxUrl(String webStoreId) throws IOException {
        String x = "id=" + webStoreId + "&installsource=ondemand&uc";
        return "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=120.0.0.0&acceptformat=crx2,crx3&x=" + URLEncoder.encode(x, "UTF-8");
    }

    private static String createExtensionId(Uri uri) {
        String lastSegment = uri == null ? "extension" : uri.getLastPathSegment();
        if (TextUtils.isEmpty(lastSegment)) {
            lastSegment = "extension";
        }
        String normalized = lastSegment.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24);
        }
        return normalized + "_" + Long.toHexString(System.currentTimeMillis());
    }
}
