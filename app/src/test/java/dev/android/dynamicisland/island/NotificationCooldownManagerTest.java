package dev.android.dynamicisland.island;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NotificationCooldownManagerTest {
    @Test
    public void allowsThresholdNotificationThenBuffersLatestDuringCooldown() {
        NotificationCooldownManager<String> manager = new NotificationCooldownManager<>();
        long start = 10_000L;
        assertFalse(manager.shouldThrottle("chat", "one", "one", 3, 3, start));
        assertFalse(manager.shouldThrottle("chat", "two", "two", 3, 3, start + 2_000L));
        assertFalse(manager.shouldThrottle("chat", "three", "three", 3, 3, start + 4_000L));
        assertEquals(180_000L, manager.remainingMillis("chat", start + 4_000L));

        assertTrue(manager.shouldThrottle("chat", "four", "four", 3, 3, start + 5_000L));
        assertTrue(manager.shouldThrottle("chat", "five", "five", 3, 3, start + 6_000L));
        assertEquals("five", manager.pollBufferedNotification("chat", start + 184_001L));
        assertNull(manager.pollBufferedNotification("chat", start + 184_001L));
    }

    @Test
    public void doesNotCountTheSamePostingTwice() {
        NotificationCooldownManager<String> manager = new NotificationCooldownManager<>();
        assertFalse(manager.shouldThrottle("chat", "same@1", "item", 3, 3, 1_000L));
        assertFalse(manager.shouldThrottle("chat", "same@1", "item", 3, 3, 1_100L));
        assertFalse(manager.shouldThrottle("chat", "two", "two", 3, 3, 1_200L));
        assertFalse(manager.shouldThrottle("chat", "three", "three", 3, 3, 1_300L));
        assertTrue(manager.shouldThrottle("chat", "four", "four", 3, 3, 1_400L));
    }

    @Test
    public void expiredCooldownStartsANewDetectionWindow() {
        NotificationCooldownManager<String> manager = new NotificationCooldownManager<>();
        assertFalse(manager.shouldThrottle("chat", "one", "one", 2, 1, 1_000L));
        assertFalse(manager.shouldThrottle("chat", "two", "two", 2, 1, 2_000L));
        assertTrue(manager.shouldThrottle("chat", "three", "three", 2, 1, 3_000L));
        assertEquals("three", manager.pollBufferedNotification("chat", 63_000L));
        assertFalse(manager.shouldThrottle("chat", "four", "four", 2, 1, 63_000L));
        assertFalse(manager.shouldThrottle("chat", "five", "five", 2, 1, 64_000L));
    }
}
