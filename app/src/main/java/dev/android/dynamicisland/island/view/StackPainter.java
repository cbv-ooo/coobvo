package dev.android.dynamicisland.island.view;

import static dev.android.dynamicisland.island.graphics.Easing.easeOutBack;
import static dev.android.dynamicisland.island.graphics.Easing.lerp;
import static dev.android.dynamicisland.island.view.IslandView.clamp01;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Easing;
import dev.android.dynamicisland.island.graphics.Metaball;
import dev.android.dynamicisland.island.view.card.CardBody;
import dev.android.dynamicisland.island.view.card.RemoteBody;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;

/**
 * Draws expanded opaque cards, their content, and the metaball neck between cards that are being swallowed.
 */
final class StackPainter {
    /** Card content fades in from this far below its place and from this size. */
    private static final float CONTENT_RISE_DP = 10.0f;
    private static final float CONTENT_SCALE_FROM = 0.95f;
    /** How long a leaving card's content takes to fade while it shrinks with the card. */
    private static final float LEAVING_CONTENT_FADE_MS = 90.0f;
    /** Fold progress by which a leaving card's content is gone: it fades first, then the empty shape folds into the pill. */
    private static final float FOLD_FADE_TO = 0.38f;
    /** Opening progress at which a new card's content is fully there. */
    private static final float OPEN_FADE_TO = 0.25f;
    /** A card that is leaving reports this "elapsed" time so its content is fully shown while it fades. */
    private static final long LEAVING_CONTENT_ELAPSED_MS = 2305843009213693951L;
    /** Necks thinner than this are not drawn; they reach full opacity this much wider. */
    private static final float NECK_FADE_FROM_DP = 4.0f;
    private static final float NECK_FADE_RANGE_DP = 6.0f;

    private final Metrics m;
    private final Paints paints;
    private final Scene scene;
    private final Shapes shapes;
    private final Path clip = new Path();
    private final RectF inner = new RectF();
    private final RectF slot = new RectF();
    private final RectF gooUpper = new RectF();
    private final RectF gooLower = new RectF();
    private final Path gooPath = new Path();

    private boolean drawFailureLogged;

    StackPainter(Metrics m, Paints paints, Scene scene, Shapes shapes) {
        this.m = m;
        this.paints = paints;
        this.scene = scene;
        this.shapes = shapes;
    }

    /** Fade of a card's content: in once the card is nearly open, out quickly when it leaves. */
    static float contentAlpha(Card c, long t, float opening) {
        if (c.handoff != null) return clamp01(c.handoff.expanded(t) * c.handoffScale);
        if (c.leaving) {
            if (ModuleSettings.legacyAnimation()) {
                return c.leaveContentAlpha * (1.0f - clamp01((t - c.leaveAt) / LEAVING_CONTENT_FADE_MS));
            }
            // the content fades out in the first part of the fold, while the card is still large; what
            // travels back into the pill is the plain shape, which does not leave a smear of tiny text behind
            return c.leaveContentAlpha * (1.0f - Easing.easeInOut(clamp01(foldProgress(c) / FOLD_FADE_TO)));
        }
        if (!ModuleSettings.legacyAnimation()) {
            // the mirror of folding: the content grows with the card from the first moment instead of appearing in an empty shape
            return clamp01(opening / OPEN_FADE_TO);
        }
        return clamp01((opening - 0.85f) / IslandView.PAUSED_SPEED) * clamp01((t - c.contentStart) / 150.0f);
    }

    /** Compatibility value for remote-card layout: no transition blur is applied. */
    static float contentBlurAmount(Card card, long time, float opening) { return 0.0f; }

    /** How far a leaving card has folded towards the size it is going to (0 = not yet, 1 = there). */
    private static float foldProgress(Card c) {
        if (!c.launched) {
            return 0.0f;
        }
        float spanH = c.leaveFromH - c.targetH;
        if (spanH > 1.0f) {
            return clamp01((c.leaveFromH - c.h.value()) / spanH);
        }
        float spanW = c.leaveFromW - c.targetW;
        return spanW > 1.0f ? clamp01((c.leaveFromW - c.w.value()) / spanW) : 1.0f;
    }

    /** A liquid neck fades out as its waist gets thin, so it never shows as a hair-thin line. */
    float neckAlpha(float waist) {
        return clamp01((waist - m.dp(NECK_FADE_FROM_DP)) / m.dp(NECK_FADE_RANGE_DP));
    }

    /** The "opening" a card is drawn with: a leaving card goes back to the pill's black along its fold, not along its height. */
    float visualOpening(Card c, float opening) {
        if(c.iModePaging) return opening;
        if (c.leaving && !ModuleSettings.legacyAnimation()) {
            return Math.min(opening, 1.0f - Easing.easeInOut(foldProgress(c)));
        }
        return opening;
    }

    /** Expanded cards use one fully opaque Material surface; no glass, fade-through or gradient fill. */
    int fillColor(float opening) {
        return Colors.SURFACE_CONTAINER;
    }

    /** The neck of goo between two cards that are folding back into the pill one after the other. */
    void drawGoo(Canvas canvas) {
        if(scene.iMode!=null) return;
        Card previous = null;
        for (int i = 0, n = scene.cards.size(); i < n; i++) {
            Card c = scene.cards.get(i);
            if (!c.leaving) {
                continue;
            }
            if (previous != null) {
                drawGooBetween(canvas, previous, c);
            }
            previous = c;
        }
    }

