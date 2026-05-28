package io.wanjuan.app.ui.book;

public final class ProgressMinimapDragCalculator {

    private ProgressMinimapDragCalculator() {
    }

    public static float dragTouchOffset(
            boolean touchInsideThumb,
            float touchY,
            float thumbTop,
            float thumbHeight
    ) {
        float safeThumbHeight = Math.max(0f, thumbHeight);
        if (!touchInsideThumb) {
            return safeThumbHeight / 2f;
        }
        return coerce(touchY - thumbTop, 0f, safeThumbHeight);
    }

    public static float ratioForY(
            float touchY,
            float dragTouchOffset,
            float trackTop,
            float trackBottom,
            float thumbHeight
    ) {
        float safeThumbHeight = Math.max(0f, thumbHeight);
        float trackHeight = Math.max(0f, trackBottom - trackTop);
        if (trackHeight <= 0f) {
            return 0f;
        }
        float travel = Math.max(trackHeight - safeThumbHeight, 1f);
        float thumbTop = coerce(touchY - dragTouchOffset, trackTop, trackBottom - safeThumbHeight);
        return coerce((thumbTop - trackTop) / travel, 0f, 1f);
    }

    public static float ratioForTrackY(
            float touchY,
            float trackTop,
            float trackBottom
    ) {
        float trackHeight = Math.max(0f, trackBottom - trackTop);
        if (trackHeight <= 0f) {
            return 0f;
        }
        return coerce((touchY - trackTop) / trackHeight, 0f, 1f);
    }

    private static float coerce(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
