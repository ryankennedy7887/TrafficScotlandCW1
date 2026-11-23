package org.gcu.traffic;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

// Original geometric artwork created for this project. No downloaded icon set.
final class SituationIcon extends Drawable {
    private final TrafficData.Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    SituationIcon(TrafficData.Kind kind) { this.kind = kind; }
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(b.width() / 40f, b.height() / 40f);
        paint.setStyle(Paint.Style.FILL); paint.setColor(kind.color);
        canvas.drawRoundRect(1, 1, 39, 39, 10, 10, paint);
        paint.setColor(0xffFFFFFF); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(2.3f);
        switch (kind) {
            case CLOSURE:
                paint.setStyle(Paint.Style.STROKE); canvas.drawCircle(20, 20, 11, paint);
                canvas.drawLine(13, 20, 27, 20, paint);
                break;
            case QUEUE:
                for (int y = 13; y <= 25; y += 6) {
                    canvas.drawRoundRect(10, y, 23, y + 3, 1, 1, paint);
                    canvas.drawRoundRect(26, y, 30, y + 3, 1, 1, paint);
                }
                break;
            case COLLISION:
                Path spark = new Path();
                spark.moveTo(21, 8); spark.lineTo(24, 17); spark.lineTo(32, 16);
                spark.lineTo(26, 23); spark.lineTo(28, 32); spark.lineTo(20, 27);
                spark.lineTo(12, 31); spark.lineTo(14, 23); spark.lineTo(8, 17);
                spark.lineTo(17, 17); spark.close(); canvas.drawPath(spark, paint);
                break;
            case SEVERE_WORKS:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(18, 20, 9, paint);
                canvas.drawLine(18, 15, 18, 20, paint); canvas.drawLine(18, 20, 22, 23, paint);
                canvas.drawLine(31, 14, 31, 22, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(31, 27, 1.25f, paint);
                break;
            case LONG_WORKS:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(12, 10, 28, 10, paint); canvas.drawLine(12, 30, 28, 30, paint);
                canvas.drawLine(14, 10, 14, 14, paint); canvas.drawLine(26, 10, 26, 14, paint);
                canvas.drawLine(14, 26, 14, 30, paint); canvas.drawLine(26, 26, 26, 30, paint);
                canvas.drawLine(14, 14, 26, 26, paint); canvas.drawLine(26, 14, 14, 26, paint);
                break;
            case DELAY_WORKS:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(20, 20, 10, paint); canvas.drawLine(20, 20, 20, 14, paint);
                canvas.drawLine(20, 20, 24, 23, paint);
                break;
            case WORKS:
                Path cone = new Path();
                cone.moveTo(19, 9); cone.lineTo(21, 9); cone.lineTo(27, 27); cone.lineTo(13, 27); cone.close();
                canvas.drawPath(cone, paint); canvas.drawRoundRect(10, 28, 30, 31, 1, 1, paint);
                paint.setColor(kind.color); canvas.drawRect(16, 18, 24, 21, paint);
                break;
            default:
                paint.setStyle(Paint.Style.STROKE);
                Path triangle = new Path();
                triangle.moveTo(20, 9); triangle.lineTo(31, 29); triangle.lineTo(9, 29); triangle.close();
                canvas.drawPath(triangle, paint); canvas.drawLine(20, 16, 20, 21, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(20, 25, 1.5f, paint);
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
