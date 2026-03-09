package com.dbgid.browser;

public final class SetWebGl {

    private SetWebGl() {
    }

    public static String buildOverrideJavascript(SetRandomUserAgent.DeviceProfile profile) {
        if (profile == null) {
            return buildOverrideJavascript("Qualcomm", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)", "adreno-830-generic");
        }
        return buildOverrideJavascript(profile.webGlVendor, profile.webGlRenderer, profile.webGlFingerprint);
    }

    public static String buildOverrideJavascript(String vendor, String renderer, String fingerprint) {
        String safeVendor = escapeJs(vendor);
        String safeRenderer = escapeJs(renderer);
        String safeFingerprint = escapeJs(fingerprint);
        return "(function(){"
                + "try{"
                + "if(window.__dbgModernWebGlApplied){return true;}"
                + "window.__dbgModernWebGlApplied=true;"
                + "var vendor='" + safeVendor + "';"
                + "var renderer='" + safeRenderer + "';"
                + "var fingerprint='" + safeFingerprint + "';"
                + "var patch=function(proto){"
                + "if(!proto||proto.__dbgModernWebGlPatched){return;}"
                + "var originalGetParameter=proto.getParameter;"
                + "var originalGetExtension=proto.getExtension;"
                + "proto.getParameter=function(param){"
                + "if(param===37445){return vendor;}"
                + "if(param===37446){return renderer;}"
                + "return originalGetParameter.apply(this,arguments);"
                + "};"
                + "proto.getExtension=function(name){"
                + "if(name==='WEBGL_debug_renderer_info'){"
                + "return {UNMASKED_VENDOR_WEBGL:37445,UNMASKED_RENDERER_WEBGL:37446};"
                + "}"
                + "return originalGetExtension.apply(this,arguments);"
                + "};"
                + "proto.__dbgModernWebGlPatched=true;"
                + "};"
                + "patch(window.WebGLRenderingContext&&window.WebGLRenderingContext.prototype);"
                + "patch(window.WebGL2RenderingContext&&window.WebGL2RenderingContext.prototype);"
                + "try{Object.defineProperty(window,'__dbgModernWebGlFingerprint',{value:fingerprint,configurable:true});}catch(_e){}"
                + "return true;"
                + "}catch(e){return false;}"
                + "})();";
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
