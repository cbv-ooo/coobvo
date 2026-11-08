package dev.android.dynamicisland.island.source;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import de.robv.android.xposed.XSharedPreferences;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gesture-safe quick-launch: pinned packages as an island card with actions.
 * Does not change TouchController (long-press hide, swipe up/down, horizontal drag stay intact).
 * Open path is the normal card expand + action tap / content tap.
 *
 * Prefs (module settings file, personal experimental):
 *   quick_launch (boolean, default false)
 *   quick_launch_apps (StringSet of package names)
 *
 * Soft-fail only.
 */
public final class QuickLaunchSource {
    public static final String KEY_ENABLED = "quick_launch";
    public static final String KEY_APPS = "quick_launch_apps";
    public static final String ITEM_KEY = "quick:launch";

    private final Context ctx;
    private final Handler main;
    private final PackageManager pm;

    public QuickLaunchSource(Context ctx, Handler main, Runnable onChange) {
        this.ctx = ctx.getApplicationContext();
        this.main = main;
        this.pm = this.ctx.getPackageManager();
    }

    public void start() {}

    public void collect(List<IslandItem> items) {
        if (items == null) return;
        try {
            if (!enabled()) return;
            List<String> pkgs = pinnedPackages();
            if (pkgs.isEmpty()) return;
            if (hasBlockingContent(items)) return;
            items.removeIf(it -> it != null && it.key != null && it.key.startsWith("idle:"));
            IslandItem card = build(pkgs);
            if (card != null) items.add(card);
        } catch (Throwable t) {
            XLog.once("QuickLaunchSource.collect", t);
        }
    }

    private boolean enabled() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(ModuleSettings.PACKAGE, ModuleSettings.FILE);
            prefs.reload();
            return prefs.getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private List<String> pinnedPackages() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(ModuleSettings.PACKAGE, ModuleSettings.FILE);
            prefs.reload();
            Set<String> set = prefs.getStringSet(KEY_APPS, null);
            if (set == null || set.isEmpty()) return Collections.emptyList();
            List<String> out = new ArrayList<>(set.size());
            for (String p : set) {
                if (p != null && !p.isEmpty()) out.add(p);
            }
            Collections.sort(out);
            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /** Live content must not be displaced. Idle is OK to replace. */
    private static boolean hasBlockingContent(List<IslandItem> items) {
        for (IslandItem it : items) {
            if (it == null || it.key == null) continue;
            if (it.key.startsWith("idle:")) continue;
            if (it.key.startsWith("preview:")) continue;
            if (ITEM_KEY.equals(it.key)) continue;
            return true;
        }
        return false;
    }

    private IslandItem build(List<String> pkgs) {
        IslandItem it = new IslandItem(ITEM_KEY);
        it.kind = IslandItem.Kind.EVENT;
        it.eventLayout = IslandItem.EventLayout.STATUS;
        it.iconIsImage = false;
        it.title = "Apps";
        it.subtitle = pkgs.size() == 1 ? labelOf(pkgs.get(0)).toString() : (pkgs.size() + " pinned");
        it.icon = GlyphIcon.of(GlyphIcon.Kind.HOTSPOT);
        it.accent = 0xFF3E91FF;
        it.postTime = SystemClock.uptimeMillis();
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = "Open";
        it.packageName = pkgs.get(0);

        int limit = Math.min(pkgs.size(), 4);
        for (int i = 0; i < limit; i++) {
            String pkg = pkgs.get(i);
            CharSequence title = labelOf(pkg);
            Drawable icon = iconOf(pkg);
            final String target = pkg;
            it.actions.add(new IslandItem.Action(title, icon, null, () -> launch(target), false));
        }
        return it;
    }

    private CharSequence labelOf(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label : pkg;
        } catch (Throwable t) {
            return pkg;
        }
    }

    private Drawable iconOf(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Throwable t) {
            return null;
        }
    }

    private void launch(String pkg) {
        main.post(() -> {
            try {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) {
                    XLog.once("QuickLaunch no launch intent", new IllegalStateException(pkg));
                    return;
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(launch);
            } catch (Throwable t) {
                XLog.once("QuickLaunch launch " + pkg, t);
            }
        });
    }
}
