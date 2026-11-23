package org.gcu.traffic;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

public final class MainActivity extends FragmentActivity {
    private static final String[] CATEGORIES = {"All types", "Incidents", "Roadworks"};
    private static final String[] RADIUS = {"Any distance", "Glasgow ≤ 5 km", "Glasgow ≤ 15 km", "Glasgow ≤ 30 km", "Glasgow ≤ 60 km"};
    private static final int[] KM = {0, 5, 15, 30, 60};
    private TrafficViewModel model;
    private boolean landscape;
    private String status = "", notice = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, window) -> {
            Insets i = window.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Android 15 enforces edge-to-edge for this target; earlier versions fit system bars.
            if (android.os.Build.VERSION.SDK_INT >= 35) v.setPadding(i.left, i.top, i.right,
                    Math.max(i.bottom, window.getInsets(WindowInsetsCompat.Type.ime()).bottom));
            return window;
        });
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        model = new ViewModelProvider(this).get(TrafficViewModel.class);
        if (state != null && model.snapshots().getValue() == null) {
            model.filter(state.getString("road", ""), state.getInt("category"), state.getInt("radius"));
            model.screen(state.getString("selected", ""), state.getBoolean("map"));
        }
        if (getSupportFragmentManager().findFragmentById(R.id.list_panel) == null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.list_panel, new ResultsFragment())
                    .add(R.id.detail_panel, new DetailsFragment())
                    .commitNow();
        }
        configureFilters();
        Button refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(v -> model.refresh());
        findViewById(R.id.credentials).setOnClickListener(v -> credentials());
        model.busy().observe(this, loading -> {
            refresh.setEnabled(!loading);
            refresh.setText(loading ? "Updating…" : "Refresh");
        });
        model.snapshots().observe(this, snapshot -> { status = snapshot.status; renderStatus(); });
        model.notice().observe(this, text -> { notice = text; renderStatus(); });
        model.screens().observe(this, screen -> {
            boolean panel = landscape || screen.map || !screen.key.isEmpty();
            findViewById(R.id.list_panel).setVisibility(!landscape && panel ? View.GONE : View.VISIBLE);
            findViewById(R.id.detail_panel).setVisibility(panel ? View.VISIBLE : View.GONE);
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (model.screen().map || !model.screen().key.isEmpty()) model.screen("", false);
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void renderStatus() {
        TextView view = findViewById(R.id.source_status);
        view.setText((status.isEmpty() ? "Loading Traffic Scotland data…" : status)
                + (notice.isEmpty() ? "" : "\n" + notice));
    }

    private void configureFilters() {
        EditText road = findViewById(R.id.road_input);
        Spinner category = findViewById(R.id.category), radius = findViewById(R.id.radius);
        road.setText(model.filter().road);
        spinner(category, CATEGORIES);
        spinner(radius, RADIUS);
        category.setSelection(model.filter().category);
        for (int i = 0; i < KM.length; i++) if (KM[i] == model.filter().radius) radius.setSelection(i);
        Runnable update = () -> {
            int c = Math.max(0, category.getSelectedItemPosition()), r = Math.max(0, radius.getSelectedItemPosition());
            String q = road.getText().toString();
            TrafficViewModel.Filter current = model.filter();
            if (c != current.category || KM[r] != current.radius || !q.equals(current.road))
                model.filter(q, c, KM[r]);
        };
        road.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        road.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard(); return true;
        });
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int position, long id) { update.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        category.setOnItemSelectedListener(listener);
        radius.setOnItemSelectedListener(listener);
        findViewById(R.id.map_results).setOnClickListener(v -> { hideKeyboard(); model.screen("", true); });
        findViewById(R.id.root).setFocusableInTouchMode(true);
        findViewById(R.id.root).requestFocus();
    }

    private void spinner(Spinner spinner, String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void credentials() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        fields.setPadding(margin, 0, margin, 0);
        EditText id = new EditText(this), key = new EditText(this);
        id.setHint("Client ID"); key.setHint("Client key");
        id.setSingleLine(true); key.setSingleLine(true);
        id.setSaveEnabled(false); key.setSaveEnabled(false);
        id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (android.os.Build.VERSION.SDK_INT >= 26) fields.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember on this device (encrypted)");
        fields.addView(id); fields.addView(key); fields.addView(remember);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Traffic Scotland API access")
                .setMessage("Enter your approved DATEX II credentials on this device only. Leave blank to continue viewing cached or sample data.")
                .setView(fields).setNegativeButton("Cancel", null)
                .setNeutralButton("Forget", (d, w) -> model.forget())
                .setPositiveButton("Connect", null).create();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String client = id.getText().toString().trim(), secret = key.getText().toString().trim();
            if (client.isEmpty() || client.contains(":")) { id.setError("Enter a valid client ID"); return; }
            if (secret.isEmpty()) { key.setError("Enter the key"); return; }
            model.access(client, secret, remember.isChecked());
            key.setText("");
            dialog.dismiss();
        });
    }

    @Override protected void onStart() { super.onStart(); model.foreground(); }
    @Override protected void onStop() { model.background(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("road", model.filter().road);
        out.putInt("category", model.filter().category); out.putInt("radius", model.filter().radius);
        out.putString("selected", model.screen().key); out.putBoolean("map", model.screen().map);
        super.onSaveInstanceState(out);
    }
}
