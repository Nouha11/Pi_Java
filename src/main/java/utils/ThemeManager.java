package utils;

import javafx.application.Platform;
import javafx.scene.Scene;

import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ThemeManager {

    public enum Mode { LIGHT, DARK, SCHEDULED }

    public static final Map<String, LocalTime[]> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("Evening  (18:00 -> 07:00)", new LocalTime[]{LocalTime.of(18, 0), LocalTime.of(7,  0)});
        PRESETS.put("Night    (21:00 -> 06:00)", new LocalTime[]{LocalTime.of(21, 0), LocalTime.of(6,  0)});
        PRESETS.put("Sunset   (20:00 -> 08:00)", new LocalTime[]{LocalTime.of(20, 0), LocalTime.of(8,  0)});
        PRESETS.put("Office   (17:00 -> 09:00)", new LocalTime[]{LocalTime.of(17, 0), LocalTime.of(9,  0)});
    }

    private static final String PROPS_FILE = "src/main/resources/config.properties";
    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("HH:mm");
    private static ThemeManager instance;

    private Mode      mode       = Mode.LIGHT;
    private LocalTime darkStart  = LocalTime.of(20, 0);
    private LocalTime lightStart = LocalTime.of(7,  0);

    private final List<Scene> scenes = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ThemeScheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> task;
    private String darkCssUrl;

    private controllers.NovaDashboardController dashboardController;

    private ThemeManager() {
        var res = getClass().getResource("/css/dark.css");
        darkCssUrl = (res != null) ? res.toExternalForm() : null;
        if (darkCssUrl == null)
            System.err.println("[ThemeManager] WARNING: dark.css not found!");
        loadPrefs();
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    public void register(Scene scene) {
        if (scene != null && !scenes.contains(scene)) {
            scenes.add(scene);
            applyToScene(scene);
        }
    }

    public void unregister(Scene scene) { scenes.remove(scene); }

    public void setDashboardController(controllers.NovaDashboardController ctrl) {
        this.dashboardController = ctrl;
    }

    public void setLight() {
        mode = Mode.LIGHT;
        cancelTask();
        applyAll();
        savePrefs();
    }

    public void setDark() {
        mode = Mode.DARK;
        cancelTask();
        applyAll();
        savePrefs();
    }

    public void setScheduled(LocalTime darkAt, LocalTime lightAt) {
        mode       = Mode.SCHEDULED;
        darkStart  = darkAt;
        lightStart = lightAt;
        cancelTask();
        applyAll();
        startScheduler();
        savePrefs();
    }

    public Mode      getMode()       { return mode;       }
    public LocalTime getDarkStart()  { return darkStart;  }
    public LocalTime getLightStart() { return lightStart; }

    public boolean isDark() {
        return mode == Mode.DARK ||
               (mode == Mode.SCHEDULED && inDarkWindow());
    }

    private void applyAll() {
        if (Platform.isFxApplicationThread()) doApplyAll();
        else Platform.runLater(this::doApplyAll);
    }

    private void doApplyAll() {
        boolean dark = isDark();
        scenes.forEach(s -> applyToScene(s));
        if (dashboardController != null) {
            dashboardController.applyDarkModeToNodes(dark);
            dashboardController.applyDarkModeToContentArea(dark);
        }
    }

    public void applyToScene(Scene scene) {
        if (scene == null || darkCssUrl == null) return;
        scene.getStylesheets().removeIf(s -> s.contains("dark.css"));
        if (isDark()) scene.getStylesheets().add(darkCssUrl);
    }

    /** Apply dark mode to a freshly loaded view node */
    public void applyToParent(javafx.scene.Parent parent) {
        if (parent == null) return;
        DarkModeApplier.applyToNode(parent, isDark());
    }

    private void startScheduler() {
        task = scheduler.scheduleAtFixedRate(this::applyAll, 30, 30, TimeUnit.SECONDS);
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) task.cancel(false);
    }

    private boolean inDarkWindow() {
        LocalTime now = LocalTime.now();
        if (darkStart.isBefore(lightStart)) {
            return !now.isBefore(darkStart) && now.isBefore(lightStart);
        } else {
            return !now.isBefore(darkStart) || now.isBefore(lightStart);
        }
    }

    private void savePrefs() {
        try {
            File f = new File(PROPS_FILE);
            Properties p = new Properties();
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) { p.load(in); }
            }
            p.setProperty("theme.mode",        mode.name());
            p.setProperty("theme.dark.start",  darkStart.format(FMT));
            p.setProperty("theme.light.start", lightStart.format(FMT));
            try (OutputStream out = new FileOutputStream(f)) {
                p.store(out, "NOVA Theme Settings");
            }
        } catch (Exception e) {
            System.err.println("[ThemeManager] Save failed: " + e.getMessage());
        }
    }

    private void loadPrefs() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is == null) return;
            Properties p = new Properties();
            p.load(is);
            mode       = Mode.valueOf(p.getProperty("theme.mode", "LIGHT"));
            darkStart  = LocalTime.parse(p.getProperty("theme.dark.start",  "20:00"), FMT);
            lightStart = LocalTime.parse(p.getProperty("theme.light.start", "07:00"), FMT);
            if (mode == Mode.SCHEDULED) startScheduler();
        } catch (Exception e) {
            mode = Mode.LIGHT;
        }
    }

    public void shutdown() { scheduler.shutdownNow(); }
}