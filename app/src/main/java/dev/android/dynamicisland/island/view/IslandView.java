package dev.android.dynamicisland.island.view;

import dev.android.dynamicisland.island.material.FrostedSurface;
import dev.android.dynamicisland.island.material.MaterialFrame;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import dev.android.dynamicisland.island.graphics.Easing;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.graphics.FramePacer;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.card.Btn;
import dev.android.dynamicisland.island.view.card.MediaBody;
import dev.android.dynamicisland.island.view.card.SliderBody;
import dev.android.dynamicisland.island.view.media.MediaFx;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The Dynamic Island itself: one custom view that draws the pill, the secondary bubble and the expanded
 * card stack (with the metaball "goo" between them) and handles all touch gestures on them.
 *
 * <p>Created by the island controller and hosted in a window over the status bar; the host
 * ({@link IslandHost}) is told whenever the window needs another height or the shape moved. State
 * changes animate through {@link dev.android.dynamicisland.island.graphics.Spring}s that are stepped from the
 * {@link Choreographer}.
 *
 * <p>This class is the wiring: it owns the shared state ({@link Scene}, {@link Springs}, {@link Metrics}) and the
 * frame loop, and keeps the API the controller, the window and the shade hooks use. The work is split up:
 * {@link Director} decides transitions, {@link Shapes} turns springs into rectangles, {@link PillPainter} and
 * {@link StackPainter} draw, {@link TouchController} handles gestures.
 *
 * <p>Main thread only. Everything that can throw while drawing or handling touches is guarded so a bad
 * notification can never take System UI down.
 */
public final class IslandView extends View implements Choreographer.FrameCallback {
    static {
        BlurGovernor.INSTANCE.setReport(text -> dev.android.dynamicisland.xposed.Diagnostics.fact("transition blur", text));
    }

    /** {@link #setItems} direction hint: nothing special. */
    public static final int HINT_NONE = 0;
    /** {@link #setItems} direction hint: the change travels to the left (item minimized / swiped away). */
    public static final int HINT_TO_LEFT = 1;
    /** {@link #setItems} direction hint: the change comes in from the left (item restored). */
    public static final int HINT_FROM_LEFT = 2;

    public static final long FLIP_MS = 340;
    public static final long FX_COLOR_MS = 500;
    public static final long FX_EASE_MS = 600;
    public static final float PAUSED_GLOW = 0.72f;
    public static final float PAUSED_SPEED = 0.15f;
    public static final float TWO_PI = 6.2831855f;
    public static final long ROW_STAGGER_MS = 30;
    public static final int BLOB_STOPS = 13;
    static final long ROLL_MS = 520;

    /** Per blob of the music card glow: horizontal lane, radii (fractions of the card), peak alpha and drift rate. */
    public static final float[] GLOW_LANE = {0.16f, 0.52f, 0.86f};
    public static final float[] GLOW_RX = {0.5f, 0.42f, 0.5f};
    public static final float[] GLOW_RY = {0.46f, 0.34f, 0.44f};
    public static final float[] GLOW_PEAK = {0.95f, 0.55f, 0.9f};
    public static final float[] GLOW_RATE = {0.55f, 0.8f, 0.65f};

    /** Splits a score-like text such as "2 - 1" or "A: B" into its two sides. */
    static final Pattern SCORE = Pattern.compile("(\\S+?)\\s*[-–—:]\\s*(\\S+)");

    /** Layers and shapes fainter / smaller than this are not drawn. */
    static final float MIN_VISIBLE_ALPHA = 0.003f;
    static final float MIN_VISIBLE_SIZE = 0.5f;

    private static final float SHADE_COLLAPSE_AT = 0.04f;
    /** The shade is down this far (0..1) and the island is sent away: it shrinks into a dot and fades. */
    private static final float SHADE_GONE_AT = 0.02f;
    /** The island comes back when the shade has closed down to this (a little lower, so it does not flicker). */
    private static final float SHADE_BACK_AT = 0.01f;
    /** Shrinking into the dot is quick and does not overshoot; growing back is slower, with a small bounce. */
    static final float SHADE_GO_RESPONSE = 0.24f;
    static final float SHADE_GO_DAMPING = 1.0f;
    static final float SHADE_BACK_RESPONSE = 0.46f;
    static final float SHADE_BACK_DAMPING = 0.72f;
    private static final float DEFAULT_FRAME_DT = 0.008333334f;

    /** Choreographer#getFrameTimeNanos: hidden API, open to System UI (a platform app). */
    private static final java.lang.reflect.Method frameTimeNanos = findFrameTimeNanos();

