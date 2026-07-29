# OneUI 8.5 AI Notifications

An LSPosed module that unlocks Samsung's built-in Priority Notification
Highlights and AI Notification Summaries from S26 series to the S24 Ultra.

This project does not replace `framework.jar`, `services.jar`, SystemUI, or
Settings. It enables code that already exists in Samsung's One UI 8.5 firmware.

## Tested device

- Galaxy S24 Ultra `SM-S928U1`
- Device codename `e3q`
- One UI 8.5
- Android 16 / API 36
- Build `S928U1UES6DZF2`
- KernelSU LKM root
- LSPosed API 102

Other firmware revisions may use different class or field names. Verify your
stock `services.jar` before installing on another build.

## What it changes

Samsung limits both features to a device allowlist in:

```text
com.android.server.notification.NmRune
```

The module sets these fields before `NotificationManagerService` is
constructed:

```text
NM_SUPPORT_AI_NOTIFICATION_PRIORITY = true
NM_SUPPORT_AI_NOTIFICATION_SUMMARY = true
```

The companion activity controls the original Samsung Secure settings through
root:

```text
noti_intelligence_priority_content
noti_intelligence_summarize_content
```

No Samsung APK or framework file is patched.

## Installation

1. Install `dist/S24U-AI-Notifications-v1.1.apk`.
2. Open LSPosed and enable **S24U AI Notifications**.
3. Select only **System Framework** as the scope.
4. Open the module settings and enable the desired features.
5. Let the hook load during the next normal system initialization.

Do not force a reboot on devices that use a temporary or difficult-to-reactivate
LKM jailbreak. Arrange system initialization according to the requirements of
your root environment.

## Notification Summary behavior

The summary engine is intentionally conservative. A visible summary normally
requires all of the following:

- Samsung account signed in
- A supported messaging notification
- At least 40 words on non-Korean system locales
- At least 100 characters on Korean system locales
- Notification language matching the current system language
- Screen off
- Battery above 30 percent
- Power saving mode disabled
- Three minutes since the last update for a private conversation
- Nine minutes since the last update for a group conversation

Every new message resets the freshness timer. Turning the screen on cancels a
pending summary task.

Runtime diagnostics:

```sh
adb shell settings get secure noti_intelligence_priority_content
adb shell settings get secure noti_intelligence_summarize_content
adb shell logcat -d | grep -E \
  "Summary noti setting Update|NotiSummaryManager|Priority noti setting Update"
```

Useful successful states include:

```text
Summary noti setting Update : mIsSummaryNotiEnabledForAnyUsers = true
NotiSummaryManager:ScreenStateReceiver: register start
NotiSummaryManager: summarizeIfNecessary
NotiSummaryManager: summarize <notification-key>
```

## Priority Highlights behavior

Priority Highlights uses Samsung's stock scenario classifiers, including:

- Important people
- Family safety
- Traffic and transport
- SmartThings
- Software updates
- Work-location events

It does not mark every notification as important. SystemUI keeps a small
Highlights section and only promotes notifications that match a stock scenario.

## Building

Requirements:

- JDK with `javac` and `keytool`
- Android SDK platform 36
- Android build-tools 36.1.0
- Legacy Xposed API JAR

Set the environment and run:

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
export XPOSED_API_JAR=/path/to/api-82.jar
sh build.sh
```

The local signing key is generated as `debug.keystore` and is excluded from
Git. Published upgrades must be signed with the same key as the installed APK.

## Safety

- Scope the module only to System Framework.
- Keep a known-good copy of the previously installed APK.
- Do not install S23 framework or SystemUI files on an S24 Ultra.
- Disable the module from LSPosed if a future firmware update removes or renames
  the target fields.

