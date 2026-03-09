package com.dbgid.browser;

import java.util.Random;

public final class SetRandomUserAgent {

    public static final String LATEST_CHROME_VERSION = "145.0.7632.159";
    private static final Random RANDOM = new Random();
    private static final DeviceProfile[] MODERN_ANDROID_DEVICES = new DeviceProfile[]{
            new DeviceProfile("Samsung", "Galaxy S25 Ultra", "15", "Qualcomm", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)", "adreno-830-galaxy-s25-ultra"),
            new DeviceProfile("Xiaomi", "Xiaomi 15 Ultra", "15", "Qualcomm", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)", "adreno-830-xiaomi-15-ultra"),
            new DeviceProfile("POCO", "POCO F7 Ultra", "15", "Qualcomm", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)", "adreno-830-poco-f7-ultra"),
            new DeviceProfile("vivo", "vivo X200 Pro", "15", "ARM", "ANGLE (ARM, Immortalis-G925, OpenGL ES 3.2)", "immortalis-g925-vivo-x200-pro"),
            new DeviceProfile("OPPO", "OPPO Find X8 Pro", "15", "ARM", "ANGLE (ARM, Immortalis-G925, OpenGL ES 3.2)", "immortalis-g925-oppo-find-x8-pro"),
            new DeviceProfile("HUAWEI", "HUAWEI Mate 70 Pro", "15", "Huawei", "ANGLE (Huawei, Maleoon 920, OpenGL ES 3.2)", "maleoon-920-huawei-mate-70-pro")
    };

    public static final class DeviceProfile {
        public final String brand;
        public final String model;
        public final String androidVersion;
        public final String webGlVendor;
        public final String webGlRenderer;
        public final String webGlFingerprint;

        DeviceProfile(String brand, String model, String androidVersion, String webGlVendor,
                      String webGlRenderer, String webGlFingerprint) {
            this.brand = brand;
            this.model = model;
            this.androidVersion = androidVersion;
            this.webGlVendor = webGlVendor;
            this.webGlRenderer = webGlRenderer;
            this.webGlFingerprint = webGlFingerprint;
        }

        public String buildUserAgent() {
            return "Mozilla/5.0 (Linux; Android " + androidVersion + "; " + model + ") "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + LATEST_CHROME_VERSION
                    + " Mobile Safari/537.36";
        }
    }

    private SetRandomUserAgent() {
    }

    public static DeviceProfile randomProfile() {
        return MODERN_ANDROID_DEVICES[RANDOM.nextInt(MODERN_ANDROID_DEVICES.length)];
    }

    public static String randomMobileChromeUserAgent() {
        return randomProfile().buildUserAgent();
    }

    public static String latestDesktopChromeUserAgent() {
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + LATEST_CHROME_VERSION + " Safari/537.36";
    }
}
