package com.dbgid.browser;

public final class GetTurnstileSiteKey {

    private GetTurnstileSiteKey() {
    }

    public static String detectionJavascript() {
        return "(function(){"
                + "try{"
                + "var key='';"
                + "if(window.__seledroidTurnstileSiteKey&&window.__seledroidTurnstileSiteKey.length>5){key=window.__seledroidTurnstileSiteKey;}"
                + "var direct=document.querySelector('.cf-turnstile[data-sitekey],[data-sitekey][data-cf-turnstile],[data-sitekey][class*=\\\"turnstile\\\"],[data-sitekey][id*=\\\"turnstile\\\"]');"
                + "if(direct&&direct.getAttribute('data-sitekey')){key=direct.getAttribute('data-sitekey');}"
                + "if(!key){"
                + "var all=document.querySelectorAll('[data-sitekey]');"
                + "for(var i=0;i<all.length;i++){"
                + "var el=all[i];"
                + "var v=el.getAttribute('data-sitekey');"
                + "if(!v||v.length<5){continue;}"
                + "var marker=(el.className||'')+' '+(el.id||'')+' '+(el.getAttribute('data-cf-turnstile')||'');"
                + "marker=marker.toLowerCase();"
                + "if(marker.indexOf('turnstile')!==-1||el.classList.contains('cf-turnstile')){key=v;break;}"
                + "}"
                + "}"
                + "if(!key){var frame=document.querySelector('iframe[src*=\\\"challenges.cloudflare.com\\\"][src*=\\\"sitekey=\\\"]');"
                + "if(frame&&frame.src){try{var u=new URL(frame.src);key=u.searchParams.get('sitekey')||'';}catch(_e){"
                + "var m=frame.src.match(/[?&]sitekey=([^&#]+)/);if(m&&m[1]){key=decodeURIComponent(m[1]);}}}}"
                + "if(!key&&window.__CF$cv$params&&window.__CF$cv$params.sitekey){key=window.__CF$cv$params.sitekey;}"
                + "if(!key){"
                + "var html=(document.documentElement&&document.documentElement.innerHTML)||'';"
                + "var ms=html.match(/\\\"sitekey\\\"\\s*:\\s*\\\"([^\\\"]{6,})\\\"/i);"
                + "if(ms&&ms[1]){key=ms[1];}"
                + "}"
                + "if(key&&key.length>5){window.__seledroidTurnstileSiteKey=key;}"
                + "return key||'';"
                + "}catch(e){return '';}"
                + "})();";
    }

    public static String parse(String value) {
        if (value == null) {
            return "";
        }
        String parsed = value.trim();
        if (parsed.startsWith("\"") && parsed.endsWith("\"") && parsed.length() >= 2) {
            parsed = parsed.substring(1, parsed.length() - 1);
            parsed = parsed.replace("\\\\", "\\");
            parsed = parsed.replace("\\\"", "\"");
            parsed = parsed.replace("\\/", "/");
        }
        return parsed.trim();
    }
}
