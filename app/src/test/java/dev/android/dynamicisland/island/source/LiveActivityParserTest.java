package dev.android.dynamicisland.island.source;

import android.app.Application;
import android.app.Notification;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class LiveActivityParserTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private StatusBarNotification sbn(String pkg, Notification.Builder builder) throws Exception {
        Constructor<StatusBarNotification> constructor = StatusBarNotification.class.getConstructor(
                String.class, String.class, int.class, String.class, int.class, int.class,
                Notification.class, UserHandle.class, String.class, long.class);
        return constructor.newInstance(pkg, pkg, 11, null, 10100, 1, builder.build(),
                android.os.Process.myUserHandle(), null, System.currentTimeMillis());
    }

    private Notification.Builder notification(String title, String body) {
        return new Notification.Builder(app, "live")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true);
    }

    @Test
    public void recognizesRideWithEtaAndPreservesRealEta() throws Exception {
        LiveActivityParser.Info info = LiveActivityParser.parse(
                sbn("com.ubercab", notification("Your driver is on the way", "Arriving in 8 min")));
        assertNotNull(info);
        assertEquals("8 min", info.etaText);
        assertEquals(3, info.stage);
        assertEquals(-1f, info.progressRatio, 0.001f);
    }

    @Test
    public void preservesBothBoundsOfAnEtaRange() throws Exception {
        LiveActivityParser.Info info = LiveActivityParser.parse(
                sbn("com.ubercab", notification("Your driver is on the way", "Arrival in 6-9 min")));
        assertNotNull(info);
        assertEquals("6–9 min", info.etaText);
        assertEquals(2, info.stage);
    }

    @Test
    public void ignoresPromotionalNotificationFromSupportedDeliveryApp() throws Exception {
        assertNull(LiveActivityParser.parse(
                sbn("com.application.zomato", notification("Special offer", "50% off — use code SAVE"))));
    }

    @Test
    public void ignoresUnsupportedAppEvenWhenTextLooksLikeTracking() throws Exception {
        assertNull(LiveActivityParser.parse(
                sbn("com.example.chat", notification("Driver on the way", "Arriving in 4 min"))));
    }

    @Test
    public void usesOnlyNotificationProgressWhenProgressIsActuallyProvided() throws Exception {
        Notification.Builder builder = notification("Your order is preparing", "Order accepted")
                .setProgress(100, 40, false);
        LiveActivityParser.Info info = LiveActivityParser.parse(sbn("com.grabtaxi.passenger", builder));
        assertNotNull(info);
        assertEquals(0.4f, info.progressRatio, 0.001f);
    }
}
