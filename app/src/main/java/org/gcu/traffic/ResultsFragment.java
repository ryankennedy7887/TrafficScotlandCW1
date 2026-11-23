package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.Collections;
import java.util.List;

public final class ResultsFragment extends Fragment {
    private TrafficViewModel model;
    private Rows adapter;
    private TextView count;

    public ResultsFragment() { super(R.layout.fragment_results); }

    @Override public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);
        count = view.findViewById(R.id.result_count);
        ListView list = view.findViewById(R.id.result_list);
        adapter = new Rows(requireContext());
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, position, id) -> {
            ((MainActivity) requireActivity()).hideKeyboard();
            model.screen(adapter.getItem(position).key(), false);
        });
        model.snapshots().observe(getViewLifecycleOwner(), snapshot -> render());
        model.filters().observe(getViewLifecycleOwner(), filters -> render());
    }

    private void render() {
        List<Situation> values = model.results();
        adapter.values = values;
        adapter.notifyDataSetChanged();
        if (model.snapshots().getValue() == null) count.setText("Reading traffic feeds…");
        else if (values.isEmpty()) count.setText("No matching situations. Try a different filter.");
        else count.setText(values.size() + (values.size() == 1 ? " situation" : " situations")
                + (model.filter().radius > 0 ? " · nearest first" : "") + " · select for details");
    }

    private static final class Rows extends BaseAdapter {
        final LayoutInflater inflater;
        List<Situation> values = Collections.emptyList();
        Rows(Context context) { inflater = LayoutInflater.from(context); }
        @Override public int getCount() { return values.size(); }
        @Override public Situation getItem(int position) { return values.get(position); }
        @Override public long getItemId(int position) { return getItem(position).key().hashCode(); }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = inflater.inflate(R.layout.traffic_row, parent, false);
            Situation s = getItem(position);
            Kind kind = s.kind();
            ((ImageView) view.findViewById(R.id.kind_icon)).setImageDrawable(new SituationIcon(kind));
            TextView label = view.findViewById(R.id.kind_label);
            label.setText(kind.label); label.setTextColor(kind.color);
            ((TextView) view.findViewById(R.id.comment)).setText(s.comment());
            return view;
        }
    }
}
