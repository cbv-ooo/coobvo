package dev.android.dynamicisland.island.view;

import android.graphics.Canvas;
import android.graphics.Path;
import dev.android.dynamicisland.island.IslandController;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.card.Btn;
import dev.android.dynamicisland.island.view.card.CardBody;
import dev.android.dynamicisland.island.view.card.MediaBody;

/** Renders and hit-tests an opaque island card in the notification shade. */
final class ShadeCards {
    private static final long SETTLED_ELAPSED_MS = 290;
    private final IslandView view;
    private final StackPainter painter;
    private CardBody body;
    private IslandItem item;
    private float width;

    ShadeCards(IslandView view, StackPainter painter) {
        this.view = view;
        this.painter = painter;
    }

    void forget() { body = null; }

    private CardBody bodyFor(IslandItem wanted, float w) {
        view.followFont();
        if (body == null || item != wanted || width != w) {
            body = view.bodies().create(wanted, w, true);
            item = wanted;
            width = w;
        }
        return body;
    }

    float heightOf(IslandItem wanted, float w) {
        return bodyFor(wanted, w).h;
    }

    void draw(Canvas canvas, IslandItem wanted, float w) {
        CardBody b = bodyFor(wanted, w);
        view.tmpRect.set(0.0f, 0.0f, b.w, b.h);
        float corner = b.cornerRadius(view.dp(28), b.h);
        int save = canvas.save();
        try {
            Path clip = new Path();
            clip.addRoundRect(view.tmpRect, corner, corner, Path.Direction.CW);
            canvas.clipPath(clip);
            painter.drawBackground(b, canvas, view.tmpRect, 1.0f);
            painter.drawBody(b, canvas, 0.0f, 0.0f, 1.0f, SETTLED_ELAPSED_MS);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    boolean hasButtonAt(IslandItem wanted, float w, float x, float y) {
        return bodyFor(wanted, w).hit(x, y) != null;
    }

    boolean buttonDown(IslandItem wanted, float w, float x, float y) {
        Btn b = bodyFor(wanted, w).hit(x, y);
        if (b != null) view.buttons().press(b, wanted);
        return b != null;
    }

    boolean seekStart(IslandItem wanted, float w, float x, float y) {
        CardBody b = bodyFor(wanted, w);
        if (!(b instanceof MediaBody) || !((MediaBody) b).onSeekBar(x, y)) return false;
        view.seekBody = (MediaBody) b;
        view.seekFraction = view.seekBody.fractionAt(x);
        return true;
    }

    void seekMove(float x) {
        if (view.seekBody != null) view.seekFraction = view.seekBody.fractionAt(x);
    }

    void seekEnd(boolean commit) {
        MediaBody seeking = view.seekBody;
        view.seekBody = null;
        if (commit && seeking != null && seeking.item.mediaSeek != null) {
            seeking.item.mediaSeek.accept((long) (view.seekFraction * seeking.item.mediaDuration));
        }
    }

    boolean tap(IslandItem wanted, float w, float x, float y) {
        Btn b = bodyFor(wanted, w).hit(x, y);
        if (b == null) return false;
        if (b.run != null) {
            b.run.run();
            return true;
        }
        if (b.action == null) return true;
        IslandController controller = IslandController.get();
        if (controller == null) return false;
        controller.onItemAction(wanted, b.action);
        return true;
    }
}
