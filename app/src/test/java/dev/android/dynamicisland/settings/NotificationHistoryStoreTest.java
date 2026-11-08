package dev.android.dynamicisland.settings;

import android.os.Bundle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public final class NotificationHistoryStoreTest {
    private android.content.Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("coobvo_notification_history.db");
    }

    @After
    public void tearDown() {
        context.deleteDatabase("coobvo_notification_history.db");
    }

    @Test
    public void recordsOnceAndReadsNewestEntry() {
        Bundle entry = entry("key-1", System.currentTimeMillis());
        assertTrue(NotificationHistoryStore.record(context, entry));
        assertFalse(NotificationHistoryStore.record(context, entry));

        ArrayList<String> rows = NotificationHistoryStore.read(context, 100);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).contains("hello"));
    }

    @Test
    public void rejectsMissingIdentityAndExpiredRows() {
        Bundle missing = entry("", System.currentTimeMillis());
        assertFalse(NotificationHistoryStore.record(context, missing));
        Bundle expired = entry("old", System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L);
        assertFalse(NotificationHistoryStore.record(context, expired));
        assertTrue(NotificationHistoryStore.read(context, 100).isEmpty());
    }

    @Test
    public void clearRemovesStoredEntries() {
        assertTrue(NotificationHistoryStore.record(context, entry("key-2", System.currentTimeMillis())));
        NotificationHistoryStore.clear(context);
        assertTrue(NotificationHistoryStore.read(context, 100).isEmpty());
    }

    private static Bundle entry(String key, long postTime) {
        Bundle bundle = new Bundle();
        bundle.putString("notificationKey", key);
        bundle.putString("packageName", "com.example.chat");
        bundle.putString("appName", "Example Chat");
        bundle.putString("title", "Message");
        bundle.putString("text", "hello");
        bundle.putLong("postTime", postTime);
        return bundle;
    }
}
