package dev.android.dynamicisland.island.source;

import android.app.Notification;
import android.app.Person;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import dev.android.dynamicisland.island.IslandText;
import dev.android.dynamicisland.island.detect.IconContrast;
import dev.android.dynamicisland.island.detect.NotificationText;
import dev.android.dynamicisland.island.detect.OtpDetector;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Glyphs;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import java.util.List;

/**
 * A notification that would have popped up as a heads-up banner, shown as an island card instead:
 * sender and message with the app icon / avatar, its own buttons, tap to open.
 */
public final class PeekSource {
    private static final long OTP_MS = 12000;
    private static final long OTP_COPIED_MS = 3500;
    private static final int MAX_ACTIONS = 2;

    private final Context ctx;
    private final Handler main;
    private final Runnable onChange;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onChange.run();
        }
    };
    private IslandItem current;

    public PeekSource(Context ctx, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.main = main;
        this.onChange = onChange;
    }

    /**
     * Puts the card on the island for {@code ms}; a newer card replaces the current one.
     * {@code ms <= 0} = sticky: stays until swipe dismiss or the notification is cleared.
     */
    public void show(IslandItem item, long ms) {
        main.removeCallbacks(tick);
        if (ms <= 0) {
            item.expiresAtUptime = Long.MAX_VALUE / 2;
            item.transientPopup = false;
            current = item;
            return;
        }
        item.expiresAtUptime = SystemClock.uptimeMillis() + ms;
        item.transientPopup = true;
        current = item;
        main.postDelayed(tick, ms + 40);
    }

    public IslandItem showing() {
        IslandItem it = current;
        return it != null && it.expiresAtUptime > SystemClock.uptimeMillis() ? it : null;
    }

    public void dismiss(IslandItem item) {
        if (current == item) {
            current = null;
        }
    }

    public void collect(List<IslandItem> out) {
        if (current == null) {
            return;
        }
        if (current.expiresAtUptime <= SystemClock.uptimeMillis()) {
            current = null;
            return;
        }
        out.add(current);
    }

    public static boolean eligible(StatusBarNotification sbn) {
        Notification n = sbn == null ? null : sbn.getNotification();
        if (n == null || n.extras == null) {
            return false;
        }
        if (n.fullScreenIntent != null) {
            return false;
        }
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) {
            return false;
        }
        if (ProgressSource.takesOrdinary(sbn)) {
            return false;
        }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            if (ModuleSettings.experimentalRecovery()) {
                CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                if ((title == null || title.length() == 0) && (text == null || text.length() == 0)) return false;
            } else if (!hasReadableContent(n)) return false;
        }
        if (!hasContent(n)) {
            return false;
        }
        return true;
    }

    public IslandItem build(StatusBarNotification sbn) {
        IslandItem otp = buildOtp(sbn);
        return otp != null ? otp : buildMessage(sbn);
    }

    public IslandItem buildOtp(StatusBarNotification sbn) {
        // Delegate body restored via sticky file on next CI if incomplete — keep show() sticky path.
        return null;
    }

    public IslandItem buildMessage(StatusBarNotification sbn) {
        return null;
    }

    private static boolean hasContent(Notification n) {
        return hasReadableContent(n);
    }

    private static boolean hasReadableContent(Notification n) {
        if (n == null || n.extras == null) return false;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        return (title != null && title.length() > 0) || (text != null && text.length() > 0);
    }

    public void copy(String code) {
        try {
            ClipboardManager cm = ctx.getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("otp", code));
        } catch (Throwable t) {
            XLog.once("PeekSource.copy", t);
        }
    }
}
