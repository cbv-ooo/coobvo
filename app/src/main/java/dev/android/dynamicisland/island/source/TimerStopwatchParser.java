package dev.android.dynamicisland.island.source;

import android.app.Notification;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies timer/stopwatch notifications and extracts a useful clock value.
 * This is a fresh Java implementation of the notification behaviours identified in the
 * supplied Smart Island reference; it intentionally does not depend on that app's services.
 */
final class TimerStopwatchParser {
    private static final Set<String> CLOCK_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.deskclock", "com.sec.android.app.clockpackage",
            "com.android.deskclock", "com.miui.deskclock", "com.oneplus.deskclock",
            "com.coloros.alarmclock", "com.oplus.alarmclock", "com.android.bbkclock",
            "com.vivo.clock", "com.huawei.deskclock", "com.hihonor.deskclock",
            "com.motorola.blur.alarmclock", "com.motorola.timeweatherwidget",
            "com.sec.android.deskclock", "com.asus.deskclock", "com.lenovo.deskclock",
            "com.sonyericsson.organizer", "com.htc.android.worldclock",
            "com.timer.stopwatch", "com.hybrid.stopwatch", "com.sportstracklive.stopwatch",
            "com.jee.timer", "com.clover.timer"));
    private static final String[] TIMER_WORDS = {
            "timer", "countdown", "time's up", "times up", "time is up",
            "timer paused", "timer running", "timer expired", "timer finished",
            "remaining", "sec remaining", "min remaining"
    };
    private static final String[] ALARM_WORDS = {
            "alarm", "upcoming alarm", "snooze", "snoozed", "wake up", "wake-up"
    };
    private static final String[] RESUME_WORDS = {
            "resume", "start", "play", "continue", "unpause"
    };
    private static final String[] PAUSED_WORDS = {
            "paused", "pause", "en pause", "pausado", "angehalten", "已暂停", "一時停止中"
    };
    private static final Pattern CLOCK = Pattern.compile(
            "\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,2}))?\\b");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:h|hr|hrs|hours?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*(?:m|min|mins|minutes?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECONDS = Pattern.compile("(\\d+)\\s*(?:s|sec|secs|seconds?)\\b", Pattern.CASE_INSENSITIVE);

    private TimerStopwatchParser() { }

    static boolean isClockPackage(String packageName) {
        String pkg = lower(packageName);
        return CLOCK_PACKAGES.contains(pkg) || pkg.contains(".clock") || pkg.contains(".timer");
    }

    static boolean isAlarm(Notification notification, String packageName) {
        if (notification == null) return false;
        String text = fullText(notification);
        String[] actions = actionLabels(notification);
        if (containsAny(text, new String[]{"snooze", "snoozed", "rappeler", "posponer", "schlummern"})) return true;
        if (containsAny(join(actions), new String[]{"snooze", "snoozed"})) return true;
        if (text.contains("upcoming alarm") || text.contains("alarm firing") || text.contains("alarm ringing")) return true;
        boolean timerCue = containsAny(text, TIMER_WORDS)
                || containsAny(join(actions), new String[]{"+1", "timer", "add 1", "pause", "resume", "reset", "lap"});
        String category = lower(notification.category);
        return (Notification.CATEGORY_ALARM.equals(category) || containsAny(text, ALARM_WORDS))
                && !timerCue;
    }

    static boolean isTimer(Notification notification, String packageName) {
        if (notification == null || isAlarm(notification, packageName)
                || isStopwatch(notification, packageName)) return false;
        Bundle extras = notification.extras;
        String channel = lower(notification.getChannelId());
        String text = fullText(notification);
        String actions = join(actionLabels(notification));
        if (chronometerCountDown(extras) || channel.contains("timer")) return true;
        if (containsAny(actions, new String[]{"+1", "timer", "add 1", "reset timer"})) return true;
        boolean clockApp = isClockPackage(packageName);
        boolean timeLike = hasClockText(text) || containsAny(text, TIMER_WORDS);
        if (clockApp && (timeLike || isTimerPaused(notification))) return true;
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean message = Notification.CATEGORY_MESSAGE.equals(notification.category)
                || Notification.CATEGORY_EMAIL.equals(notification.category)
                || Notification.CATEGORY_SOCIAL.equals(notification.category);
        return ongoing && !message && containsAny(text, TIMER_WORDS) && hasClockText(text);
    }

    static boolean isStopwatch(Notification notification, String packageName) {
        if (notification == null || isAlarm(notification, packageName)) return false;
        String category = lower(notification.category);
        String channel = lower(notification.getChannelId());
        if ("stopwatch".equals(category) || "category_stopwatch".equals(category) || channel.contains("stopwatch")) {
            return true;
        }
        boolean clockApp = isClockPackage(packageName);
        boolean countDown = chronometerCountDown(notification.extras);
        boolean chronometer = notification.extras != null
                && (notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
                || notification.extras.getLong("android.chronometerBase", 0L) > 0L);
        String text = fullText(notification);
        String actions = join(actionLabels(notification));
        boolean lapAction = containsAny(actions, new String[]{"lap", "split", "vuelta", "tour", "runde"});
        boolean stopwatchWord = containsAny(text, new String[]{"stopwatch", "elapsed"});
        boolean liveCountUp = chronometer && !countDown;
        boolean trusted = clockApp || liveCountUp;
        return trusted && !countDown && (lapAction || stopwatchWord || (clockApp && chronometer));
    }

    static boolean isTimerPaused(Notification notification) {
        String text = fullText(notification);
        String actions = join(actionLabels(notification));
        boolean hasResume = containsAny(actions, RESUME_WORDS);
        boolean hasPause = actions.contains("pause");
        return hasResume || (containsAny(text, PAUSED_WORDS) && !hasPause);
    }

    static boolean isStopwatchPaused(Notification notification) {
        String text = fullText(notification);
        String actions = join(actionLabels(notification));
        boolean hasResume = containsAny(actions, RESUME_WORDS);
        boolean hasPause = actions.contains("pause");
        return hasResume || ((text.contains("paused") || text.contains("pause")) && !hasPause);
    }

    static Long timerRemainingSeconds(Notification notification) {
        if (notification == null) return null;
        Bundle extras = notification.extras;
        if (!isTimerPaused(notification) && chronometerCountDown(extras) && extras != null) {
            long base = extras.getLong("android.chronometerBase", 0L);
            if (base > 0L) {
                long delta = base - SystemClock.elapsedRealtime();
                if (delta > 0L) return (delta + 500L) / 1000L;
            }
        }
        if (!isTimerPaused(notification) && notification.when > System.currentTimeMillis()) {
            return (notification.when - System.currentTimeMillis() + 500L) / 1000L;
        }
        return parseTimeFields(notification);
    }

    static Long stopwatchElapsedSeconds(Notification notification, String packageName) {
        if (notification == null) return null;
        Bundle extras = notification.extras;
        if (!isStopwatchPaused(notification) && extras != null && !chronometerCountDown(extras)) {
            long base = extras.getLong("android.chronometerBase", 0L);
            if (base > 0L) {
                long delta = SystemClock.elapsedRealtime() - base;
                if (delta >= 0L) return delta / 1000L;
            }
        }
        Long parsed = parseTimeFields(notification);
        if (parsed != null) return parsed;
        // Some clock apps expose the stopwatch start as Notification.when without a
        // Chronometer RemoteViews child. Only trust that field for known clock packages.
        if (!isStopwatchPaused(notification) && isClockPackage(packageName)
                && notification.when > 0L && notification.when <= System.currentTimeMillis()) {
            return (System.currentTimeMillis() - notification.when) / 1000L;
        }
        return null;
    }

    private static Long parseTimeFields(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return null;
        String[] keys = {Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT};
        for (String key : keys) {
            CharSequence value = extras.getCharSequence(key);
            if (value == null || value.length() == 0) continue;
            Long parsed = parseTime(value.toString());
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static Long parseTime(String text) {
        Matcher matcher = CLOCK.matcher(text);
        if (matcher.find()) {
            long hours = matcher.group(1) == null ? 0L : parseLong(matcher.group(1));
            long minutes = parseLong(matcher.group(2));
            long seconds = parseLong(matcher.group(3));
            return hours * 3600L + minutes * 60L + seconds;
        }
        long hours = matchNumber(HOURS, text), minutes = matchNumber(MINUTES, text), seconds = matchNumber(SECONDS, text);
        return hours + minutes + seconds == 0L ? null : hours * 3600L + minutes * 60L + seconds;
    }

    private static long matchNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? parseLong(matcher.group(1)) : 0L;
    }

    private static boolean chronometerCountDown(Bundle extras) {
        return extras != null && (extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
                || extras.getBoolean("android.chronometerCountDown", false));
    }

    private static boolean hasClockText(String text) {
        return text != null && CLOCK.matcher(text).find();
    }

    private static String fullText(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";
        StringBuilder out = new StringBuilder(160);
        append(out, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(out, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(out, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        append(out, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        append(out, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        append(out, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence ticker = notification.tickerText;
        append(out, ticker);
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(out, line);
        return out.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String[] actionLabels(Notification notification) {
        Notification.Action[] actions = notification.actions;
        if (actions == null || actions.length == 0) return new String[0];
        String[] out = new String[actions.length];
        int count = 0;
        for (Notification.Action action : actions) {
            if (action != null && action.title != null) out[count++] = action.title.toString().toLowerCase(java.util.Locale.ROOT);
        }
        return count == out.length ? out : Arrays.copyOf(out, count);
    }

    private static void append(StringBuilder out, CharSequence value) {
        if (value == null || value.length() == 0) return;
        if (out.length() > 0) out.append(' ');
        out.append(value);
    }

    private static String join(String[] values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) if (value != null) {
            if (out.length() > 0) out.append(' ');
            out.append(value);
        }
        return out.toString();
    }

    private static boolean containsAny(String value, String[] needles) {
        if (value == null || value.isEmpty()) return false;
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (RuntimeException ignored) { return 0L; }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
