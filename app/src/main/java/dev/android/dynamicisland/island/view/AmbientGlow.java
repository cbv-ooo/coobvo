package dev.android.dynamicisland.island.view;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Crisp animated colour halo for the island. The halo is made from discrete stroked contours; it uses no
 * BlurMaskFilter, bitmap blur, RenderEffect, or sampled backdrop.
 */
public final class AmbientGlow {
    private static final float[] OFFSETS_DP = {2.0f, 5.0f, 8.0f};
    private static final float[] OPACITIES = {0.46f, 0.27f, 0.13f};

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF outline = new RectF();
    private final Path cutout = new Path();
    private final Matrix gradientMatrix = new Matrix();
    private LinearGradient gradient;
    private int first, middle, last;

    public AmbientGlow(float density) {
        this.density = Math.max(1f, density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void draw(Canvas canvas, RectF body, int colour, float alpha, int strength) {
        if (body.isEmpty() || alpha <= 0.001f || strength <= 0) return;
        paint.setShader(null);
        paint.setColor(colour | 0xFF000000);
        drawContours(canvas, body, alpha, strength, 1f);
    }

    public void draw(Canvas canvas, RectF body, GlowMotion motion, float alpha, int strength) {
        if (body.isEmpty() || alpha <= 0.001f || strength <= 0) return;
        int a = motion.colour(0), b = motion.colour(1), c = motion.colour(2);
        if (gradient == null || a != first || b != middle || c != last) {
            first = a;
            middle = b;
            last = c;
            gradient = new LinearGradient(0, 0, 1, 0,
                    new int[]{a, b, c, b, a}, null, Shader.TileMode.CLAMP);
        }
        float period = Math.max(1f, body.width() * 1.35f);
        gradientMatrix.setScale(period, 1f);
        gradientMatrix.postTranslate(body.left - period * motion.phase(), 0f);
        gradient.setLocalMatrix(gradientMatrix);
        paint.setShader(gradient);
        drawContours(canvas, body, alpha * motion.opacity(), strength, motion.spread());
    }

    private void drawContours(Canvas canvas, RectF body, float alpha, int strength, float spread) {
        float intensity = (float) Math.sqrt(Math.min(100, strength) / 100f);
        float scale = body.height() / Math.max(1f, density * 30f);
        float maxOffset = density * 8f * scale * Math.max(0.35f, spread);
        int save = canvas.save();
        cutout.reset();
        cutout.addRoundRect(body, body.height() / 2f, body.height() / 2f, Path.Direction.CW);
        canvas.clipOutPath(cutout);
        for (int i = 0; i < OFFSETS_DP.length; i++) {
            float offset = Math.min(maxOffset, density * OFFSETS_DP[i] * scale * Math.max(0.35f, spread));
            outline.set(body);
            outline.inset(-offset, -offset);
            paint.setStrokeWidth(Math.max(1f, density * 1.6f * scale));
            paint.setAlpha(Math.round(255f * Math.min(1f, Math.max(0f, alpha))
                    * intensity * OPACITIES[i]));
            float radius = body.height() / 2f + offset;
            canvas.drawRoundRect(outline, radius, radius, paint);
        }
        canvas.restoreToCount(save);
    }
}
