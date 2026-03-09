package com.dbgid.browser;

import org.json.JSONException;
import org.json.JSONObject;

public final class isWasSolve {

    public static final class Result {
        public final boolean challengePage;
        public final boolean solved;
        public final String token;
        public final boolean turnstileWidget;
        public final boolean turnstileApi;

        public Result(boolean challengePage, boolean solved, String token, boolean turnstileWidget, boolean turnstileApi) {
            this.challengePage = challengePage;
            this.solved = solved;
            this.token = token;
            this.turnstileWidget = turnstileWidget;
            this.turnstileApi = turnstileApi;
        }

        public boolean hasTurnstileSignal() {
            return turnstileWidget || turnstileApi || challengePage;
        }
    }

    private isWasSolve() {
    }

    public static String installMonitorJavascript() {
        return "(function(){"
                + "try{"
                + "if(window.__seledroidTurnstileMonitorInstalled){return true;}"
                + "window.__seledroidTurnstileMonitorInstalled=true;"
                + "window.__seledroidTurnstileSiteKey=window.__seledroidTurnstileSiteKey||'';"
                + "window.__seledroidTurnstileToken=window.__seledroidTurnstileToken||'';"
                + "window.__seledroidChallengeFrameReady=window.__seledroidChallengeFrameReady||false;"
                + "var wrapCallbacks=function(opts){"
                + "if(!opts||typeof opts!=='object'){return;}"
                + "if(opts.sitekey&&typeof opts.sitekey==='string'){window.__seledroidTurnstileSiteKey=opts.sitekey;}"
                + "if(typeof opts.callback==='function'&&!opts.__seledroidWrapped){"
                + "var cb=opts.callback;"
                + "opts.callback=function(token){"
                + "if(token&&token.length>10){window.__seledroidTurnstileToken=token;}"
                + "return cb.apply(this,arguments);"
                + "};"
                + "opts.__seledroidWrapped=true;"
                + "}"
                + "};"
                + "var patchTurnstile=function(){"
                + "if(!window.turnstile||window.turnstile.__seledroidPatched){return;}"
                + "try{"
                + "var t=window.turnstile;"
                + "if(typeof t.render==='function'){"
                + "var oldRender=t.render;"
                + "t.render=function(container,opts){"
                + "try{wrapCallbacks(opts);}catch(_e){}"
                + "return oldRender.apply(this,arguments);"
                + "};"
                + "}"
                + "if(typeof t.getResponse==='function'){"
                + "var oldGet=t.getResponse;"
                + "t.getResponse=function(widgetId){"
                + "var r=oldGet.apply(this,arguments);"
                + "if(r&&r.length>10){window.__seledroidTurnstileToken=r;}"
                + "return r;"
                + "};"
                + "}"
                + "t.__seledroidPatched=true;"
                + "}catch(_e){}"
                + "};"
                + "patchTurnstile();"
                + "var bindChallengeFrames=function(){"
                + "var frames=document.querySelectorAll('iframe[src*=\\\"challenges.cloudflare.com\\\"],iframe[src*=\\\"challenge-platform\\\"]');"
                + "for(var i=0;i<frames.length;i++){"
                + "var f=frames[i];"
                + "if(f.getAttribute('data-seledroid-listener')!=='1'){"
                + "f.setAttribute('data-seledroid-listener','1');"
                + "f.addEventListener('load',function(){"
                + "try{this.setAttribute('data-seledroid-loaded','1');window.__seledroidChallengeFrameReady=true;}catch(_e){}"
                + "},true);"
                + "}"
                + "try{"
                + "if(f.contentDocument&&f.contentDocument.readyState==='complete'){"
                + "f.setAttribute('data-seledroid-loaded','1');"
                + "window.__seledroidChallengeFrameReady=true;"
                + "}"
                + "}catch(_e){}"
                + "}"
                + "};"
                + "var refresh=function(){"
                + "patchTurnstile();"
                + "bindChallengeFrames();"
                + "try{"
                + "var direct=document.querySelector('.cf-turnstile[data-sitekey],[data-sitekey][data-cf-turnstile]');"
                + "if(direct&&direct.getAttribute('data-sitekey')){window.__seledroidTurnstileSiteKey=direct.getAttribute('data-sitekey');}"
                + "var resp=document.querySelector('textarea[name=\\\"cf-turnstile-response\\\"],input[name=\\\"cf-turnstile-response\\\"]');"
                + "if(resp&&resp.value&&resp.value.length>10){window.__seledroidTurnstileToken=resp.value;}"
                + "}catch(_e){}"
                + "};"
                + "window.__seledroidTurnstileRefresh=refresh;"
                + "var obs=new MutationObserver(function(){refresh();});"
                + "obs.observe(document.documentElement||document,{childList:true,subtree:true,attributes:true});"
                + "window.__seledroidTurnstileObserver=obs;"
                + "refresh();"
                + "return true;"
                + "}catch(e){return false;}"
                + "})();";
    }

