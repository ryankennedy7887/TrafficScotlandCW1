package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;
import org.gcu.traffic.TrafficData.Record;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DetailsFragment extends Fragment {
    private TrafficViewModel model;
    private TextView title, caption;
    private View mapPanel;
    private Button action;
    private LinearLayout body;
    private ScrollView scroll;
    private ScotlandMapView map;
    private Situation shown;
    private boolean renderedDetails;

    public DetailsFragment() { super(R.layout.fragment_details); }

    @Override public void onViewCreated(@NonNull View v, Bundle state) {
        model = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);
        title = v.findViewById(R.id.panel_title);
        caption = v.findViewById(R.id.map_caption);
        scroll = v.findViewById(R.id.detail_scroll);
        body = v.findViewById(R.id.detail_body);
        mapPanel = v.findViewById(R.id.map_panel);
        map = v.findViewById(R.id.map_view);
        action = v.findViewById(R.id.panel_action);
        shown = null; renderedDetails = false;
        v.findViewById(R.id.back_results).setOnClickListener(w -> model.screen("", false));
        map.onResult(s -> model.screen(s.key(), false));
        model.snapshots().observe(getViewLifecycleOwner(), x -> render());
        model.filters().observe(getViewLifecycleOwner(), x -> { if (model.screen().map) render(); });
        model.screens().observe(getViewLifecycleOwner(), x -> render());
    }

    private void render() {
        TrafficViewModel.Screen screen = model.screen();
        Situation s = model.selected();
        mapPanel.setVisibility(screen.map ? View.VISIBLE : View.GONE);
        scroll.setVisibility(screen.map ? View.GONE : View.VISIBLE);
        action.setVisibility(View.VISIBLE);
        if (screen.map) {
            List<Situation> results = screen.key.isEmpty() ? model.results()
                    : s == null ? Collections.emptyList() : Collections.singletonList(s);
            title.setText(screen.key.isEmpty() ? "Results map" : "Situation map");
            int count = map.setSituations(results, model.filter().radius);
            caption.setText(count + " of " + results.size() + " located · Tap a marker for details\n"
                    + "Pinch to zoom · Drag to move · Natural Earth");
            action.setText("Fit");
            action.setOnClickListener(v -> map.fit());
            return;
        }
        title.setText(s == null ? "Traffic details" : "Situation details");
        action.setText("Map");
        action.setContentDescription(s == null ? "Map filtered results" : "Map this situation");
        action.setOnClickListener(v -> model.screen(s == null ? "" : s.key(), true));
        if (renderedDetails && shown == s && s != null) return;
        boolean changed = shown != s;
        renderedDetails = true; shown = s;
        body.removeAllViews();
        if (s == null) {
            heading(screen.key.isEmpty() ? "Select a situation" : "Situation not available");
            text(screen.key.isEmpty() ? "Choose a result to see its impact, timing and location."
                    : "This item is loading or is no longer in the latest feed. Return to results to select another.");
            return;
        }
        if (changed) scroll.scrollTo(0, 0);
        TextView category = add(s.kind().label, 14, s.kind().color, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) category.setAccessibilityHeading(true);
        heading(s.comment());
        text(s.feed.label + " · " + s.id);
        if (!s.routes.isEmpty()) field("Roads mentioned", String.join(", ", s.routes));
        field("Information status", friendly(s.status));
        double distance = s.distance(GLASGOW);
        if (Double.isFinite(distance)) field("From central Glasgow", String.format(Locale.UK, "%.1f km (straight line)", distance));
        for (int i = 0; i < s.records.size(); i++) {
            Record r = s.records.get(i);
            if (s.records.size() > 1) {
                heading("Record " + (i + 1));
                field("Record ID", r.id);
                if (!r.description.equals(s.comment())) text(r.description);
            }
            field("Type", friendly(r.subtype.isEmpty() ? r.type : r.subtype));
            heading("Impact");
            field("Delay level", friendly(r.impact.delays));
            field("Operational lanes", r.impact.operational);
            field("Restricted lanes", r.impact.restricted);
            field("Minimum / reported delay", r.impact.minimum.isEmpty() ? "" : r.impact.minimum + " seconds");
            field("Maximum delay", r.impact.maximum.isEmpty() ? "" : r.impact.maximum + " seconds");
            field("Capacity remaining", r.impact.capacity.isEmpty() ? "" : r.impact.capacity + "%");
            field("Compliance", friendly(r.compliance));
            field("Urgent roadworks", r.urgent.isEmpty() ? "" : "true".equalsIgnoreCase(r.urgent) ? "Yes" : "No");
            if (r.impact.delays.isEmpty() && r.impact.operational.isEmpty() && r.impact.restricted.isEmpty()
                    && r.impact.minimum.isEmpty() && r.impact.maximum.isEmpty() && r.impact.capacity.isEmpty())
                text("No quantified impact supplied.");
            heading("Validity");
            field("Starts", date(r.start)); field("Ends", date(r.end));
            field("Status", friendly(r.validity)); field("Record updated", date(r.updated));
            field("Source", r.source); field("Occurrence", friendly(r.certainty));
            heading("Location");
            if (r.locations.isEmpty()) text("No location was provided.");
            for (Location l : r.locations) {
                field("Direction", friendly(l.direction)); field("Location type", friendly(l.type));
                if (l.places.isEmpty()) text("No location coordinates or names supplied.");
                for (Place p : l.places) {
                    String location = p.name;
                    if (p.point != null) location += (location.isEmpty() ? "" : "\n")
                            + String.format(Locale.UK, "%.6f°, %.6f°", p.point.lat, p.point.lon);
                    field(friendly(p.role), location);
                }
            }
        }
    }

    private void heading(String value) {
        TextView t = add(value, 18, 0xff102d39, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        t.setPadding(0, dp(10), 0, dp(4));
    }
    private void text(String value) { if (!value.isEmpty()) add(value, 15, 0xff28444f, false); }
    private void field(String title, String value) { if (!value.isEmpty()) text(title + ": " + value); }
    private TextView add(String value, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(2), 0, dp(2));
        body.addView(view);
        return view;
    }
    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }
}
