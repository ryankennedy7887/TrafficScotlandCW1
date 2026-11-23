package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;
import org.gcu.traffic.TrafficData.Record;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.json.JSONArray;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScotlandMapView extends View {
    interface Open { void open(Situation situation); }
    private static final Point INVERNESS = new Point(57.4778, -4.2247);
    private static final Point ABERDEEN = new Point(57.1497, -2.0943);
    private static final Point EDINBURGH = new Point(55.9533, -3.1883);
    private static final Point DUMFRIES = new Point(55.0709, -3.6051);
    private static final double LAT_SCALE = 1.8;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path land = new Path();
    private final List<Pin> pins = new ArrayList<>();
    private final GestureDetector gestures;
    private final ScaleGestureDetector pinch;
    private List<Situation> previous = Collections.emptyList();
    private int radius, located;
    private float units = 1, shiftX, shiftY;
    private double lon = -4.0, lat = 57.0;
    private boolean scaling;
    private Open action;

    public ScotlandMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setContentDescription("Map of situations. The Results list provides the same situations as selectable text.");
        setFocusable(true);
        setClickable(true);
        try (InputStream input = context.getAssets().open("basemap.json")) {
            JSONArray shapes = new JSONArray(TrafficRepository.read(input));
            for (int i = 0; i < shapes.length(); i++) {
                JSONArray polys = shapes.getJSONObject(i).getJSONArray("polygons");
                for (int j = 0; j < polys.length(); j++) {
                    JSONArray rings = polys.getJSONArray(j);
                    for (int k = 0; k < rings.length(); k++) {
                        JSONArray ring = rings.getJSONArray(k);
                        for (int p = 0; p < ring.length(); p++) {
                            JSONArray xy = ring.getJSONArray(p);
                            float x = (float) xy.getDouble(0), y = (float) (-xy.getDouble(1) * LAT_SCALE);
                            if (p == 0) land.moveTo(x, y); else land.lineTo(x, y);
                        }
                        land.close();
                    }
                }
            }
        } catch (Exception ignored) { /* Points remain usable if the map asset is unavailable. */ }
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                scaling = true;
                return true;
            }
        });
        pinch.setQuickScaleEnabled(false);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (!scaling) { shiftX -= dx; shiftY -= dy; invalidate(); }
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                zoom(1.7f, e.getX(), e.getY()); return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                Pin closest = null;
                double best = dp(25);
                for (Pin p : pins) {
                    double distance = Math.hypot(e.getX() - x(p.center), e.getY() - y(p.center));
                    if (distance < best) { best = distance; closest = p; }
                }
                if (closest != null && action != null) action.open(closest.situation);
                return true;
            }
        });
    }

    int setSituations(List<Situation> results, int radius) {
        if (previous.equals(results) && this.radius == radius) return located;
        previous = new ArrayList<>(results);
        this.radius = radius;
        pins.clear();
        located = 0;
        for (Situation s : results) {
            boolean found = false;
            for (Record record : s.records) for (Location l : record.locations) {
                Point from = l.at("from"), to = l.at("to");
                if (from != null && to != null) {
                    pins.add(new Pin(s, new Point((from.lat + to.lat) / 2, (from.lon + to.lon) / 2), from, to));
                    found = true;
                }
                for (Place p : l.places) {
                    if (p.point == null || (from != null && to != null && ("from".equals(p.role) || "to".equals(p.role)))) continue;
                    pins.add(new Pin(s, p.point, null, null)); found = true;
                }
            }
            if (found) located++;
        }
        fit();
        return located;
    }

    void onResult(Open open) { action = open; }

    void fit() {
        shiftX = shiftY = 0;
        double west = -8.1, east = -0.7, south = 54.6, north = 60.9;
        if (!pins.isEmpty()) {
            west = south = Double.POSITIVE_INFINITY;
            east = north = Double.NEGATIVE_INFINITY;
            for (Pin p : pins) {
                Point[] points = p.start == null ? new Point[]{p.center} : new Point[]{p.start, p.end};
                for (Point q : points) {
                    west = Math.min(west, q.lon); east = Math.max(east, q.lon);
                    south = Math.min(south, q.lat); north = Math.max(north, q.lat);
                }
            }
            if (radius > 0) {
                double dy = radius / 111.2, dx = dy * LAT_SCALE;
                west = Math.min(west, GLASGOW.lon - dx); east = Math.max(east, GLASGOW.lon + dx);
                south = Math.min(south, GLASGOW.lat - dy); north = Math.max(north, GLASGOW.lat + dy);
            }
        }
        lon = (west + east) / 2; lat = (south + north) / 2;
        units = (float) Math.max(1, Math.min(
                Math.max(1, getWidth() - dp(64)) / Math.max(0.065, east - west),
                Math.max(1, getHeight() - dp(64)) / (Math.max(0.038, north - south) * LAT_SCALE)));
        invalidate();
    }

    private void zoom(float multiplier, float fx, float fy) {
        float prev = units;
        units = Math.max(4, Math.min(12000, units * multiplier));
        float ratio = units / prev, mx = getWidth() / 2f, my = getHeight() / 2f;
        shiftX = fx - mx - (fx - mx - shiftX) * ratio;
        shiftY = fy - my - (fy - my - shiftY) * ratio;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { if (w > 0 && h > 0) fit(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) scaling = false;
        pinch.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xffdcebee);
        canvas.save();
        canvas.translate(x(new Point(0, 0)), y(new Point(0, 0)));
        canvas.scale(units, units);
        ink.setStyle(Paint.Style.FILL); ink.setColor(0xffedf0d7); canvas.drawPath(land, ink);
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(dp(1) / units);
        ink.setColor(0xffaabf9f); canvas.drawPath(land, ink);
        canvas.restore();
        city(canvas, GLASGOW, "Glasgow"); city(canvas, EDINBURGH, "Edinburgh");
        city(canvas, INVERNESS, "Inverness"); city(canvas, ABERDEEN, "Aberdeen"); city(canvas, DUMFRIES, "Dumfries");
        if (radius > 0) {
            float px = (float) (radius / 111.2 * LAT_SCALE * units);
            ink.setStyle(Paint.Style.FILL); ink.setColor(0x22116c64);
            canvas.drawCircle(x(GLASGOW), y(GLASGOW), px, ink);
            ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(dp(1)); ink.setColor(0xff116c64);
            canvas.drawCircle(x(GLASGOW), y(GLASGOW), px, ink);
        }
        ink.setStrokeCap(Paint.Cap.ROUND);
        for (Pin p : pins) {
            if (p.start == null) continue;
            ink.setColor(p.situation.kind().color); ink.setStrokeWidth(dp(3));
            canvas.drawLine(x(p.start), y(p.start), x(p.end), y(p.end), ink);
        }
        ink.setStyle(Paint.Style.FILL);
        for (Pin p : pins) {
            float x = x(p.center), y = y(p.center);
            ink.setColor(0xffFFFFFF); canvas.drawCircle(x, y, dp(6), ink);
            ink.setColor(p.situation.kind().color); canvas.drawCircle(x, y, dp(4.3f), ink);
        }
        ink.setColor(0xff234854); ink.setTextSize(dp(13));
        canvas.drawText("↑ N", dp(12), dp(22), ink);
        if (pins.isEmpty()) canvas.drawText("No mapped results", dp(12), getHeight() - dp(12), ink);
    }
    private void city(Canvas canvas, Point p, String label) {
        float x = x(p), y = y(p);
        ink.setStyle(Paint.Style.FILL); ink.setColor(0xff4d666e); ink.setTextSize(dp(10));
        canvas.drawCircle(x, y, dp(2), ink);
        canvas.drawText(label, x + dp(4), y - dp(4), ink);
    }
    private float x(Point p) { return getWidth() / 2f + shiftX + (float) ((p.lon - lon) * units); }
    private float y(Point p) { return getHeight() / 2f + shiftY - (float) ((p.lat - lat) * LAT_SCALE * units); }
    private float dp(float d) { return d * getResources().getDisplayMetrics().density; }

    private static final class Pin {
        final Situation situation;
        final Point center, start, end;
        Pin(Situation situation, Point center, Point start, Point end) {
            this.situation = situation; this.center = center; this.start = start; this.end = end;
        }
    }
}
