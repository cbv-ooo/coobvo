package dev.android.dynamicisland.island.view;

import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import dev.android.dynamicisland.island.feedback.Haptics;
import dev.android.dynamicisland.island.graphics.Easing;
import dev.android.dynamicisland.island.graphics.Motion;
import dev.android.dynamicisland.island.graphics.Spring;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.card.MediaBody;
import dev.android.dynamicisland.island.view.card.SliderBody;
import dev.android.dynamicisland.island.view.card.CardBody;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RESTORED STUB - full file must be applied from artifacts/Director_FINAL.java
 * This placeholder prevents compile with see_file; replace immediately with full Director.
 */
final class Director {
    static final int MAX_CARDS = 4;
    private final IslandView view;
    private final IslandHost host;
    private final Metrics m;
    private final Scene scene;
    private final Springs sp;
    private final Shapes shapes;
    private final BodyFactory bodies;
    private final MediaFxCache mediaFx;
    private final Runnable autoCollapse = this::collapseIfExpanded;
    private final Runnable fadePillBlob = () -> {};
    private final Runnable startSplit = () -> {};
    private long appearedAt;
    private String peekKey;

    Director(IslandView view, IslandHost host, Metrics m, Scene scene, Shapes shapes, BodyFactory bodies, MediaFxCache mediaFx) {
        this.view = view; this.host = host; this.m = m; this.scene = scene;
        this.sp = scene.springs; this.shapes = shapes; this.bodies = bodies; this.mediaFx = mediaFx;
    }

    private void collapseIfExpanded() {
        if (scene.mode == Mode.EXPANDED) collapse();
    }
    Runnable autoCollapseTask() { return autoCollapse; }
    void cancelAutoCollapse() { view.removeCallbacks(autoCollapse); }
    void scheduleAutoCollapse(long ms) {
        view.removeCallbacks(autoCollapse);
        if (ms > 0 && !scene.held) view.postDelayed(autoCollapse, ms);
    }
    void postAutoCollapse() {
        if (!scene.held) view.postDelayed(autoCollapse, ModuleSettings.autoCollapseMs());
    }
    String clearPeek() { String k = peekKey; peekKey = null; return k; }

    void update(List<IslandItem> list, int hint) {
        /* incomplete restore - replace with artifacts/Director_FINAL.java */
    }
    void reveal() {}
    void collapse() {}
    void expand(List<IslandItem> show, long autoCollapseMs) {}
    void peek(IslandItem item, long ms) {
        scene.peekOpen = true;
        peekKey = item.key;
        if (scene.iMode != null) {
            scene.iMode.peek(item);
            scheduleAutoCollapse(ms);
        } else {
            expand(Collections.singletonList(item), ms);
        }
    }
    void finishSleeping() {}
    void refreshMain() {}
    void refreshBubble() {}
    void refreshFontLayouts() {}
    void setHeld(boolean held) { scene.held = held; }
}
