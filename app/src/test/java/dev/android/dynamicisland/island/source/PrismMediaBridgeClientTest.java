package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class PrismMediaBridgeClientTest {
    @Test public void derivesPrismBridgeUriFromMediaIdWhenCompatDropsCustomExtras() {
        Bundle extras = PrismMediaBridgeClient.ensureBridgeExtras(null, "song/123");
        assertNotNull(extras);
        assertEquals(1, extras.getInt("coobvo.prism.bridge.version"));
        Uri uri = Uri.parse(extras.getString("coobvo.prism.bridge.uri"));
        assertEquals("com.prism.music.artwork", uri.getAuthority());
        assertEquals("bridge", uri.getPathSegments().get(0));
        assertEquals("song/123", uri.getLastPathSegment());
    }

    @Test public void preservesTheUriExportedInPrismMedia3Extras() {
        Bundle source = new Bundle();
        source.putString("coobvo.prism.bridge.uri", "content://com.prism.music.artwork/bridge/native-id");
        source.putInt("coobvo.prism.bridge.version", 2);
        Bundle extras = PrismMediaBridgeClient.ensureBridgeExtras(source, "different-fallback");
        assertEquals(2, extras.getInt("coobvo.prism.bridge.version"));
        assertEquals("content://com.prism.music.artwork/bridge/native-id",
                extras.getString("coobvo.prism.bridge.uri"));
    }

    @Test public void returnsNullWhenNeitherPrismExtrasNorMediaIdCanIdentifyTheSong() {
        assertNull(PrismMediaBridgeClient.ensureBridgeExtras(null, null));
    }

    @Test public void readsOnlyCompleteNormalizedPrismSongWaveforms() {
        JSONArray json = new JSONArray();
        for (int i = 0; i < 100; i++) json.put(i / 99.0d);
        float[] values = PrismMediaBridgeClient.readWaveform(json);
        assertNotNull(values);
        assertEquals(100, values.length);
        assertEquals(0.0f, values[0], 0.001f);
        assertEquals(1.0f, values[99], 0.001f);

        assertNull(PrismMediaBridgeClient.readWaveform(new JSONArray("[0.2,0.4]")));
        assertNull(PrismMediaBridgeClient.readWaveform(null));
    }
}
