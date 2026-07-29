# OneUI 8.5 AI Notifications

An LSPosed module that unlocks Samsung's built-in Priority Notification
Highlights, AI Notification Summaries, and keyboard-inline Now Nudge on
compatible One UI firmware.

This project does not replace `framework.jar`, `services.jar`, SystemUI, or
Settings. It enables code that already exists in Samsung's One UI 8.5 firmware.

## Tested firmware

- One UI 8.5
- Android 16 / API 36
- KernelSU LKM root
- LSPosed API 102

Other firmware revisions may use different class or field names. Always verify
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

## Keyboard Now Nudge

The installed Smart Suggestions and Samsung Keyboard builds already contain the
Now Nudge engine and keyboard presentation code. The module:

- enables the Smart Suggestions Now Nudge gate;
- keeps Ambient Nudge disabled so results are routed to Samsung Keyboard;
- enables the keyboard's existing suggested-replies receiver;
- exposes the stock Settings controller; and
- controls the stock `now_nudge_setting` and `now_nudge_enabled` globals.

Both the global HoneyBoard 5.9.30.97 layout and the China-release
HoneyBoard 5.9.30.97 layout are supported. The China-release build uses a
different obfuscation map, so the module has separate runtime hooks for each
layout.

The Android 17 One UI Home APK is not installed. Its ambient overlay renderer
depends on API 37 framework code and on the new Launcher's internal dependency
graph.

## Chinese prediction engine

The global HoneyBoard APK does not contain the Sogou native engine libraries.
Installing dictionary files alone is therefore insufficient. A compatible
Samsung-signed China or TGY HoneyBoard build must be installed separately.

The module then:

- forces the China/Sogou engine predicate;
- enables the detailed Chinese dictionary feature;
- installs the bundled offline Sogou database inside HoneyBoard's own data
  directory; and
- redirects HoneyBoard's downloaded-database resolver to that app-owned path.

The stock TGY XT9 files are already present on tested firmware and remain on
Samsung's original `/prism/sipdb/Xt9` path. An optional separately installed
XT9 database can also be selected from:

```text
/data/user/0/com.samsung.android.honeyboard/files/oneui85-xt9/
```

## Installation

1. Install `dist/OneUI-8.5-AI-Notifications-v1.6.apk`.
2. Open LSPosed and enable **OneUI 8.5 AI Notifications**.
3. Select **System Framework** for notification features.
4. For Now Nudge, also select **Smart Suggestions**, **Samsung Keyboard**, and
   **Settings**.
5. Open the module settings and enable the desired features.
6. Relaunch the three application processes to load Now Nudge without rebooting.

For the Sogou Chinese engine, install a compatible Samsung-signed
China/TGY HoneyBoard package before relaunching Samsung Keyboard. Keep a copy of
the original HoneyBoard APK for rollback.

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

- Use only the four documented LSPosed scopes.
- Keep a known-good copy of the previously installed APK.
- Do not install framework or SystemUI files from a different device.
- Disable the module from LSPosed if a future firmware update removes or renames
  the target fields.
