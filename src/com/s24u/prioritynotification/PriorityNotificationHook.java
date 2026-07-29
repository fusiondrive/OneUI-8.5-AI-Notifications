package com.s24u.prioritynotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PriorityNotificationHook
        implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "OneUI85AINotifications";
    private static final String ANDROID_PACKAGE = "android";
    private static final String SMART_SUGGESTIONS_PACKAGE =
            "com.samsung.android.smartsuggestions";
    private static final String HONEYBOARD_PACKAGE = "com.samsung.android.honeyboard";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String RUNE_CLASS = "com.android.server.notification.NmRune";
    private static final String SERVICE_CLASS =
            "com.android.server.notification.NotificationManagerService";
    private static final String SMART_SUGGESTIONS_RUNE_CLASS =
            "com.samsung.android.smartsuggestions.featureconfig.rune.Rune";
    private static final String NOW_NUDGE_SETTING_HELPER_CLASS =
            "com.samsung.android.smartsuggestions.service.screenintelligence.setting."
                    + "NowNudgesSettingHelper";
    private static final String HONEYBOARD_RUNE_CLASS = "Y8.a";
    private static final String HONEYBOARD_REGION_CLASS = "k7.g";
    private static final String HONEYBOARD_DATABASE_PATH_CLASS = "lj.c";
    private static final String HONEYBOARD_CHINA_RUNE_CLASS = "sj.d";
    private static final String HONEYBOARD_CHINA_REGION_CLASS = "md.c";
    private static final String HONEYBOARD_CHINA_DATABASE_PATH_CLASS = "o50.e";
    private static final String HONEYBOARD_APPLICATION_CLASS =
            "com.samsung.android.honeyboard.app.HoneyBoardApplication";
    private static final String SETTINGS_NOW_NUDGE_CONTROLLER =
            "com.samsung.android.settings.usefulfeature.intelligenceservice."
                    + "NowNudgesGalaxyAIController";
    private static final String PRIORITY_FIELD = "NM_SUPPORT_AI_NOTIFICATION_PRIORITY";
    private static final String SUMMARY_FIELD = "NM_SUPPORT_AI_NOTIFICATION_SUMMARY";
    private static final String HONEYBOARD_NOW_NUDGE_REGION_FLAG = "I";
    private static final String HONEYBOARD_NOW_NUDGE_OTHER_REGION_FLAG = "K";
    private static final String HONEYBOARD_SOGOU_FLAG = "N6";
    private static final String HONEYBOARD_CHINESE_FEATURE_FLAG = "F5";
    private static final String HONEYBOARD_DETAILED_DICTIONARY_FLAG = "x6";
    private static final String SOGOU_ASSET_PREFIX = "assets/sogou_db/";
    private static final String SOGOU_CELL_ASSET_PREFIX = "assets/sogou_cell/";
    private static final String SOGOU_DATABASE_DIRECTORY = "files/oneui85-sogou-db-v2";
    private static final String SOGOU_CELL_DIRECTORY = "files/oneui85-sogou-cell-v2";
    private static final String SOGOU_MARKER = ".oneui85_sogou_v2";
    private static String modulePath;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (ANDROID_PACKAGE.equals(loadPackageParam.packageName)
                && ANDROID_PACKAGE.equals(loadPackageParam.processName)) {
            installNotificationHooks(loadPackageParam);
            return;
        }

        if (SMART_SUGGESTIONS_PACKAGE.equals(loadPackageParam.packageName)) {
            installSmartSuggestionsNowNudgeHooks(loadPackageParam.classLoader);
            return;
        }

        if (HONEYBOARD_PACKAGE.equals(loadPackageParam.packageName)) {
            enableHoneyboardFeatures(
                    loadPackageParam.classLoader, loadPackageParam.appInfo.dataDir);
            return;
        }

        if (SETTINGS_PACKAGE.equals(loadPackageParam.packageName)) {
            installSettingsNowNudgeHook(loadPackageParam.classLoader);
        }
    }

    private static void installNotificationHooks(
            XC_LoadPackage.LoadPackageParam loadPackageParam) {
        forceNotificationAiRunes(loadPackageParam.classLoader, "package load");

        Class<?> notificationManagerService =
                XposedHelpers.findClassIfExists(SERVICE_CLASS, loadPackageParam.classLoader);
        if (notificationManagerService == null) {
            log("NotificationManagerService not found; hook disabled");
            return;
        }

        XposedBridge.hookAllConstructors(notificationManagerService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                forceNotificationAiRunes(loadPackageParam.classLoader, "NMS constructor");
            }
        });
        log("Hook installed for system_server");
    }

    private static void installSmartSuggestionsNowNudgeHooks(ClassLoader classLoader) {
        try {
            Class<?> rune = XposedHelpers.findClass(SMART_SUGGESTIONS_RUNE_CLASS, classLoader);
            forceBooleanResult(rune, "getSUPPORT_NOW_NUDGE", true);
            forceBooleanResult(rune, "getSUPPORT_AMBIENT_NUDGE", false);

            Class<?> settingHelper =
                    XposedHelpers.findClassIfExists(
                            NOW_NUDGE_SETTING_HELPER_CLASS, classLoader);
            if (settingHelper != null) {
                forceBooleanResult(settingHelper, "isNowNudgesSwitchOn", true);
                forceBooleanResult(settingHelper, "isNowNudgesFTU", false);
            }
            log("Now Nudge enabled in Smart Suggestions; ambient route kept disabled");
        } catch (Throwable throwable) {
            logThrowable("Smart Suggestions Now Nudge hook failed", throwable);
        }
    }

    private static void enableHoneyboardFeatures(ClassLoader classLoader, String dataDir) {
        Class<?> globalRegion =
                XposedHelpers.findClassIfExists(HONEYBOARD_REGION_CLASS, classLoader);
        Class<?> globalRune =
                XposedHelpers.findClassIfExists(HONEYBOARD_RUNE_CLASS, classLoader);
        if (globalRegion != null && globalRune != null) {
            forceBooleanResult(globalRegion, "A", true);
            log("Samsung Keyboard global-build China-region predicate forced on");
            deferHoneyboardRuneChanges(classLoader);
        } else {
            enableChinaHoneyboardFeatures(classLoader);
        }

        File databaseDirectory = installBundledSogouDatabase(dataDir);
        if (databaseDirectory != null) {
            hookSogouDatabasePath(classLoader, databaseDirectory);
        }
        enablePortedXt9Database(classLoader, dataDir);
    }

    private static void enableChinaHoneyboardFeatures(ClassLoader classLoader) {
        try {
            Class<?> region =
                    XposedHelpers.findClass(HONEYBOARD_CHINA_REGION_CLASS, classLoader);
            forceBooleanResult(region, "a", true);

            Class<?> rune =
                    XposedHelpers.findClass(HONEYBOARD_CHINA_RUNE_CLASS, classLoader);
            forceBooleanResult(rune, "m3", true);
            forceBooleanResult(rune, "l3", true);
            log("Samsung Keyboard China-build Sogou and Now Nudge gates forced on");
        } catch (Throwable throwable) {
            logThrowable("Samsung Keyboard China-build feature hook failed", throwable);
        }
    }

    private static void deferHoneyboardRuneChanges(final ClassLoader classLoader) {
        try {
            Class<?> applicationClass =
                    XposedHelpers.findClass(HONEYBOARD_APPLICATION_CLASS, classLoader);
            XposedBridge.hookAllMethods(
                    applicationClass,
                    "attachBaseContext",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            forceHoneyboardRunes(classLoader);
                        }
                    });
            log("Samsung Keyboard feature flags scheduled after app initialization");
        } catch (Throwable throwable) {
            logThrowable("Samsung Keyboard deferred feature hook failed", throwable);
        }
    }

    private static void forceHoneyboardRunes(ClassLoader classLoader) {
        try {
            Class<?> rune = XposedHelpers.findClass(HONEYBOARD_RUNE_CLASS, classLoader);
            XposedHelpers.setStaticBooleanField(
                    rune, HONEYBOARD_NOW_NUDGE_REGION_FLAG, true);
            XposedHelpers.setStaticBooleanField(
                    rune, HONEYBOARD_NOW_NUDGE_OTHER_REGION_FLAG, true);
            XposedHelpers.setStaticBooleanField(rune, HONEYBOARD_SOGOU_FLAG, true);
            XposedHelpers.setStaticBooleanField(
                    rune, HONEYBOARD_CHINESE_FEATURE_FLAG, true);
            XposedHelpers.setStaticBooleanField(
                    rune, HONEYBOARD_DETAILED_DICTIONARY_FLAG, true);
            log(
                    "Samsung Keyboard Now Nudge receiver flags: "
                            + HONEYBOARD_NOW_NUDGE_REGION_FLAG
                            + "="
                            + XposedHelpers.getStaticBooleanField(
                                    rune, HONEYBOARD_NOW_NUDGE_REGION_FLAG)
                            + ", "
                            + HONEYBOARD_NOW_NUDGE_OTHER_REGION_FLAG
                            + "="
                            + XposedHelpers.getStaticBooleanField(
                                    rune, HONEYBOARD_NOW_NUDGE_OTHER_REGION_FLAG));
            log(
                    "Samsung Keyboard Chinese engine flags: "
                            + HONEYBOARD_SOGOU_FLAG
                            + "="
                            + XposedHelpers.getStaticBooleanField(
                                    rune, HONEYBOARD_SOGOU_FLAG)
                            + ", "
                            + HONEYBOARD_CHINESE_FEATURE_FLAG
                            + "="
                            + XposedHelpers.getStaticBooleanField(
                                    rune, HONEYBOARD_CHINESE_FEATURE_FLAG)
                            + ", "
                            + HONEYBOARD_DETAILED_DICTIONARY_FLAG
                            + "="
                            + XposedHelpers.getStaticBooleanField(
                                    rune, HONEYBOARD_DETAILED_DICTIONARY_FLAG));
        } catch (Throwable throwable) {
            logThrowable("Samsung Keyboard feature enable failed", throwable);
        }
    }

    private static File installBundledSogouDatabase(String dataDir) {
        File databaseDirectory = new File(dataDir, SOGOU_DATABASE_DIRECTORY);
        File cellDirectory = new File(dataDir, SOGOU_CELL_DIRECTORY);
        File marker = new File(databaseDirectory, SOGOU_MARKER);
        if (marker.isFile()) {
            log("Bundled Sogou database is already installed at "
                    + databaseDirectory.getAbsolutePath());
            return databaseDirectory;
        }
        if (modulePath == null || modulePath.isEmpty()) {
            log("Module path unavailable; bundled Sogou database was not installed");
            return null;
        }

        try (ZipFile moduleApk = new ZipFile(modulePath)) {
            extractAssetDirectory(moduleApk, SOGOU_ASSET_PREFIX, databaseDirectory);
            extractAssetDirectory(moduleApk, SOGOU_CELL_ASSET_PREFIX, cellDirectory);
            try (FileOutputStream output = new FileOutputStream(marker)) {
                output.write("v2\n".getBytes("UTF-8"));
            }
            log("Bundled Sogou database installed at "
                    + databaseDirectory.getAbsolutePath());
            return databaseDirectory;
        } catch (Throwable throwable) {
            logThrowable("Bundled Sogou database installation failed", throwable);
            return null;
        }
    }

    private static void hookSogouDatabasePath(
            ClassLoader classLoader, File databaseDirectory) {
        try {
            String path = databaseDirectory.getAbsolutePath() + File.separator;
            Class<?> globalPathClass =
                    XposedHelpers.findClassIfExists(
                            HONEYBOARD_DATABASE_PATH_CLASS, classLoader);
            if (globalPathClass != null) {
                XposedBridge.hookAllMethods(globalPathClass, "a", resultHook(path));
                XposedBridge.hookAllMethods(globalPathClass, "b", resultHook(path));
                XposedBridge.hookAllMethods(globalPathClass, "c", resultHook(path));
            } else {
                Class<?> chinaPathClass =
                        XposedHelpers.findClass(
                                HONEYBOARD_CHINA_DATABASE_PATH_CLASS, classLoader);
                XposedBridge.hookAllMethods(chinaPathClass, "a", resultHook(path));
                XposedBridge.hookAllMethods(chinaPathClass, "b", resultHook(path));
                XposedBridge.hookAllMethods(chinaPathClass, "e", resultHook(path));
            }
            log("Samsung Keyboard Sogou database path forced to " + path);
        } catch (Throwable throwable) {
            logThrowable("Samsung Keyboard Sogou database path hook failed", throwable);
        }
    }

    private static void extractAssetDirectory(
            ZipFile moduleApk, String prefix, File destination) throws Exception {
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create destination " + destination.getAbsolutePath());
        }
        String canonicalDestination = destination.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[32768];
        Enumeration<? extends ZipEntry> entries = moduleApk.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entry.isDirectory() || !entryName.startsWith(prefix)) {
                continue;
            }
            String relativeName = entryName.substring(prefix.length());
            if (relativeName.isEmpty()) {
                continue;
            }
            File outputFile = new File(destination, relativeName);
            String canonicalOutput = outputFile.getCanonicalPath();
            if (!canonicalOutput.startsWith(canonicalDestination)) {
                throw new SecurityException("Invalid bundled database entry");
            }
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException(
                        "Could not create directory " + parent.getAbsolutePath());
            }
            try (InputStream input =
                            new BufferedInputStream(moduleApk.getInputStream(entry));
                    BufferedOutputStream output =
                            new BufferedOutputStream(new FileOutputStream(outputFile))) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private static void enablePortedXt9Database(ClassLoader classLoader, String dataDir) {
        File portedXt9 = new File(dataDir, "files/oneui85-xt9");
        File hongKongConfig = new File(portedXt9, "zh_hk/config.xml");
        File taiwanConfig = new File(portedXt9, "zh_tw/config.xml");
        File simplifiedConfig = new File(portedXt9, "zh_cn/config.xml");
        if (!hongKongConfig.isFile()
                && !taiwanConfig.isFile()
                && !simplifiedConfig.isFile()) {
            log("No ported XT9 database installed; stock keyboard path remains active");
            return;
        }

        try {
            Class<?> enginePathClass = XposedHelpers.findClass("p651xb.b", classLoader);
            String path = portedXt9.getAbsolutePath() + File.separator;
            XposedHelpers.setStaticObjectField(enginePathClass, "f36784d", path);
            log("Samsung Keyboard XT9 preload path=" + path);
        } catch (Throwable throwable) {
            logThrowable("Ported XT9 path enable failed", throwable);
        }
    }

    private static void installSettingsNowNudgeHook(ClassLoader classLoader) {
        try {
            Class<?> controller =
                    XposedHelpers.findClassIfExists(
                            SETTINGS_NOW_NUDGE_CONTROLLER, classLoader);
            if (controller == null) {
                log("Settings Now Nudge controller not found");
                return;
            }
            XposedBridge.hookAllMethods(controller, "getAvailabilityStatus", resultHook(0));
            log("Settings Now Nudge controller made available");
        } catch (Throwable throwable) {
            logThrowable("Settings Now Nudge hook failed", throwable);
        }
    }

    private static void forceBooleanResult(Class<?> targetClass, String methodName, boolean value) {
        XposedBridge.hookAllMethods(targetClass, methodName, resultHook(value));
    }

    private static XC_MethodHook resultHook(final Object value) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(value);
            }
        };
    }

    private static void forceNotificationAiRunes(ClassLoader classLoader, String stage) {
        try {
            Class<?> rune = XposedHelpers.findClass(RUNE_CLASS, classLoader);
            XposedHelpers.setStaticBooleanField(rune, PRIORITY_FIELD, true);
            XposedHelpers.setStaticBooleanField(rune, SUMMARY_FIELD, true);
            boolean priorityEnabled =
                    XposedHelpers.getStaticBooleanField(rune, PRIORITY_FIELD);
            boolean summaryEnabled =
                    XposedHelpers.getStaticBooleanField(rune, SUMMARY_FIELD);
            log(
                    stage
                            + ": "
                            + PRIORITY_FIELD
                            + "="
                            + priorityEnabled
                            + ", "
                            + SUMMARY_FIELD
                            + "="
                            + summaryEnabled);
        } catch (Throwable throwable) {
            logThrowable("failed during " + stage, throwable);
        }
    }

    private static void logThrowable(String message, Throwable throwable) {
        XposedBridge.log(TAG + ": " + message);
        XposedBridge.log(throwable);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