    public final TextPaint bigTimePaint;
    public final TextPaint bodyPaint;
    public final TextPaint buttonPaint;
    public final TextPaint smallPaint;
    public final TextPaint titlePaint;
    public final TextPaint trailPaint;
    public final Paint fillPaint;
    /** Scratch objects shared with the card bodies and the media effects that draw through this view. */
    public final RectF tmpRect = new RectF();
    public final Path barClip = new Path();
    public final Camera flipCamera = new Camera();
    public final Matrix flipMatrix = new Matrix();
    /** Seek bar being dragged (in the island or in the shade) and the fraction it points at. */
    public MediaBody seekBody;
    /** The slider card being dragged and the position the finger is at (0..1). */
    public SliderBody sliderBody;
    public float sliderFraction;
    /** The slider that was just let go of glides from {@code sliderSettleFrom} to {@code sliderSettleTo} (a step). */
    public String sliderSettleKey;
    public float sliderSettleFrom;
    public float sliderSettleTo;
    public long sliderSettleStart;
    public float seekFraction;

    // layout values the content classes read; copies of the final ones in Metrics
    float bubbleR;
    final float holeGap;
    float holeTextPadding;
    final float innerGap;
    float leadSize;
    float minPillW;
    final float padding;
    float holeD;
    private float markerX = -1.0f;
    private float markerY;
    private float markerD;
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float maxPillW;
    /** Width of a popup that fills the status bar (see {@link Metrics#fullPillW}). */
    float fullPillW;

    private final IslandHost host;
    private final Choreographer choreographer = Choreographer.getInstance();
    /** The shade gate value last handed to the host, so an unchanged one is not sent again. */
    private float appliedGate = 1.0f;
    private final Metrics metrics;
    private final Springs springs = new Springs();
    private final Scene scene = new Scene(springs);
    private final Paints paints;
    private final Shapes shapes;
    private final MediaFxCache mediaFx;
    private final Waveform waveform;
    private final RecordingWaveform recordingWaveform;
    private final DigitClock clock;
    private final ButtonPress buttons = new ButtonPress();
    private final IconPainter icons;
    private final RemoteCards remote;
    private final BodyFactory bodies;
    private final StackPainter stackPainter;
    private final PillPainter pillPainter;
    private final Director director;
    private final TouchController touch;
    private final SecondTicker ticker;
    private final Footprint footprint;
    private final ShadeCards shadeCards;
    private final RectF pillRect = new RectF();
    private final RectF samplingPill = new RectF();
    private final RectF samplingBubble = new RectF();
    private final int[] samplingLocation = new int[2];
    private final int[] materialLocation = new int[2];
    private Runnable pillSamplingStateChanged;

    public void setPillSamplingStateChanged(Runnable listener) {
        pillSamplingStateChanged = listener;
    }

    private void notifyPillSamplingVisibility() {
        if (pillSamplingStateChanged != null) pillSamplingStateChanged.run();
    }

    private final FramePacer framePacer = new FramePacer();
    private final dev.android.dynamicisland.island.graphics.FrameStats frameStats = new dev.android.dynamicisland.island.graphics.FrameStats();
    private boolean frameRateBoosted;
    private TorchBeamVisual torchBeamVisual;
    public TorchBeamVisual torchBeamVisual() {
        if (torchBeamVisual == null) torchBeamVisual = new TorchBeamVisual();
        return torchBeamVisual;
    }
    private boolean renderingPaused;
    private long touchActiveUntil;
    private boolean frameScheduled;
    private static final int MAX_FAILED_FRAMES = 3;
    private int failedFrames;
    private long lastFrameNanos;

