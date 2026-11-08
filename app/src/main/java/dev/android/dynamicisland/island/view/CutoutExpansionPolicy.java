package dev.android.dynamicisland.island.view;

/** Pure geometry policy for keeping automatic card expansion away from a physical camera cutout. */
public final class CutoutExpansionPolicy {
    private CutoutExpansionPolicy() {}

    public static boolean overlaps(float left, float top, float right, float bottom,
            int holeLeft, int holeTop, int holeRight, int holeBottom) {
        if (right <= left || bottom <= top || holeRight <= holeLeft || holeBottom <= holeTop) {
            return false;
        }
        return right > holeLeft && left < holeRight && bottom > holeTop && top < holeBottom;
    }
}
