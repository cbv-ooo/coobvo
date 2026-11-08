package dev.android.dynamicisland.island;

import android.app.Notification;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import dev.android.dynamicisland.island.detect.NotificationText;
import dev.android.dynamicisland.island.detect.OtpDetector;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.source.PeekSource;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import dev.android.dynamicisland.xposed.hooks.HeadsUpHooks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which heads-up banners and which fresh verification codes become a temporary card on the
 * island, and remembers what already got one so nothing is shown twice.
 */
final class HeadsUpTakeover {
    /** How long a heads-up card stays open unless the item asks for something else. */
    private static final long DEFAULT_PEEK_MS = 5000;
    private static final long STOCK_DISMISS_DELAY_MS = 120;
    private static final long OTP_FRESH_MS = 20000;
    private static final long ALL_FRESH_MS = 8000;
    private static final long OTP_CLOCK_SKEW_MS = 5000;
    private static final long OTP_REPEAT_WINDOW_MS = 120000;
    private static final long REPEAT_QUIET_MS = 180_000;
    private static final int MAX_PEEKED = 32;
    private static final int MAX_OTP_CHECKED = 128;
    private static final int MAX_OTP_LOGS = 20;

    private final IslandSources sources;
    private final IslandPlacement placement;
    private final Runnable refreshNow;
    private final NotificationCooldownManager<StatusBarNotification> notificationCooldown =
            new NotificationCooldownManager<>();
    private final Map<String, Runnable> cooldownDeliveries = new HashMap<>();
    private final Map<String, Long> peeked = new HashMap<>();
    private final Map<String, Shown> shown = new HashMap<>();
    private final Set<String> otpChecked = new HashSet<>();
    private final Set<String> allChecked = new HashSet<>();
    private List<StatusBarNotification> snapshot = java.util.Collections.emptyList();
    private int rejectLogs;
    private int otpLogs;
    private String lastOtpCode;
    private long lastOtpAt;

    HeadsUpTakeover(IslandSources sources, IslandPlacement placement, Runnable refreshNow) {
        this.sources = sources;
        this.placement = placement;
        this.refreshNow = refreshNow;
    }

    void setSnapshot(List<StatusBarNotification> notifications) {
        snapshot = new java.util.ArrayList<>(notifications);
    }

    boolean owns(String key) {
        IslandItem item = sources.peeks.showing();
        if (item == null || !item.key.equals("peek:" + key) || !placement.view().isShowingCard(item)) return false;
        if (!ModuleSettings.appAllowed(item.packageName)) return false;
        if (item.otpCode != null ? !ModuleSettings.otpCard() : !(ModuleSettings.headsUpIsland() || ModuleSettings.allNotifications())) return false;
        for (StatusBarNotification notification : snapshot) {
            if (notification.getKey().equals(key) && notification.getPostTime() != item.postTime) return false;
        }
        return true;
    }

    private boolean foregroundBlocked(StatusBarNotification sbn) {
        return !ModuleSettings.foregroundNotifications() && sbn.getPackageName().equals(sources.foregroundPackage());
    }

    boolean take(StatusBarNotification sbn) {
        try {
            IslandView view = placement.view();
            if (sbn == null || !placement.ready()) {
                return false;
            }
            if (!ModuleSettings.appAllowed(sbn.getPackageName())) {
                reject("banner of " + sbn.getPackageName() + ": the app is kept out of the island");
                return false;
            }
            if (isOnIslandNow(sbn) || recentlyShown(sbn)) {
                return true;
            }
            if (view.isSuppressedNow() || !PeekSource.eligible(sbn)
                    || (!ModuleSettings.experimentalRecovery()
                    && (foregroundBlocked(sbn) || !PeekSource.preferred(sbn, snapshot)))) {
                reject("banner of " + sbn.getPackageName() + ": " + (view.isSuppressedNow() ? "island hidden" : !PeekSource.eligible(sbn) ? "not an ordinary notification" : "its app is open"));
                return false;
            }
            IslandItem code = sources.peeks.buildOtp(sbn);
            if (code != null) {
                if (isRepeat(code.otpCode)) {
                    IslandItem showing = sources.peeks.showing();
                    return showing != null && code.otpCode.equals(showing.otpCode);
                }
                boolean shown = show(sbn, code);
                log(shown ? "shown with its heads-up" : "not shown: the island could not open");
                return shown;
            }
            if (!(ModuleSettings.headsUpIsland() || ModuleSettings.allNotifications())) {
                reject("banner of " + sbn.getPackageName() + ": " + (view.isExpanded() ? "a card is open" : "the notification settings are off"));
                return false;
            }
            if (throttleOrdinaryNotification(sbn)) {
                reject("banner of " + sbn.getPackageName() + ": notification cooldown");
                return false;
            }
            IslandItem message = sources.peeks.buildMessage(sbn);
            boolean taken = message != null && show(sbn, message);
            note("banner of " + sbn.getPackageName() + (taken ? " taken by the island" : " left to the stock banner: no card could be built or opened"));
            return taken;
        } catch (Throwable t) {
            XLog.once("IslandController.peekHeadsUp", t);
            return false;
        }
    }

