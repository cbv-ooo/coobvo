package dev.android.dynamicisland.island;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Main-thread-owned per-package notification burst limiter.
 *
 * The notification that reaches the threshold is allowed and starts the cooldown. Later notifications
 * are held in a one-item-per-package buffer, replacing older buffered notifications with the newest.
 * The caller is responsible for deciding which notification types are exempt and for delivering the
 * buffered item after the quiet interval.
 */
final class NotificationCooldownManager<T> {
    static final long DETECTION_WINDOW_MS = 30_000L;
    private static final int MAX_PACKAGES = 64;
    private static final int MAX_POSTINGS = 256;

    private final Map<String, ArrayDeque<Long>> recentTimestamps = new HashMap<>();
    private final Map<String, Long> cooldownExpirations = new HashMap<>();
    private final Map<String, T> bufferedNotifications = new HashMap<>();
    private final Set<String> seenPostings = new LinkedHashSet<>();
    private final Set<String> blockedPostings = new HashSet<>();

    boolean shouldThrottle(String packageName, String postingKey, T notification,
            int threshold, int cooldownMinutes, long nowMs) {
        if (packageName == null || packageName.isEmpty() || postingKey == null
                || notification == null || threshold < 2 || cooldownMinutes < 1) return false;

        String identity = packageName + "|" + postingKey;
        if (blockedPostings.contains(identity)) return true;
        if (!seenPostings.add(identity)) return false;
        if (seenPostings.size() > MAX_POSTINGS) seenPostings.clear();

        Long expiresAt = cooldownExpirations.get(packageName);
        if (expiresAt != null && nowMs < expiresAt) {
            bufferedNotifications.put(packageName, notification);
            blockedPostings.add(identity);
            trimPackages();
            return true;
        }
        if (expiresAt != null) {
            cooldownExpirations.remove(packageName);
            recentTimestamps.remove(packageName);
        }

        ArrayDeque<Long> timestamps = recentTimestamps.get(packageName);
        if (timestamps == null) {
            timestamps = new ArrayDeque<>();
            recentTimestamps.put(packageName, timestamps);
        }
        timestamps.addLast(nowMs);
        while (!timestamps.isEmpty() && nowMs - timestamps.peekFirst() > DETECTION_WINDOW_MS) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= threshold) {
            cooldownExpirations.put(packageName, nowMs + cooldownMinutes * 60_000L);
            timestamps.clear();
        }
        trimPackages();
        return false;
    }

    long remainingMillis(String packageName, long nowMs) {
        Long expiresAt = cooldownExpirations.get(packageName);
        return expiresAt == null ? 0L : Math.max(0L, expiresAt - nowMs);
    }

    T pollBufferedNotification(String packageName, long nowMs) {
        T result = bufferedNotifications.remove(packageName);
        String prefix = packageName + "|";
        for (Iterator<String> it = blockedPostings.iterator(); it.hasNext();) {
            if (it.next().startsWith(prefix)) it.remove();
        }
        Long expiry = cooldownExpirations.get(packageName);
        if (expiry == null || expiry <= nowMs) {
            cooldownExpirations.remove(packageName);
            recentTimestamps.remove(packageName);
        }
        return result;
    }

    void clear() {
        recentTimestamps.clear();
        cooldownExpirations.clear();
        bufferedNotifications.clear();
        seenPostings.clear();
        blockedPostings.clear();
    }

    private void trimPackages() {
        if (recentTimestamps.size() <= MAX_PACKAGES && cooldownExpirations.size() <= MAX_PACKAGES
                && bufferedNotifications.size() <= MAX_PACKAGES) return;
        recentTimestamps.clear();
        cooldownExpirations.clear();
        bufferedNotifications.clear();
        blockedPostings.clear();
        seenPostings.clear();
    }
}