    public static String detectionJavascript() {
        return "(function(){"
                + "try{"
                + "var href=(location.href||'').toLowerCase();"
                + "var text=((document.title||'')+' '+((document.body&&document.body.innerText)||'')).toLowerCase();"
                + "var hasScript=function(){"
                + "var scripts=document.getElementsByTagName('script');"
                + "for(var i=0;i<scripts.length;i++){"
                + "var src=(scripts[i].src||'').toLowerCase();"
                + "if(!src){continue;}"
                + "if(src.indexOf('challenges.cloudflare.com/turnstile')!==-1||src.indexOf('/turnstile/v0/api.js')!==-1){return true;}"
                + "}"
                + "return false;"
                + "};"
                + "var widget=!!document.querySelector('.cf-turnstile,[data-sitekey][data-cf-turnstile],iframe[src*=\\\"challenges.cloudflare.com\\\"][src*=\\\"turnstile\\\"]');"
                + "var api=!!window.turnstile||hasScript()||href.indexOf('/turnstile/v0/')!==-1;"
                + "var challenge=false;"
                + "if(text.indexOf('just a moment')!==-1||text.indexOf('checking your browser before accessing')!==-1||text.indexOf('verify you are human')!==-1||href.indexOf('/cdn-cgi/challenge-platform/')!==-1||href.indexOf('challenge-platform')!==-1||href.indexOf('cf_chl')!==-1){challenge=true;}"
                + "var token='';"
                + "var selectors=['textarea[name=\\\"cf-turnstile-response\\\"]','input[name=\\\"cf-turnstile-response\\\"]','textarea[name=\\\"cf_challenge_response\\\"]','input[name=\\\"cf_challenge_response\\\"]'];"
                + "for(var i=0;i<selectors.length;i++){var el=document.querySelector(selectors[i]);if(el&&el.value&&el.value.length>10){token=el.value;break;}}"
                + "if(!token&&window.turnstile&&typeof window.turnstile.getResponse==='function'){try{var r=window.turnstile.getResponse();if(r&&r.length>10){token=r;}}catch(_e){}}"
                + "if(!token&&window.__seledroidTurnstileToken&&window.__seledroidTurnstileToken.length>10){token=window.__seledroidTurnstileToken;}"
                + "var solved=token.length>10;"
                + "return JSON.stringify({challenge:challenge,solved:solved,token:token,widget:widget,api:api});"
                + "}catch(e){return JSON.stringify({challenge:false,solved:false,token:'',widget:false,api:false});}"
                + "})();";
    }

    public static String continueAfterSolveJavascript() {
        return "(function(){"
                + "try{"
                + "var token=window.__seledroidTurnstileToken||'';"
                + "if(!token||token.length<10){return false;}"
                + "if(window.__seledroidContinueAt&&Date.now()-window.__seledroidContinueAt<1500){return false;}"
                + "window.__seledroidContinueAt=Date.now();"
                + "var fields=document.querySelectorAll('textarea[name=\\\"cf-turnstile-response\\\"],input[name=\\\"cf-turnstile-response\\\"],textarea[name=\\\"cf_challenge_response\\\"],input[name=\\\"cf_challenge_response\\\"]');"
                + "for(var i=0;i<fields.length;i++){"
                + "var f=fields[i];"
                + "try{if(f.value!==token){f.value=token;}}catch(_e){}"
                + "try{f.dispatchEvent(new Event('input',{bubbles:true}));}catch(_e){}"
                + "try{f.dispatchEvent(new Event('change',{bubbles:true}));}catch(_e){}"
                + "if(f.form&&!f.form.__seledroidSubmitted){"
                + "try{"
                + "f.form.__seledroidSubmitted=true;"
                + "if(typeof f.form.requestSubmit==='function'){f.form.requestSubmit();}else{f.form.submit();}"
                + "}catch(_e){f.form.__seledroidSubmitted=false;}"
                + "}"
                + "}"
                + "var btn=document.querySelector('button[type=\\\"submit\\\"],input[type=\\\"submit\\\"],button[data-action*=\\\"verify\\\"]');"
                + "if(btn&&!btn.disabled){try{btn.click();}catch(_e){}}"
                + "return true;"
                + "}catch(e){return false;}"
                + "})();";
    }

    public static Result parse(String value) {
        try {
            String json = decodeEvaluateJavascriptResult(value);
            JSONObject object = new JSONObject(json);
            String token = object.optString("token", "");
            boolean solved = object.optBoolean("solved", false) || token.length() > 10;
            boolean challengePage = object.optBoolean("challenge", false);
            boolean widget = object.optBoolean("widget", false);
            boolean api = object.optBoolean("api", false);
            return new Result(challengePage, solved, token, widget, api);
        } catch (JSONException ignored) {
            return new Result(false, false, "", false, false);
        }
    }

    public static String normalizeTokenForCopy(String token) {
        if (token == null) {
            return "";
        }
        return token.trim();
    }

    public static String normalizeTokenForCopy(Result result) {
        if (result == null) {
            return "";
        }
        return normalizeTokenForCopy(result.token);
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