    void scanAll(List<StatusBarNotification> snapshot) {
        if (!ModuleSettings.allNotifications() || !placement.ready()) {
            return;
        }
        if (allChecked.size() > MAX_OTP_CHECKED) {
            allChecked.clear();
        }
        long now = System.currentTimeMillis();
        StatusBarNotification newest = null;
        for (StatusBarNotification sbn : snapshot) {
            long age = now - sbn.getPostTime();
            if (age > ALL_FRESH_MS || age < -OTP_CLOCK_SKEW_MS || !allChecked.add(sbn.getKey() + "@" + sbn.getPostTime())) {
                continue;
            }
            if (alreadyHadCard(sbn) || recentlyShown(sbn) || !PeekSource.eligible(sbn) || !ModuleSettings.appAllowed(sbn.getPackageName())
                    || (!ModuleSettings.experimentalRecovery()
                    && (foregroundBlocked(sbn) || !PeekSource.preferred(sbn, snapshot)))) {
                continue;
            }
            if (throttleOrdinaryNotification(sbn)) {
                continue;
            }
            if (newest == null || sbn.getPostTime() >= newest.getPostTime()) {
                newest = sbn;
            }
        }
        if (newest == null) {
            return;
        }
        IslandView view = placement.view();
        if (ModuleSettings.experimentalRecovery() ? view.isSuppressedNow() : !view.canPeek()) {
            reject("all notifications: the island is unavailable");
            return;
        }
        IslandItem item = sources.peeks.build(newest);
        if (item != null) {
            if (show(newest, item)) {
                note("card for " + newest.getPackageName() + " from the notification list (stock banner asked to leave: "
                        + HeadsUpHooks.dismissStock(newest.getKey()) + ")");
            } else {
                reject("all notifications: the island could not open");
            }
        }
    }

    private boolean throttleOrdinaryNotification(StatusBarNotification sbn) {
        if (!ModuleSettings.notificationCooldown() || sbn == null || isTransferExempt(sbn)) return false;
        long now = SystemClock.elapsedRealtime();
        boolean throttled = notificationCooldown.shouldThrottle(
                sbn.getPackageName(), sbn.getKey() + "@" + sbn.getPostTime(), sbn,
                3, 3, now);
        if (throttled) scheduleCooldownDelivery(sbn.getPackageName());
        return throttled;
    }

    private boolean isTransferExempt(StatusBarNotification sbn) {
        if (sbn == null) return true;
        Notification n = sbn.getNotification();
        if (n == null) return true;
        String category = n.category;
        return Notification.CATEGORY_TRANSPORT.equals(category)
                || Notification.CATEGORY_PROGRESS.equals(category)
                || Notification.CATEGORY_SERVICE.equals(category)
                || Notification.CATEGORY_CALL.equals(category)
                || Notification.CATEGORY_ALARM.equals(category)
                || Notification.CATEGORY_NAVIGATION.equals(category);
    }

    private void scheduleCooldownDelivery(String packageName) {
        IslandView view = placement.view();
        if (view == null) return;
        Runnable previous = cooldownDeliveries.remove(packageName);
        if (previous != null) view.removeCallbacks(previous);
        Runnable deliver = () -> {
            cooldownDeliveries.remove(packageName);
            try {
                deliverBufferedCooldownNotification(packageName);
            } catch (Throwable t) {
                XLog.once("HeadsUpTakeover.cooldownDelivery", t);
            }
        };
        cooldownDeliveries.put(packageName, deliver);
        view.postDelayed(deliver, Math.max(0L, notificationCooldown.remainingMillis(packageName, SystemClock.elapsedRealtime())));
    }

    private void deliverBufferedCooldownNotification(String packageName) {
        StatusBarNotification queued = notificationCooldown.pollBufferedNotification(packageName, SystemClock.elapsedRealtime());
        if (queued == null || !placement.ready()) return;
        IslandView view = placement.view();
        if (view == null || (ModuleSettings.experimentalRecovery() ? view.isSuppressedNow() : !view.canPeek())) return;
        if (!ModuleSettings.appAllowed(queued.getPackageName()) || !PeekSource.eligible(queued)) return;
        if (!ModuleSettings.experimentalRecovery() && (foregroundBlocked(queued) || !PeekSource.preferred(queued, snapshot))) return;
        if (!(ModuleSettings.headsUpIsland() || ModuleSettings.allNotifications())) return;
        IslandItem item = sources.peeks.buildMessage(queued);
        if (item != null && show(queued, item)) {
            note("cooldown delivery for " + packageName);
        }
    }

