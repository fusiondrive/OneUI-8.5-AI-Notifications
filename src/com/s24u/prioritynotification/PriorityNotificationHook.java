package com.s24u.prioritynotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class PriorityNotificationHook implements IXposedHookLoadPackage {
    private static final String TAG = "S24UPriority";
    private static final String ANDROID_PACKAGE = "android";
    private static final String RUNE_CLASS = "com.android.server.notification.NmRune";
    private static final String SERVICE_CLASS =
            "com.android.server.notification.NotificationManagerService";
    private static final String PRIORITY_FIELD = "NM_SUPPORT_AI_NOTIFICATION_PRIORITY";
    private static final String SUMMARY_FIELD = "NM_SUPPORT_AI_NOTIFICATION_SUMMARY";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!ANDROID_PACKAGE.equals(loadPackageParam.packageName)
                || !ANDROID_PACKAGE.equals(loadPackageParam.processName)) {
            return;
        }

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
            XposedBridge.log(TAG + ": failed during " + stage);
            XposedBridge.log(throwable);
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