    private void drawGooBetween(Canvas canvas, Card a, Card b) {
        shapes.card(a, gooUpper);
        shapes.card(b, gooLower);
        RectF top = gooUpper.top <= gooLower.top ? gooUpper : gooLower;
        RectF bottom = top == gooUpper ? gooLower : gooUpper;
        float gap = bottom.top - top.bottom;
        float openness = Math.max(shapes.expansion(a), shapes.expansion(b));
        float spread = (1.0f - clamp01(gap / m.dp(30.0f))) * 0.6f * (1.0f - openness);
        if (spread <= 0.02f) {
            return;
        }
        float topR = Math.min(Math.min(top.height(), top.width()) / 2.0f, m.dp(22.0f));
        float bottomR = Math.min(Math.min(bottom.height(), bottom.width()) / 2.0f, m.dp(22.0f));
        gooPath.reset();
        float alpha = neckAlpha(Metaball.neck(top.centerX(), top.bottom - topR, topR, bottom.centerX(), bottom.top + bottomR, bottomR, spread, gooPath));
        if (alpha > 0.0f) {
            paints.shape.setAlpha(Math.round(255.0f * alpha));
            canvas.drawPath(gooPath, paints.shape);
            paints.shape.setAlpha(255);
        }
    }

    /** Draws one card whose rectangle {@code r} was already computed. */
    void drawCard(Canvas canvas, Card c, RectF r, long t, float opening) {
        int save=canvas.save();
        if(c.iMode && c.iModeOpacity.value()<.995f)
            canvas.saveLayerAlpha(r,Math.round(255*clamp01(c.iModeOpacity.value())));
        try { drawCardSurface(canvas,c,r,t,opening); }
        finally { canvas.restoreToCount(save); }
    }

    private void drawCardSurface(Canvas canvas, Card c, RectF r, long t, float opening) {
        float corner = c.iMode ? IMode.corner(c,r,m.pillH,m.cardRadius) : c.body.cornerRadius(m.cardRadius, r.height());
        opening = visualOpening(c, opening);

        paints.card.setColor(fillColor(opening));
        canvas.drawRoundRect(r, corner, corner, paints.card);
        paints.cardStroke.setShader(null);
        paints.cardStroke.setColor(Colors.OUTLINE_VARIANT);
        inner.set(r);
        inner.inset(m.outlineW / 2.0f, m.outlineW / 2.0f);
        canvas.drawRoundRect(inner, corner, corner, paints.cardStroke);

        if(c.iMode && scene.iMode!=null) {
            int compactSave=canvas.save();
            clip.reset();clip.addRoundRect(r,corner,corner,Path.Direction.CW);canvas.clipPath(clip);
            scene.iMode.drawCompactCard(canvas,c,r,t);
            canvas.restoreToCount(compactSave);
        }
        float alpha = contentAlpha(c, t, opening);
        if (alpha <= IslandView.MIN_VISIBLE_ALPHA || c.body instanceof RemoteBody && !IMode.paintsRemoteInSurface(c,t)) {
            return;
        }
        int save = canvas.save();
        clip.reset();
        clip.addRoundRect(r, corner, corner, Path.Direction.CW);
        canvas.clipPath(clip);
        float backgroundAlpha=c.iMode && c.item.isMedia ? Easing.easeOutCubic(opening) : alpha;
        drawBackground(c.body, canvas, r, backgroundAlpha);
        try {
            if (!c.leaving && alpha < 1.0f && ModuleSettings.legacyAnimation()) {
                float settle = easeOutBack(alpha);
                float scale = CONTENT_SCALE_FROM + ((1.0f - CONTENT_SCALE_FROM) * settle);
                canvas.translate(0.0f, (1.0f - settle) * m.dp(CONTENT_RISE_DP));
                canvas.scale(scale, scale, r.centerX(), r.top);
            }
            long elapsed = c.leaving || c.handoff != null ? LEAVING_CONTENT_ELAPSED_MS : t - c.contentStart;
            if(c.iMode) {
                float scale=Math.max(.001f,Math.min(r.width()/c.body.w,r.height()/c.body.h));
                canvas.translate(r.centerX()-c.body.w*scale/2,r.centerY()-c.body.h*scale/2);
                canvas.scale(scale,scale);
                if(c.body instanceof RemoteBody) {
                    RemoteBody remote=(RemoteBody)c.body;
                    canvas.translate(remote.pad,remote.pad);
                    int remoteSave=canvas.saveLayerAlpha(0,0,remote.view.getMeasuredWidth(),remote.view.getMeasuredHeight(),Math.round(alpha*255));
                    try { remote.view.draw(canvas); }
                    finally { canvas.restoreToCount(remoteSave); }
                } else drawBody(c.body,canvas,0,0,alpha,elapsed);
            } else if (c.leaving && !ModuleSettings.legacyAnimation()) {
                drawBody(c.body, canvas, r.left + ((r.width() - c.body.w) / 2.0f), r.top, alpha, elapsed);
            } else {
                canvas.scale(r.width() / c.body.w, r.height() / c.body.h, r.left, r.top);
                drawBody(c.body, canvas, r.left, r.top, alpha, elapsed);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    void drawBackground(CardBody body, Canvas canvas, RectF frame, float alpha) {
        try {
            body.drawBackground(canvas, frame, alpha);
        } catch (Throwable t) {
            XLog.once("IslandView.drawBackground", t);
        }
    }

    /** A bad notification payload must never take System UI down with an exception inside onDraw. */
    void drawBody(CardBody body, Canvas canvas, float x, float y, float alpha, long elapsed) {
        try {
            body.draw(canvas, x, y, alpha, elapsed);
        } catch (Throwable t) {
            if (!drawFailureLogged) {
                drawFailureLogged = true;
                XLog.e("card draw failed for " + body.item, t);
            }
        }
    }
}
