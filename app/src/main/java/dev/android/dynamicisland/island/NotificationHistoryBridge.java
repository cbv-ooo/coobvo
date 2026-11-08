package dev.android.dynamicisland.island;

import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import dev.android.dynamicisland.island.detect.OtpDetector;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.source.PeekSource;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.settings.SettingsProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends minimal, privacy-filtered records to the app-owned provider off the System UI thread. */
final class NotificationHistoryBridge {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CoobvoNotificationHistory");
        thread.setDaemon(true);
        return thread;
    });

    private NotificationHistoryBridge() {}

    static void record(Context context, StatusBarNotification sbn, IslandItem item) {
        if (context == null || sbn == null || item == null || !ModuleSettings.notificationHistory()
                || item.otpCode != null) return;
        try {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null
                    || notification.visibility == Notification.VISIBILITY_SECRET
                    || (notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0
                    || Notification.CATEGORY_CALL.equals(notification.category)
                    || Notification.CATEGORY_ALARM.equals(notification.category)
                    || Notification.CATEGORY_TRANSPORT.equals(notification.category)
                    || Notification.CATEGORY_NAVIGATION.equals(notification.category)) return;
            if (OtpDetector.detect(PeekSource.otpText(notification)) != null) return;

            String title = text(item.title);
            String body = text(item.subtitle);
            if (title.isEmpty() && body.isEmpty()) return;
            Bundle data = new Bundle();
            data.putString("notificationKey", sbn.getKey());
            data.putString("packageName", sbn.getPackageName());
            String appName = text(item.appName);
            data.putString("appName", appName.isEmpty() ? sbn.getPackageName() : appName);
            data.putString("title", title);
            data.putString("text", body);
            data.putString("subText", text(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)));
            data.putString("category", notification.category);
            data.putString("channelId", notification.getChannelId());
            data.putLong("postTime", sbn.getPostTime());

            Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            IO.execute(() -> {
                try {
                    Uri uri = Uri.parse("content://" + ModuleSettings.PACKAGE + ".settings");
                    app.getContentResolver().call(uri, SettingsProvider.METHOD_HISTORY_ADD, null, data);
                } catch (Throwable ignored) {
                    // History is optional and must never interfere with notification takeover.
                }
            });
        } catch (Throwable ignored) {
            // Malformed OEM notification extras are skipped.
        }
    }

    private static String text(CharSequence value) {
        if (value == null) return "";
        String result = value.toString().replace('\u0000', ' ').trim();
        return result.length() > 2000 ? result.substring(0, 2000) : result;
    }
}