    void scanForCodes(List<StatusBarNotification> snapshot) {
        if (!ModuleSettings.otpCard() || !placement.ready()) return;
        long now = System.currentTimeMillis();
        for (StatusBarNotification sbn : snapshot) {
            long age = now - sbn.getPostTime();
            if (age > OTP_FRESH_MS || age < -OTP_CLOCK_SKEW_MS) continue;
            if (!otpChecked.add(sbn.getKey() + "@" + sbn.getPostTime())) continue;
            if (alreadyHadCard(sbn) || !ModuleSettings.appAllowed(sbn.getPackageName())) continue;
            IslandItem item = sources.peeks.buildOtp(sbn);
            if (item != null) {
                if (isRepeat(item.otpCode)) continue;
                log(show(sbn, item) ? "shown from the notification list" : "not shown: the island could not open");
            }
        }
        if (otpChecked.size() > MAX_OTP_CHECKED) otpChecked.clear();
    }

    private boolean alreadyHadCard(StatusBarNotification sbn) {
        Long seen = peeked.get(sbn.getKey());
        return seen != null && seen == sbn.getPostTime();
    }

    private boolean recentlyShown(StatusBarNotification sbn) {
        Shown previous = shown.get(sbn.getKey());
        if (previous == null) return false;
        if (SystemClock.uptimeMillis() - previous.at > REPEAT_QUIET_MS) return false;
        return previous.content.equals(contentOf(sbn));
    }

    private static String contentOf(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return "";
        StringBuilder out = new StringBuilder();
        for (String key : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_INFO_TEXT}) {
            CharSequence value = n.extras.getCharSequence(key);
            if (value != null) out.append(value).append('\u0001');
        }
        android.os.Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable.class);
        return out.append(messages == null ? 0 : messages.length).toString();
    }

    private static final class Shown {
        final String content;
        final long at;
        Shown(String content, long at) {
            this.content = content;
            this.at = at;
        }
    }

    private boolean isOnIslandNow(StatusBarNotification sbn) {
        return alreadyHadCard(sbn) && !foregroundBlocked(sbn) && owns(sbn.getKey());
    }

    private boolean isRepeat(String code) {
        return code != null && code.equals(lastOtpCode) && SystemClock.uptimeMillis() - lastOtpAt < OTP_REPEAT_WINDOW_MS;
    }

    /** Timed peeks only (DEFAULT_PEEK_MS). Sticky forever peeks intentionally not used. */
    private boolean show(StatusBarNotification sbn, IslandItem item) {
        IslandView view = placement.view();
        if (view == null || !view.canPeek()) return false;
        long millis = item.peekMillis > 0 ? item.peekMillis : DEFAULT_PEEK_MS;
        sources.peeks.show(item, millis);
        refreshNow.run();
        boolean keepCompact = placement.shouldKeepPeekCompactForCutout();
        if (!keepCompact && !view.peek(item, millis)) {
            sources.peeks.dismiss(item);
            refreshNow.run();
            return false;
        }
        if (keepCompact) {
            note("notification from " + sbn.getPackageName()
                    + " kept compact because its expansion path crosses the camera cutout");
        }
        if (peeked.size() > MAX_PEEKED) peeked.clear();
        peeked.put(sbn.getKey(), sbn.getPostTime());
        NotificationHistoryBridge.record(placement.view().getContext(), sbn, item);
        if (shown.size() > MAX_PEEKED) shown.clear();
        shown.put(sbn.getKey(), new Shown(contentOf(sbn), SystemClock.uptimeMillis()));
        final String key = sbn.getKey();
        view.postDelayed(() -> HeadsUpHooks.dismissStock(key), STOCK_DISMISS_DELAY_MS);
        if (item.otpCode != null) {
            lastOtpCode = item.otpCode;
            lastOtpAt = SystemClock.uptimeMillis();
            if (ModuleSettings.otpAutoCopy()) sources.peeks.copy(item.otpCode);
        }
        return true;
    }

    private void reject(String what) {
        if (rejectLogs < 8) {
            rejectLogs++;
            XLog.i("HeadsUpTakeover reject " + what);
        }
    }

    private void note(String what) {
        XLog.i("HeadsUpTakeover " + what);
    }

    private void log(String what) {
        if (otpLogs < MAX_OTP_LOGS) {
            otpLogs++;
            XLog.i("HeadsUpTakeover code card " + what);
        }
    }
}
