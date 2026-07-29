package com.s24u.prioritynotification;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class MainActivity extends Activity {
    private static final String PRIORITY_SETTING = "noti_intelligence_priority_content";
    private static final String SUMMARY_SETTING = "noti_intelligence_summarize_content";
    private static final String NOW_NUDGE_SETTING = "now_nudge_setting";
    private static final String NOW_NUDGE_ENABLED = "now_nudge_enabled";

    private Switch prioritySwitch;
    private Switch summarySwitch;
    private Switch nowNudgeSwitch;
    private TextView status;
    private boolean updatingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Priority notifications");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText(
                "Unlock Samsung’s built-in Priority Highlights and on-device notification "
                        + "summaries, plus keyboard-inline Now Nudge. Select the required "
                        + "LSPosed scopes before enabling each feature.");
        description.setTextSize(16);
        description.setPadding(0, dp(12), 0, dp(22));
        root.addView(description, matchWrap());

        prioritySwitch = new Switch(this);
        prioritySwitch.setText("Enable Priority Notification Highlights");
        prioritySwitch.setTextSize(17);
        prioritySwitch.setPadding(0, dp(10), 0, dp(10));
        root.addView(prioritySwitch, matchWrap());

        summarySwitch = new Switch(this);
        summarySwitch.setText("Enable AI Notification Summaries");
        summarySwitch.setTextSize(17);
        summarySwitch.setPadding(0, dp(10), 0, dp(10));
        root.addView(summarySwitch, matchWrap());

        nowNudgeSwitch = new Switch(this);
        nowNudgeSwitch.setText("Enable Keyboard Now Nudge");
        nowNudgeSwitch.setTextSize(17);
        nowNudgeSwitch.setPadding(0, dp(10), 0, dp(10));
        root.addView(nowNudgeSwitch, matchWrap());

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, dp(18));
        root.addView(status, matchWrap());

        Button refresh = new Button(this);
        refresh.setText("Refresh status");
        refresh.setGravity(Gravity.CENTER);
        root.addView(refresh, matchWrap());

        prioritySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingUi) {
                writeSetting(PRIORITY_SETTING, checked);
            }
        });
        summarySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingUi) {
                writeSetting(SUMMARY_SETTING, checked);
            }
        });
        nowNudgeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingUi) {
                writeNowNudgeSettings(checked);
            }
        });
        refresh.setOnClickListener(view -> refreshStatus());

        setContentView(root);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        status.setText("Reading secure setting through root…");
        new Thread(() -> {
            CommandResult priority = runRoot("settings get secure " + PRIORITY_SETTING);
            CommandResult summary = runRoot("settings get secure " + SUMMARY_SETTING);
            CommandResult nowNudge = runRoot("settings get global " + NOW_NUDGE_ENABLED);
            CommandResult nowNudgeSetting =
                    runRoot("settings get global " + NOW_NUDGE_SETTING);
            boolean priorityEnabled = "1".equals(priority.output.trim());
            boolean summaryEnabled = "1".equals(summary.output.trim());
            boolean nowNudgeEnabled =
                    "1".equals(nowNudge.output.trim())
                            && "1".equals(nowNudgeSetting.output.trim());
            runOnUiThread(
                    () ->
                            updateUi(
                                    priorityEnabled,
                                    summaryEnabled,
                                    nowNudgeEnabled,
                                    priority,
                                    summary,
                                    nowNudge,
                                    nowNudgeSetting));
        }, "priority-setting-read").start();
    }

    private void writeSetting(String setting, boolean enabled) {
        prioritySwitch.setEnabled(false);
        summarySwitch.setEnabled(false);
        status.setText("Applying setting through root…");
        new Thread(() -> {
            CommandResult write =
                    runRoot("settings put secure " + setting + " " + (enabled ? "1" : "0"));
            CommandResult priority = runRoot("settings get secure " + PRIORITY_SETTING);
            CommandResult summary = runRoot("settings get secure " + SUMMARY_SETTING);
            CommandResult nowNudge = runRoot("settings get global " + NOW_NUDGE_ENABLED);
            CommandResult nowNudgeSetting =
                    runRoot("settings get global " + NOW_NUDGE_SETTING);
            boolean priorityEnabled = "1".equals(priority.output.trim());
            boolean summaryEnabled = "1".equals(summary.output.trim());
            boolean nowNudgeEnabled =
                    "1".equals(nowNudge.output.trim())
                            && "1".equals(nowNudgeSetting.output.trim());
            boolean actual = setting.equals(PRIORITY_SETTING) ? priorityEnabled : summaryEnabled;
            runOnUiThread(() -> {
                updateUi(
                        priorityEnabled,
                        summaryEnabled,
                        nowNudgeEnabled,
                        priority,
                        summary,
                        nowNudge,
                        nowNudgeSetting);
                if (!write.success || actual != enabled) {
                    Toast.makeText(
                                    this,
                                    "Could not change the setting. Check KernelSU root access.",
                                    Toast.LENGTH_LONG)
                            .show();
                }
            });
        }, "priority-setting-write").start();
    }

    private void writeNowNudgeSettings(boolean enabled) {
        setSwitchesEnabled(false);
        status.setText("Applying Now Nudge settings through root…");
        new Thread(() -> {
            String value = enabled ? "1" : "0";
            CommandResult writeEnabled =
                    runRoot("settings put global " + NOW_NUDGE_ENABLED + " " + value);
            CommandResult writeSetting =
                    runRoot("settings put global " + NOW_NUDGE_SETTING + " " + value);
            CommandResult priority = runRoot("settings get secure " + PRIORITY_SETTING);
            CommandResult summary = runRoot("settings get secure " + SUMMARY_SETTING);
            CommandResult nowNudge = runRoot("settings get global " + NOW_NUDGE_ENABLED);
            CommandResult nowNudgeSetting =
                    runRoot("settings get global " + NOW_NUDGE_SETTING);
            boolean priorityEnabled = "1".equals(priority.output.trim());
            boolean summaryEnabled = "1".equals(summary.output.trim());
            boolean nowNudgeEnabled =
                    "1".equals(nowNudge.output.trim())
                            && "1".equals(nowNudgeSetting.output.trim());
            runOnUiThread(() -> {
                updateUi(
                        priorityEnabled,
                        summaryEnabled,
                        nowNudgeEnabled,
                        priority,
                        summary,
                        nowNudge,
                        nowNudgeSetting);
                if (!writeEnabled.success
                        || !writeSetting.success
                        || nowNudgeEnabled != enabled) {
                    Toast.makeText(
                                    this,
                                    "Could not change Now Nudge. Check KernelSU root access.",
                                    Toast.LENGTH_LONG)
                            .show();
                }
            });
        }, "now-nudge-setting-write").start();
    }

    private void updateUi(
            boolean priorityEnabled,
            boolean summaryEnabled,
            boolean nowNudgeEnabled,
            CommandResult priorityResult,
            CommandResult summaryResult,
            CommandResult nowNudgeResult,
            CommandResult nowNudgeSettingResult) {
        updatingUi = true;
        prioritySwitch.setChecked(priorityEnabled);
        summarySwitch.setChecked(summaryEnabled);
        nowNudgeSwitch.setChecked(nowNudgeEnabled);
        prioritySwitch.setEnabled(priorityResult.success);
        summarySwitch.setEnabled(summaryResult.success);
        nowNudgeSwitch.setEnabled(
                nowNudgeResult.success && nowNudgeSettingResult.success);
        updatingUi = false;

        if (priorityResult.success
                && summaryResult.success
                && nowNudgeResult.success
                && nowNudgeSettingResult.success) {
            status.setText(
                    "Priority Highlights: "
                            + (priorityEnabled ? "ON" : "OFF")
                            + "\nNotification Summaries: "
                            + (summaryEnabled ? "ON" : "OFF")
                            + "\nKeyboard Now Nudge: "
                            + (nowNudgeEnabled ? "ON" : "OFF")
                            + "\nNow Nudge hooks load when Smart Suggestions and Samsung "
                            + "Keyboard are relaunched.");
        } else {
            status.setText(
                    "Root command failed:\n"
                            + priorityResult.output
                            + summaryResult.output
                            + nowNudgeResult.output
                            + nowNudgeSettingResult.output);
        }
    }

    private void setSwitchesEnabled(boolean enabled) {
        prioritySwitch.setEnabled(enabled);
        summarySwitch.setEnabled(enabled);
        nowNudgeSwitch.setEnabled(enabled);
    }

    private static CommandResult runRoot(String command) {
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            exitCode = process.waitFor();
        } catch (Throwable throwable) {
            output.append(throwable.getClass().getSimpleName())
                    .append(": ")
                    .append(throwable.getMessage());
        }
        return new CommandResult(exitCode == 0, output.toString());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CommandResult {
        final boolean success;
        final String output;

        CommandResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }
}
