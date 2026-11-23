package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TrafficViewModel extends AndroidViewModel {
    private static final long REFRESH_MINUTES = 60; // Temporarily reduce only for the demo.
    private final TrafficRepository repository;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean working = new AtomicBoolean();
    private final MutableLiveData<TrafficRepository.Snapshot> snapshots = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> notice = new MutableLiveData<>("");
    private final MutableLiveData<Filter> filters = new MutableLiveData<>(new Filter("", 0, 0));
    private final MutableLiveData<Screen> screens = new MutableLiveData<>(new Screen("", false));
    private volatile TrafficRepository.Credentials credentials;
    private ScheduledFuture<?> schedule;
    private volatile long lastRequest = 0;
    private boolean started = false;

    public TrafficViewModel(@NonNull Application application) {
        super(application);
        repository = new TrafficRepository(application);
    }

    LiveData<TrafficRepository.Snapshot> snapshots() { return snapshots; }
    LiveData<Boolean> busy() { return busy; }
    LiveData<String> notice() { return notice; }
    LiveData<Filter> filters() { return filters; }
    LiveData<Screen> screens() { return screens; }
    Filter filter() { return filters.getValue(); }
    Screen screen() { return screens.getValue(); }
    void filter(String road, int category, int radius) { filters.setValue(new Filter(road, category, radius)); }
    void screen(String key, boolean map) { screens.setValue(new Screen(key, map)); }

    void foreground() {
        if (!started) {
            started = true;
            worker.execute(() -> {
                try { credentials = repository.savedCredentials(); }
                catch (Exception e) { notice.postValue("Saved API access could not be opened. Re-enter credentials."); }
                snapshots.postValue(repository.load(credentials, false));
                refreshInWorker();
            });
        } else if (lastRequest == 0 || System.currentTimeMillis() - lastRequest >= TimeUnit.MINUTES.toMillis(REFRESH_MINUTES)) {
            refresh();
        }
        if (schedule == null || schedule.isCancelled()) {
            long interval = TimeUnit.MINUTES.toMillis(REFRESH_MINUTES);
            long elapsed = lastRequest == 0 ? 0 : Math.max(0, System.currentTimeMillis() - lastRequest);
            schedule = worker.scheduleWithFixedDelay(this::refreshInWorker,
                    Math.max(1000, interval - elapsed), interval, TimeUnit.MILLISECONDS);
        }
    }

    void background() {
        if (schedule != null) schedule.cancel(false);
    }

    void refresh() { if (!working.get()) worker.execute(this::refreshInWorker); }

    private void refreshInWorker() {
        if (!working.compareAndSet(false, true)) return;
        busy.postValue(true);
        lastRequest = System.currentTimeMillis();
        try { snapshots.postValue(repository.load(credentials, true)); }
        finally { working.set(false); busy.postValue(false); }
    }

    void access(String id, String key, boolean remember) {
        worker.execute(() -> {
            credentials = new TrafficRepository.Credentials(id, key);
            try {
                if (remember) repository.saveCredentials(credentials); else repository.forgetCredentials();
                notice.postValue("");
            } catch (Exception e) {
                notice.postValue("Could not remember API access. It is available for this session only.");
            }
            refreshInWorker();
        });
    }

    void forget() {
        worker.execute(() -> {
            credentials = null;
            repository.forgetCredentials();
            notice.postValue("Credentials removed. Previously cached traffic data is still available.");
            refreshInWorker();
        });
    }

    Situation selected() {
        TrafficRepository.Snapshot snapshot = snapshots.getValue();
        if (snapshot != null) for (Situation s : snapshot.all) if (s.key().equals(screen().key)) return s;
        return null;
    }

    List<Situation> results() {
        TrafficRepository.Snapshot snapshot = snapshots.getValue();
        return snapshot == null ? Collections.emptyList() : results(snapshot.all, filter());
    }

    static List<Situation> results(List<Situation> all, Filter filter) {
        List<Situation> results = new ArrayList<>();
        for (Situation s : all) {
            if (filter.category == 1 && s.feed != Feed.INCIDENT || filter.category == 2 && s.feed != Feed.WORKS) continue;
            if (!s.matchesRoad(filter.road)) continue;
            if (filter.radius != 0 && s.distance(GLASGOW) > filter.radius) continue;
            results.add(s);
        }
        if (filter.radius != 0) results.sort(Comparator.comparingDouble(s -> s.distance(GLASGOW)));
        return results;
    }

    @Override protected void onCleared() { worker.shutdownNow(); }

    static final class Filter {
        final String road;
        final int category, radius;
        Filter(String road, int category, int radius) { this.road = road; this.category = category; this.radius = radius; }
    }
    static final class Screen {
        final String key;
        final boolean map;
        Screen(String key, boolean map) { this.key = key; this.map = map; }
    }
}