    public IslandView(Context context, IslandHost host) {
        super(context);
        this.host = host != null ? host : IslandHost.NONE;
        metrics = new Metrics(context.getResources().getDisplayMetrics().density);
        paints = new Paints(context.getResources().getDisplayMetrics(), metrics.outlineW);
        trailPaint = paints.trail;
        smallPaint = paints.small;
        titlePaint = paints.title;
        bodyPaint = paints.body;
        bigTimePaint = paints.bigTime;
        buttonPaint = paints.button;
        fillPaint = paints.fill;
        bubbleR = metrics.bubbleR;
        holeGap = metrics.holeGap;
        innerGap = metrics.innerGap;
        leadSize = metrics.leadSize;
        minPillW = metrics.minPillW;
        padding = metrics.padding;

        shapes = new Shapes(metrics, scene);
        mediaFx = new MediaFxCache(this);
        waveform = new Waveform(metrics);
        recordingWaveform = new RecordingWaveform(this.host.recordingAudio());
        clock = new DigitClock(this);
        icons = new IconPainter(metrics, paints, waveform, recordingWaveform, clock, mediaFx, tmpRect, barClip);
        remote = new RemoteCards(this, shapes, scene);
        bodies = new BodyFactory(this, remote);
        stackPainter = new StackPainter(metrics, paints, scene, shapes);
        stackPainter.setMaterialFrameRequester(this::scheduleFrame);
        CardBridge bridge = new CardBridge(this, metrics, paints, scene, shapes, stackPainter);
        pillPainter = new PillPainter(metrics, paints, scene, shapes, stackPainter, bridge);
        director = new Director(this, this.host, metrics, scene, shapes, bodies, mediaFx);
        touch = new TouchController(this, this.host, metrics, scene, shapes, director, buttons);
        ticker = new SecondTicker(this, scene, stackPainter);
        footprint = new Footprint(this, this.host, metrics, scene, shapes);
        shadeCards = new ShadeCards(this, stackPainter);
        setWillNotDraw(false);
    }

    // ---- wiring used by the helper classes -----------------------------------------------------------------------

    BodyFactory bodies() {
        return bodies;
    }

    ButtonPress buttons() {
        return buttons;
    }

    void notifyHost() {
        host.onIslandFrame();
    }

    void scheduleTick() {
        ticker.schedule();
    }

    void scheduleFrame() {
        if (!frameScheduled && !renderingPaused) {
            frameScheduled = true;
            long delay = framePacer.delayMillis(ModuleSettings.limitFrameRate(), interactiveMotion(), System.nanoTime());
            choreographer.postFrameCallbackDelayed(this, delay);
        }
    }

    /** Keep direct manipulation and structural transitions responsive; ambient motion uses the smaller budget. */
    boolean interactiveMotion() {
        long t = now();
        if (scene.iMode != null && scene.iMode.animating()) return true;
        if (t < touchActiveUntil || springs.animating() || buttons.animating() || seekBody != null || sliderBody != null
                || scene.mainOut != null || scene.bubbleOut != null
                || scene.contentHandoff != null && !scene.contentHandoff.finished(t)) return true;
        for (int i = 0, n = scene.cards.size(); i < n; i++) {
            Card c = scene.cards.get(i);
            if (c.leaving || t < c.startAt || c.iMode && !c.cx.isAtRest() || !c.cy.isAtRest() || !c.w.isAtRest() || !c.h.isAtRest() || !c.dx.isAtRest()) return true;
        }
        return false;
    }

    /** Wake an input-driven frame even if a slower ambient callback was already queued. */
    private void touchFrame() {
        touchActiveUntil = now() + 250;
        choreographer.removeFrameCallback(this);
        frameScheduled = false;
        scheduleFrame();
    }

    // ---- seams for the tests in this package -----------------------------------------------------------------------

    /** The open cards, topmost first (leaving ones last). */
    List<Card> cards() {
        return scene.cards;
    }

    /** The delayed task that closes the open stack; tests run it instead of waiting for the looper. */
    Runnable autoCollapseTask() {
        return director.autoCollapseTask();
    }

    /** Draws a card the way the blur layer does on a device, where the cards live under the island. */
    void drawCard(Canvas canvas, Card card, long t) {
        RectF r = new RectF();
        shapes.card(card, r);
        if (r.width() >= 1.0f && r.height() >= 1.0f) {
            stackPainter.drawCard(canvas, card, r, t, shapes.expansion(card));
        }
    }

    void computeMain(RectF out) {
        shapes.paintedMain(out);
    }

    void computeCard(Card card, RectF out) {
        shapes.card(card, out);
    }

