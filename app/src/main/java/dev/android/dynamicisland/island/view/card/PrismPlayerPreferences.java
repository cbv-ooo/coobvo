package dev.android.dynamicisland.island.view.card;

import org.json.JSONObject;

/**
 * Small, validated view of Prism's player preferences exported by the Morphe bridge.
 *
 * The island intentionally consumes only controls that have a meaningful equivalent in its
 * fixed expanded media card. Unsupported Prism page layouts are kept in the raw bridge snapshot.
 */
final class PrismPlayerPreferences {
    static final String TIME_BOTH = "BOTH";
    static final String TIME_ELAPSED = "ELAPSED";
    static final String TIME_LEFT = "LEFT";
    static final String TIME_NONE = "NONE";

    final String playerTimes;
    final boolean showTrackLength;
    final String scrubberStyle;
    final String scrubberColor;
    final String scrubberThumb;
    final float scrubberThickness;
    final String titleAlign;
    final String titleSize;
    final String titleWeight;
    final boolean titleScroll;
    final int artCornersDp;
    final String coverSize;
    final String coverShape;

    private PrismPlayerPreferences(JSONObject player) {
        playerTimes = choice(player, "playerTimes", TIME_BOTH,
                TIME_BOTH, TIME_ELAPSED, TIME_LEFT, TIME_NONE);
        showTrackLength = player.optBoolean("showTrackLength", false);
        scrubberStyle = choice(player, "scrubberStyle", "HAIRLINE",
                "HAIRLINE", "BOLD", "THUMB", "WAVE", "RIBBONS");
        scrubberColor = choice(player, "scrubberColor", "SPECTRUM",
                "SPECTRUM", "ACCENT", "WHITE", "COVER");
        scrubberThumb = choice(player, "scrubberThumb", "AUTO",
                "AUTO", "NONE", "DOT", "LINE", "RING");
        scrubberThickness = clamp((float) player.optDouble("scrubberThickness", 1.0d), 0.6f, 2.0f);
        titleAlign = choice(player, "playerTitleAlign", "START", "START", "CENTER");
        titleSize = choice(player, "playerTitleSize", "MEDIUM", "SMALL", "MEDIUM", "LARGE");
        JSONObject type = player.optJSONObject("type");
        titleWeight = choice(type == null ? player : type, "playerTitleWeight", "LIGHT",
                "LIGHT", "REGULAR", "BOLD");
        titleScroll = player.optBoolean("playerTitleScroll", true);
        artCornersDp = Math.max(0, Math.min(32, player.optInt("playerArtCorners", 20)));
        coverSize = choice(player, "coverSize", "STANDARD", "STANDARD", "LARGE", "FULL");
        JSONObject shapes = player.optJSONObject("shapes");
        coverShape = choice(shapes == null ? player : shapes, "player",
                player.optString("coverShape", "ROUNDED"),
                "ROUNDED", "SQUARE", "CIRCLE", "SQUIRCLE", "FLOWER", "COOKIE", "ARCH", "SLANTED");
    }

    static PrismPlayerPreferences fromLyricsLook(String raw) {
        if (raw == null || raw.trim().isEmpty()) return defaults();
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject player = root.optJSONObject("playerPreferences");
            // Older bridge snapshots flattened player fields into the root.
            return new PrismPlayerPreferences(player == null ? root : player);
        } catch (Exception ignored) {
            return defaults();
        }
    }

    static PrismPlayerPreferences defaults() {
        return new PrismPlayerPreferences(new JSONObject());
    }

    boolean showElapsedTime() {
        return TIME_BOTH.equals(playerTimes) || TIME_ELAPSED.equals(playerTimes);
    }

    boolean showRightTime() {
        return TIME_BOTH.equals(playerTimes) || TIME_LEFT.equals(playerTimes);
    }

    boolean showRemainingTime() {
        return showRightTime() && !showTrackLength;
    }

    int titleFontWeight() {
        if ("BOLD".equals(titleWeight)) return 700;
        // Prism's LIGHT default leaves the large title to its normal Material typography weight.
        return 400;
    }

    int artistFontWeight() {
        return "BOLD".equals(titleWeight) ? 600 : 400;
    }

    String effectiveThumb() {
        if (!"AUTO".equals(scrubberThumb)) return scrubberThumb;
        return "THUMB".equals(scrubberStyle) || "RIBBONS".equals(scrubberStyle) ? "DOT" : "NONE";
    }

    float titleScale() {
        switch (titleSize) {
            case "SMALL": return 0.82f;
            case "LARGE": return 1.2f;
            default: return 1.0f;
        }
    }

    private static String choice(JSONObject source, String key, String fallback, String... allowed) {
        String value = source.optString(key, fallback);
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value)) return candidate;
        }
        return fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
