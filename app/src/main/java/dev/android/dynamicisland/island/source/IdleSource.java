package dev.android.dynamicisland.island.source;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateFormat;
import dev.android.dynamicisland.island.IslandText;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Smart Island-style idle rotation when the island has nothing else to show.
 * Clock, battery, next alarm, DND, net speed (TrafficStats only - no INTERNET).
 * Soft-fail only. Personal / experimental.
 *
 * Gate: ModuleSettings.alwaysVisible() and empty gather list.
 */
public final class IdleSource {
    private static final long ROTATE_MS = 6_000;
    private static final long SAMPLE_MS = 2_000;
    private static final long NET_THRESHOLD_BPS = 50 * 1024;

    private final Context ctx;
    private final Handler main;
    private final Runnable onChange;
    private final NetSpeedTracker net = new NetSpeedTracker();
    private int slot;
    private long slotStarted;
    private boolean started;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            try {
                net.sample();
                long now = SystemClock.uptimeMillis();
                if (now - slotStarted >= rotateMs()) {
                    slot++;
                    slotStarted = now;
                }
                onChange.run();
            } catch (Throwable t) {
                XLog.once("IdleSource.tick", t);
            }
            main.postDelayed(this, ModuleSettings.lowPerformance() ? SAMPLE_MS * 2 : SAMPLE_MS);
        }
    };

    public IdleSource(Context ctx, Handler main, Runnable onChange) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.onChange = onChange;
    }

    public void start() {
        if (started) return;
        started = true;
        slotStarted = SystemClock.uptimeMillis();
        net.sample();
        main.removeCallbacks(tick);
        main.postDelayed(tick, SAMPLE_MS);
    }

    public void stop() {
        started = false;
        main.removeCallbacks(tick);
    }

    private long rotateMs() {
        return ModuleSettings.lowPerformance() ? ROTATE_MS * 2 : ROTATE_MS;
    }

    /** Only when gather is still empty and always-visible is on. */
    public void collectIfIdle(List<IslandItem> items) {
        if (items == null || !items.isEmpty()) return;
        if (!ModuleSettings.alwaysVisible()) return;
        try {
            List<IslandItem> candidates = buildCandidates();
            if (candidates.isEmpty()) return;
            int i = Math.floorMod(slot, candidates.size());
            items.add(candidates.get(i));
        } catch (Throwable t) {
            XLog.once("IdleSource.collect", t);
        }
    }

    private List<IslandItem> buildCandidates() {
        List<IslandItem> out = new ArrayList<>(5);
        out.add(clockItem());
        out.add(batteryItem());
        IslandItem alarm = alarmItem();
        if (alarm != null) out.add(alarm);
        IslandItem dnd = dndItem();
        if (dnd != null) out.add(dnd);
        IslandItem speed = netItem();
        if (speed != null) out.add(speed);
        return out;
    }

    private IslandItem clockItem() {
        IslandItem it = base("idle:clock");
        Calendar cal = Calendar.getInstance();
        String time = DateFormat.getTimeFormat(ctx).format(cal.getTime());
        it.title = time;
        it.subtitle = DateFormat.format("EEE, MMM d", cal).toString();
        it.icon = GlyphIcon.of(GlyphIcon.Kind.BELL);
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = time;
        it.accent = 0xFF8E8E93;
        return it;
    }

    private IslandItem batteryItem() {
        IslandItem it = base("idle:battery");
        int level = 50;
        boolean charging = false;
        try {
            Intent bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (lvl >= 0 && scale > 0) level = Math.round(lvl * 100f / scale);
                int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Throwable ignored) {
        }
        it.title = level + "%";
        it.subtitle = charging ? IslandText.get(ctx, "island_charging") : "Battery";
        it.icon = GlyphIcon.battery(level, charging);
        it.eventLayout = IslandItem.EventLayout.BATTERY;
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = level + "%";
        it.accent = 0xFF30D158;
        return it;
    }

    private IslandItem alarmItem() {
        try {
            AlarmManager am = ctx.getSystemService(AlarmManager.class);
            if (am == null) return null;
            AlarmManager.AlarmClockInfo next = am.getNextAlarmClock();
            if (next == null) return null;
            IslandItem it = base("idle:alarm");
            String when = DateFormat.getTimeFormat(ctx).format(next.getTriggerTime());
            it.title = when;
            it.subtitle = "Next alarm";
            it.icon = GlyphIcon.of(GlyphIcon.Kind.BELL);
            it.trailing = IslandItem.Trailing.TEXT;
            it.trailingText = when;
            it.accent = 0xFFFF9F0A;
            return it;
        } catch (Throwable t) {
            return null;
        }
    }

    private IslandItem dndItem() {
        try {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return null;
            int filter = nm.getCurrentInterruptionFilter();
            if (filter == NotificationManager.INTERRUPTION_FILTER_ALL
                    || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                return null;
            }
            IslandItem it = base("idle:dnd");
            it.title = "Do Not Disturb";
            it.subtitle = filterLabel(filter);
            it.icon = GlyphIcon.of(GlyphIcon.Kind.MOON);
            it.trailing = IslandItem.Trailing.TEXT;
            it.trailingText = "DND";
            it.accent = 0xFF5E5CE6;
            return it;
        } catch (Throwable t) {
            return null;
        }
    }

    private String filterLabel(int filter) {
        switch (filter) {
            case NotificationManager.INTERRUPTION_FILTER_NONE:
                return "Total silence";
            case NotificationManager.INTERRUPTION_FILTER_ALARMS:
                return "Alarms only";
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
                return "Priority only";
            default:
                return "Quiet";
        }
    }

    private IslandItem netItem() {
        long bps = net.busierBps();
        if (bps < NET_THRESHOLD_BPS) return null;
        IslandItem it = base("idle:net");
        String label = NetSpeedTracker.format(bps);
        it.title = label;
        it.subtitle = net.rxDominant() ? "download" : "upload";
        it.icon = GlyphIcon.of(GlyphIcon.Kind.DOWNLOAD);
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = label;
        it.accent = 0xFF64D2FF;
        return it;
    }

    private IslandItem base(String key) {
        IslandItem it = new IslandItem(key);
        it.kind = IslandItem.Kind.EVENT;
        it.eventLayout = IslandItem.EventLayout.STATUS;
        it.iconIsImage = false;
        it.packageName = "dev.android.dynamicisland";
        it.postTime = SystemClock.uptimeMillis();
        return it;
    }
}
