package com.oru.app.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.oru.app.R;

/**
 * The picture of the sound in the clip editor: bars for the levels, the kept
 * part in flame, and the cut-away parts dimmed.
 */
public final class WaveformView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] peaks = new float[0];
    private double start;
    private double end;
    private double fadeIn;
    private double fadeOut;
    private double total;

    public WaveformView(Context context, @Nullable AttributeSet attributes) {
        super(context, attributes);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setPeaks(float[] values) {
        peaks = values != null ? values : new float[0];
        invalidate();
    }

    public void setSelection(double duration, double from, double to,
            double fadeInSeconds, double fadeOutSeconds) {
        total = duration;
        start = from;
        end = to;
        fadeIn = fadeInSeconds;
        fadeOut = fadeOutSeconds;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int ink = ContextCompat.getColor(getContext(), R.color.ink);
        int flame = ContextCompat.getColor(getContext(), R.color.flame);
        int night = ContextCompat.getColor(getContext(), R.color.night);

        if (peaks.length == 0 || total <= 0) {
            paint.setColor(ink);
            paint.setAlpha(40);
            canvas.drawRect(0, height / 2f - 1, width, height / 2f + 1, paint);
            paint.setAlpha(255);
            return;
        }

        float barWidth = width / (float) peaks.length;
        float middle = height / 2f;
        for (int index = 0; index < peaks.length; index++) {
            double at = (index / (double) peaks.length) * total;
            boolean kept = at >= start && at <= end;
            float amplitude = Math.max(peaks[index], 0.02f) * (height / 2f);
            paint.setColor(kept ? flame : ink);
            paint.setAlpha(kept ? 255 : 60);
            canvas.drawRect(index * barWidth, middle - amplitude,
                    index * barWidth + Math.max(barWidth - 1f, 1f),
                    middle + amplitude, paint);
        }
        paint.setAlpha(255);

        float startX = (float) ((start / total) * width);
        float endX = (float) ((end / total) * width);
        paint.setColor(night);
        paint.setAlpha(120);
        canvas.drawRect(0, 0, startX, height, paint);
        canvas.drawRect(endX, 0, width, height, paint);
        paint.setAlpha(255);

        float span = Math.max(endX - startX, 1f);
        paint.setColor(flame);
        paint.setAlpha(70);
        if (fadeIn > 0) {
            float fade = (float) Math.min((fadeIn / Math.max(end - start, 0.01)) * span, span);
            canvas.drawRect(startX, 0, startX + fade, height, paint);
        }
        if (fadeOut > 0) {
            float fade = (float) Math.min((fadeOut / Math.max(end - start, 0.01)) * span, span);
            canvas.drawRect(endX - fade, 0, endX, height, paint);
        }
        paint.setAlpha(255);

        float density = getResources().getDisplayMetrics().density;
        paint.setColor(flame);
        canvas.drawRect(startX - density, 0, startX + density, height, paint);
        canvas.drawRect(endX - density, 0, endX + density, height, paint);
    }
}
