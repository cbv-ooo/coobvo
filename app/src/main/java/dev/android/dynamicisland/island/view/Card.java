package dev.android.dynamicisland.island.view;

import dev.android.dynamicisland.island.graphics.LiquidBridge;
import dev.android.dynamicisland.island.graphics.Motion;
import dev.android.dynamicisland.island.graphics.Spring;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.card.CardBody;

/**
 * One card of the expanded stack: the item it shows, its laid-out {@link CardBody} and the springs that
 * animate its centre, size and horizontal swipe offset. Main thread only.
 */
public final class Card {
    final ContentBlur contentBlur = new ContentBlur();
    boolean iMode;
    boolean iModePreview, iModePaging;
    final Spring iModeRole = new Spring(0, Motion.EFFECTS_DEFAULT).setValueThreshold(.002f);
    final ContentBlur iModeCompactBlur = new ContentBlur();
    final Spring cx = new Spring(0, Motion.SPATIAL_DEFAULT).setValueThreshold(.15f);
    final Spring iModeOpacity = new Spring(1, Motion.EFFECTS_SLOW).setValueThreshold(.002f);
    float leaveContentAlpha = 1;
    ContentHandoff handoff;
    float handoffScale = 1;
    CardBody body;
    long contentStart;
    IslandItem item;
    boolean launched;
    long leaveAt;
    boolean leaving;
    int order;
    long startAt;
    /** Size the card had when it started to leave; its content fades by how far the fold has got. */
    float leaveFromH;
    float leaveFromW;
    /** Where the card started when the cards are pulled open by the finger (the pill's size), see {@link Director#beginPullOpen}. */
    float pullFromCy, pullFromW, pullFromH;
    float targetCy;
    float targetH;
    float targetW;
    final Spring cy = new Spring(0.0f, Motion.SPATIAL_SLOW).setValueThreshold(0.4f);
    final Spring w = new Spring(0.0f, Motion.SPATIAL_SLOW).setValueThreshold(0.4f);
    final Spring h = new Spring(0.0f, Motion.SPATIAL_SLOW).setValueThreshold(0.4f);
    /** Swipe offset: follows the finger and springs back (or away) on release. */
    final Spring dx = new Spring(0.0f, Motion.SPATIAL_FAST).setValueThreshold(0.3f);

    /** Liquid connection to the pill while this is the topmost card. */
    final LiquidBridge bridge = new LiquidBridge();

    Card(IslandItem item, CardBody body) {
        this.item = item;
        this.body = body;
    }
}
