# DBG ID BROWSER

> DBG ID BROWSER the ultimate android browser on Android.

Inspired by [Seledroid-Chromium](https://github.com/luanon404/Seledroid-Chromium).

---

## What It Is

DBG ID BROWSER is a compact Android WebView browser project that mixes:

- normal visible browsing
- webdriver-compatible launch handling
- user-script injection
- network inspection
- Turnstile visibility tools
- random mobile browser identity
- IP header selection

It is built for on-device use inside an Android project, with a lightweight shell and a practical browser-focused toolset.

---

## Feature Map

| Area | What You Get |
| --- | --- |
| Browser | Multi-tab browsing, compact toolbar, address bar helpers, downloads, and external browser handoff |
| Modes | Normal browser mode by default, webdriver mode when launched through `SplashActivity` payload flow |
| Identity | Random mobile user agent, WebGL profile, per-site desktop mode, selectable IP header set |
| Automation | Webdriver socket command handling with payload parsing for supported launch formats |
| Scripts | On-device user script editor and match-pattern injection |
| Network | Optional in-page request overlay for fetch/XHR inspection |
| Turnstile | Floating terminal panels for site key and solved token visibility |
| Tools | Real IP check, pull-to-refresh, internal pages, bookmarks, and downloads history |

---

## Project Info

| Field | Value |
| --- | --- |
| App Name | `DBG ID BROWSER` |
| Application ID | `com.dbgid.browser` |
| Namespace | `com.dbgid.browser` |
| Min SDK | `21` |
| Target SDK | `34` |
| Compile SDK | `34` |
| Version Code | `2` |
| Version Name | `1.1` |
| Launcher Flow | `SplashActivity` -> `MainActivity` / `MainService` |
| Main Package | `app/src/main/java/com/dbgid/browser` |

---

## Highlights

### Browser Shell

- compact header and bottom navigation
- enlarged WebView area for device width and height
- quick clear button in the address bar
- home page with project links and browser tools

### Identity Control

- random Android mobile user agent
- modern WebGL override support
- desktop mode only when explicitly enabled
- generated IP header sets from `ip2asn-v4-u32.tsv`

### Turnstile Visibility

- left-bottom floating site-key panel
- right-bottom floating solved-token panel
- copy, minimize, maximize, and drag support

### Automation Compatibility

- webdriver payload parsing
- socket-based command handling
- separation between normal browsing and webdriver-triggered flow

---

## Build

### Requirements

- Android SDK
- Java `21`
- Gradle `8.13`
- Android Gradle Plugin `8.13.2`
- Android Studio or a local Gradle environment

### Build Locally

From the project root:

```bash
./gradlew clean assembleDebug --no-daemon
```

If the Gradle wrapper is not present, use a local Gradle `8.13` installation and ensure the project targets:

```bash
AGP 8.13.2
Java 21
minSdk 21
compileSdk 34
targetSdk 34
```

### Build Flow

1. Install Android SDK platform `34` and matching build tools.
2. Use Java `21`.
3. Open the project in Android Studio or run Gradle from the project root.
4. Build the debug APK with `assembleDebug`.

### Output

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Download

[Download APK](https://github.com/dbgid/DBG-ID-Browser/releases/download/v.1.1/com.dbgid.browser.apk)

You can also get builds from the GitHub Releases section.

---

## Start Page Link

GitHub shortcut on the home page:

```text
https://github.com/dbgid
```
