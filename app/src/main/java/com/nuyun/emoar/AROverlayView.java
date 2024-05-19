package com.nuyun.emoar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class AROverlayView extends View {
    private Paint paint;
    private RectF faceRect;

    public AROverlayView(Context context) {
        super(context);
        init();
    }

    public AROverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AROverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5.0f);
    }

    public void updateFaceRect(RectF faceRect) {
        this.faceRect = faceRect;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faceRect != null) {
            canvas.drawRect(faceRect, paint);
        }
    }
}
