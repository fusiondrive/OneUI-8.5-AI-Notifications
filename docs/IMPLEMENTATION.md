# Implementation notes

## Root cause

The tested One UI firmware contains the complete Priority Notification and
Notification Summary implementations. The server-side feature gates are
disabled because the target device codename is not included in Samsung's
allowlist.

The stock allowlist accepts device names beginning with:

```text
m1q
m2q
m3q
m1s
m2s
m3s
```

The AI version gate is already satisfied:

```text
SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION=20261
```

## Priority Notification chain

The stock chain is:

```text
NotificationManagerService
  -> PriorityNotiScenarioManager
  -> scenario extractors
  -> Notification.semFlags |= 0x20000
  -> Notification.semScenarioType
  -> SystemUI SemHighlightsCoordinator
```

The SystemUI implementation is already enabled by the AI version and does not
require a SystemUI hook.

## Notification Summary chain

The stock server contains:

```text
NotiSummaryManager
NotiSummarizer
NotiSummaryInput
NotiSummaryAlarmScheduler
NotiSumamryLimiter
```

It also exposes the per-application Binder methods:

```text
setAllowAINotificationSummary
isAINotificationSummaryAllowed
getAllowedSummaryAppList
```

The feature observes:

```text
noti_intelligence_summarize_content
```

When enabled, it registers screen, battery, and alarm receivers. A screen-off
event schedules the first evaluation after ten seconds. A private conversation
must be unchanged for 180 seconds; a group conversation must be unchanged for
540 seconds.

The summarizer performs language identification and rejects input whose language
does not match the current system locale.

## Verified runtime evidence

The tested device produced all of the following:

```text
Summary noti setting Update : userId = 0 enabled = true
NotiSummaryManager:WakeUpReceiver: register start
NotiSummaryManager:ScreenStateReceiver: register start
NotiSummaryManager:BatteryStateReceiver: register start
NotiSummaryManager: summarizeIfNecessary
```

Telegram was accepted as an allowed messaging application. A test conversation
with 395 characters and 82 words entered the summary pipeline, then correctly
waited because only 74 seconds had elapsed since the final update.

## Why the hook runs early

`NotificationManagerService` creates its AI managers conditionally during
construction. Changing a Secure setting after initialization cannot create a
manager that was skipped. The module therefore sets both `NmRune` fields before
every `NotificationManagerService` constructor.
# Now Nudge and Chinese engine implementation

Version 1.8 provides a narrowly scoped keyboard-inline Now Nudge path:

- `com.samsung.android.smartsuggestions`: forces
  `Rune.getSUPPORT_NOW_NUDGE()` to true and keeps
  `Rune.getSUPPORT_AMBIENT_NUDGE()` false.
- `com.samsung.android.honeyboard`: enables the receiver-registration flags
  already present in Samsung Keyboard 5.9.30.97.
- `com.android.settings`: returns available from the existing
  `NowNudgesGalaxyAIController`.

The module does not install the API 37 Launcher from the source firmware.

HoneyBoard 5.9.30.97 has two relevant code layouts:

- The global build uses `Y8.a`, `k7.g`, and `lj.c`.
- The China-release build uses `sj.d`, `md.c`, `l30.a`, `g40.g`, and `xo.b`.

The module detects the active layout at runtime. Global-build static rune
changes are delayed until after `HoneyBoardApplication.attachBaseContext`
finishes because initializing `Y8.a` before Koin starts crashes the keyboard.

The global APK has the Java Sogou integration but does not contain the Sogou
native libraries. A compatible Samsung-signed China/TGY HoneyBoard APK supplies
those libraries. The original TGY database archive is staged at:

```text
/data/user/0/com.samsung.android.honeyboard/files/oneui85-sogou-preload-v1/sogou_db.zip
```

The `xo.b.f()` Sogou preload resolver is redirected to the parent app-owned
directory. The `o50.e` resolver is not modified because it belongs to the
Japanese Omron engine. This avoids
root-created app-data directories, which cannot be repaired reliably under
KernelSU LKM SELinux categories.

On a non-China CSC, the China-release APK applies an additional preference
visibility filter even after the Sogou engine is enabled. Version 1.8 overrides
that filter only for the Chinese input category and its Sogou, detailed
dictionary, rare-word, Traditional Chinese, fuzzy-Pinyin, and Shuangpin
preferences. Other HoneyBoard settings retain their stock visibility rules.

The final engine name comes from `l30.a.a(int)`. The module returns `SOGOU`
only for Simplified Chinese language ID `4653073`; every other language keeps
Samsung's original selector result. The Sogou wrapper constructor checks
`sj.d.D7`, so that field is enabled immediately before `g40.g` construction.
Writing it during package load is unsafe because it initializes `sj.d` before
HoneyBoard starts Koin.

If a separately extracted XT9 payload exists under Samsung Keyboard's files
directory, the global HoneyBoard hook changes only its cached XT9 preload path
to that directory. The stock preload path remains unchanged when the payload
is absent.
