# Implementation notes

## Root cause

The tested S24 Ultra firmware contains the complete Priority Notification and
Notification Summary implementations. The server-side feature gates are
disabled because `e3q` is not included in Samsung's device allowlist.

The stock allowlist accepts device names beginning with:

```text
m1q
m2q
m3q
m1s
m2s
m3s
```

The target device reports:

```text
ro.product.vendor.device=e3q
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