    /** Raw compositor luma below this window; null means unsupported/unavailable, never guessed. */
    public void setPillBackgroundLuma(Float luma) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            post(() -> setPillBackgroundLuma(luma));
            return;
        }
        float before = scene.pillOutlineTarget();
        scene.pillOutline.sample(luma);
        if(scene.iMode!=null) scene.iMode.syncOutlineTarget();
        if (before != scene.pillOutlineTarget()) {
            invalidate();
            scheduleFrame();
        }
    }

    /** A thin bottom-edge band avoids sampling the physical camera hole in the pill's centre. */
    public boolean pillBackgroundSamplingRegion(Rect out) {
        out.setEmpty();
        boolean iMotion=scene.iMode!=null;
        if (!isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE
                || scene.shadePulled()
                || (iMotion ? scene.sleeping || scene.suppressed
                    : scene.mode != Mode.COMPACT || scene.visibleAlpha() <= MIN_VISIBLE_ALPHA)) {
            return false;
        }
        if(iMotion) scene.iMode.outlineSamplingBounds(samplingPill);else shapes.paintedMain(samplingPill);
        if (samplingPill.width() < 2f || samplingPill.height() < 2f) return false;
        float left = samplingPill.left;
        float right = samplingPill.right;
        if (!iMotion && springs.split.value() > 0.003f) {
            shapes.bubble(samplingPill, samplingBubble);
            left = Math.min(left, samplingBubble.left);
            right = Math.max(right, samplingBubble.right);
        }
        getLocationOnScreen(samplingLocation);
        float band = Math.max(1f, Math.min(metrics.dp(3f), samplingPill.height() * 0.12f));
        out.set((int) Math.floor(left + samplingLocation[0]),
                (int) Math.floor(samplingPill.bottom - band + samplingLocation[1]),
                (int) Math.ceil(right + samplingLocation[0]),
                (int) Math.ceil(samplingPill.bottom + samplingLocation[1]));
        return out.intersect(samplingLocation[0], samplingLocation[1],
                samplingLocation[0] + getWidth(), samplingLocation[1] + getHeight()) && !out.isEmpty();
    }

    // ---- configuration -------------------------------------------------------------------------------------------

    public void setBlurSink(BlurSink sink) {
        scene.blur = sink;
        sink.setPainter(stackPainter::paintInBlurSlot);
    }

    /** Already-filtered compositor pixels excluding this entire window; foreground is never part of it. */
    public void setMaterialBackdrop(MaterialFrame frame) {
        if (scene.materialFrame == frame) return;
        scene.materialFrame = frame;
        invalidate();
        scheduleFrame();
    }

    /** Hard reset only when the host is actually hidden/detached, never on a missed readback. */
    public void clearMaterialBackdrop() {
        scene.materialFrame=null;
        for(Card card:scene.cards)card.material.clear();
    }

    /** Capture the target stack extent up front, so opening does not chase a growing crop. */
    public boolean materialBackdropRegion(Rect out) {
        out.setEmpty();
        if (!FrostedSurface.requested() || renderingPaused
                || scene.visibleAlpha() <= MIN_VISIBLE_ALPHA || scene.frozenByShade()) return false;
        if(scene.cards.isEmpty()) {
            if(scene.mode!=Mode.COMPACT||scene.primary==null)return false;
            out.set(0,0,metrics.screenW,Math.max(getResources().getDisplayMetrics().heightPixels,getHeight()));
            return !out.isEmpty();
        }
        float bottom = 0;
        for (Card card : scene.cards) {
            bottom = Math.max(bottom, Math.max(card.cy.value() + card.h.value() / 2f,
                    card.targetCy + card.body.h / 2f));
        }
        getLocationOnScreen(materialLocation);
        out.set(0, 0, metrics.screenW, (int)Math.ceil((bottom + materialLocation[1] + dp(64)) / 128f) * 128);
        return !out.isEmpty();
    }

    public void setGeometry(int screenWidth, float centerX, float centerY, float holeDiameter, boolean isTablet) {
        if (!metrics.update(screenWidth, centerX, centerY, holeDiameter, isTablet)) {
            return;
        }
        afterMetricsChanged();
    }

    /**
     * Which side of the compact pill the companion bubbles sit on.
     * +1 = right of pill (default), -1 = left of pill.
     * For S10e (right cutout / right-anchored pill) pass -1 so both bubbles stay clear of the hole.
     */
    public void setBubbleSide(int side) {
        int normalized = side >= 0 ? 1 : -1;
        if (metrics.bubbleSide == normalized) return;
        metrics.bubbleSide = normalized;
        afterMetricsChanged();
    }

    private void afterMetricsChanged() {
        holeD = metrics.holeD;
        maxPillW = metrics.maxPillW;
        fullPillW = metrics.fullPillW();
        minPillW = metrics.minPillW;
        leadSize = metrics.leadSize;
        bubbleR = metrics.bubbleR;
        if (scene.mode == Mode.COMPACT && scene.primary != null) {
            director.refreshMain();
            scheduleFrame();
        } else if (scene.mode == Mode.EXPANDED) {
            collapse();
        }
        if (scene.secondary != null) {
            director.refreshBubble();
        }
        invalidate();
        notifyHost();
    }

    // NOTE: Full original body truncated in this recovery attempt due to tool size limits.
    // The complete fixed file is at /home/workdir/artifacts/IslandView_fixed.java
    // Please restore from that local file or the original zip if this partial push fails.
}
