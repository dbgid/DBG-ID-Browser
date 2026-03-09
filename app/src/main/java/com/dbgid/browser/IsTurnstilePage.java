package com.dbgid.browser;

import org.json.JSONException;
import org.json.JSONObject;

public final class IsTurnstilePage {

    public static final class Result {
        public final boolean turnstileWidget;
        public final boolean turnstileApi;
        public final boolean cloudflareChallenge;
        public final boolean pageReady;
        public final boolean iframeReady;
        public final boolean challengeReady;

        public Result(boolean turnstileWidget, boolean turnstileApi, boolean cloudflareChallenge,
                      boolean pageReady, boolean iframeReady, boolean challengeReady) {
            this.turnstileWidget = turnstileWidget;
            this.turnstileApi = turnstileApi;
            this.cloudflareChallenge = cloudflareChallenge;
            this.pageReady = pageReady;
            this.iframeReady = iframeReady;
            this.challengeReady = challengeReady;
        }

        public boolean isDetected() {
            return turnstileWidget || turnstileApi || cloudflareChallenge;
        }
    }

    private IsTurnstilePage() {
    }

    public static boolean isLikelyByUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("challenges.cloudflare.com")
                || lower.contains("/cdn-cgi/challenge-platform/")
                || lower.contains("/turnstile/")
                || lower.contains("cf-chl");
    }

    public static String detectionJavascript() {
        return "(function(){"
                + "try{"
                + "var href=(location.href||'').toLowerCase();"
                + "var title=(document.title||'').toLowerCase();"
                + "var text=(document.body&&document.body.innerText?document.body.innerText:'').toLowerCase();"
                + "var q=function(s){return document.querySelector(s)!==null;};"
                + "var hasScript=function(){"
                + "var scripts=document.getElementsByTagName('script');"
                + "for(var i=0;i<scripts.length;i++){"
                + "var src=(scripts[i].src||'').toLowerCase();"
                + "if(!src){continue;}"
                + "if(src.indexOf('challenges.cloudflare.com/turnstile')!==-1||src.indexOf('/turnstile/v0/api.js')!==-1){return true;}"
                + "}"
                + "return false;"
                + "};"
                + "var widget=q('.cf-turnstile,[data-sitekey][data-cf-turnstile],iframe[src*=\\\"challenges.cloudflare.com\\\"][src*=\\\"turnstile\\\"]');"
                + "var api=!!window.turnstile||hasScript()||href.indexOf('/turnstile/v0/')!==-1||!!window.__seledroidTurnstileSiteKey||!!window.__seledroidTurnstileToken;"
                + "var pageReady=document.readyState==='complete';"
                + "var frames=document.querySelectorAll('iframe[src*=\\\"challenges.cloudflare.com\\\"],iframe[src*=\\\"challenge-platform\\\"]');"
                + "var iframeReady=false;"
                + "if(frames.length===0){iframeReady=pageReady;}"
                + "for(var i=0;i<frames.length;i++){"
                + "var f=frames[i];"
                + "var loaded=f.getAttribute('data-seledroid-loaded')==='1';"
                + "if(!loaded){"
                + "try{"
                + "if(f.contentDocument&&f.contentDocument.readyState==='complete'){loaded=true;}"
                + "}catch(_e){}"
                + "}"
                + "if(loaded){iframeReady=true;break;}"
                + "}"
                + "var challenge="
                + "href.indexOf('/cdn-cgi/challenge-platform/')!==-1"
                + "||href.indexOf('challenges.cloudflare.com')!==-1&&href.indexOf('challenge-platform')!==-1"
                + "||href.indexOf('cf_chl')!==-1"
                + "||title.indexOf('just a moment')!==-1"
                + "||text.indexOf('checking your browser before accessing')!==-1"
                + "||text.indexOf('verify you are human')!==-1;"
                + "var challengeReady=!challenge||(pageReady&&iframeReady);"
                + "return JSON.stringify({widget:widget,api:api,challenge:challenge,pageReady:pageReady,iframeReady:iframeReady,challengeReady:challengeReady});"
                + "}catch(e){return JSON.stringify({widget:false,api:false,challenge:false,pageReady:false,iframeReady:false,challengeReady:false});}"
                + "})();";
    }

    public static Result parse(String value) {
        try {
            String json = decodeEvaluateJavascriptResult(value);
            JSONObject object = new JSONObject(json);
            return new Result(
                    object.optBoolean("widget", false),
                    object.optBoolean("api", false),
                    object.optBoolean("challenge", false),
                    object.optBoolean("pageReady", false),
                    object.optBoolean("iframeReady", false),
                    object.optBoolean("challengeReady", false)
            );
        } catch (JSONException ignored) {
            return new Result(false, false, false, false, false, false);
        }
    }

    private static String decodeEvaluateJavascriptResult(String value) {
        if (value == null) {
            return "{}";
        }
        String decoded = value.trim();
        if (decoded.startsWith("\"") && decoded.endsWith("\"") && decoded.length() >= 2) {
            decoded = decoded.substring(1, decoded.length() - 1);
            decoded = decoded.replace("\\\\", "\\");
            decoded = decoded.replace("\\\"", "\"");
            decoded = decoded.replace("\\/", "/");
            decoded = decoded.replace("\\n", "\n");
            decoded = decoded.replace("\\t", "\t");
        }
        return decoded;
    }
}
