package dev.android.dynamicisland.island.source;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.xposed.Diagnostics;
import dev.android.dynamicisland.xposed.XLog;
import java.util.List;

/**
 * Phase 0 proof source: synthetic island item via broadcast.
 * Soft-fail only — never throws into System UI.
 *
 * <pre>
 * adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 1
 * adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 0
 * </pre>
 *
 * Personal / experimental only.
 */
public final class DemoSource {
    public static final String ACTION = "dev.android.dynamicisland.DEMO";
    public static final String KEY = "demo:proof";

    private final Context ctx;
    private final Handler main;
    private final Runnable onChange;
    private volatile boolean show;
    private BroadcastReceiver receiver;

    public DemoSource(Context ctx, Handler main, Runnable onChange) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.onChange = onChange;
    }

    public void start() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION.equals(intent.getAction())) return;
                int flag = intent.getIntExtra("show", -1);
                boolean next = flag == 1 || (flag < 0 && intent.getBooleanExtra("show", false));
                if (flag == 0) next = false;
                if (show == next) return;
                show = next;
                Diagnostics.fact("demo", show ? "shown" : "cleared");
                main.post(onChange);
            }
        };
        try {
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, filter);
            }
            XLog.i("DemoSource registered " + ACTION);
        } catch (Throwable t) {
            XLog.once("DemoSource start", t);
            receiver = null;
        }
    }

    public void stop() {
        if (receiver == null) return;
        try {
            ctx.unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
        receiver = null;
        show = false;
    }

    public void collect(List<IslandItem> items) {
        if (!show) return;
        try {
            IslandItem it = new IslandItem(KEY);
            it.kind = IslandItem.Kind.EVENT;
            it.iconIsImage = false;
            it.packageName = "dev.android.dynamicisland";
            it.title = "Demo";
            it.subtitle = "Phase 0 proof · personal";
            it.icon = GlyphIcon.of(GlyphIcon.Kind.HOTSPOT);
            it.accent = 0xFF3E91FF;
            it.trailing = IslandItem.Trailing.TEXT;
            it.trailingText = "DEMO";
            it.postTime = SystemClock.uptimeMillis();
            items.add(it);
        } catch (Throwable t) {
            XLog.once("DemoSource collect", t);
        }
    }

    public boolean isShowing() {
        return show;
    }
}
