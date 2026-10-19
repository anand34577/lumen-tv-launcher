package com.lumen.tvlauncher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int FAV_COLUMNS = 4;
    private static final int RECENT_LIMIT = 8;
    private static final long MB = 1024L * 1024L;
    /** Pinned on first launch if installed. */
    private static final String[] DEFAULT_FAVOURITES = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.graymatrix.did",
            "org.videolan.vlc",
    };
    /** This TV's own picture menu; falls back to Display & sound elsewhere. */
    private static final ComponentName PICTURE_SETTINGS =
            new ComponentName("com.apps.tvsettings", "com.apps.tvsettings.ui.picture.adjustment.PictureAdjustActivity");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, AppEntry> apps = new LinkedHashMap<>();
    private final Runnable reloadTask = this::reload;

    private Prefs prefs;
    private ConnectivityManager connectivity;
    private ScrollView scroll;
    private HorizontalScrollView favScroll, recentScroll;
    private LinearLayout favRow, recentRow, alerts;
    private GridLayout grid;
    private TextView clock, date, net, recentHeader, appCount, hiddenChip, badge;
    private int gap, favW, favH, appW, appH, columns, favCount;
    private String movingPkg;
    private TvMenu menu;
    private boolean rendered, usageChanged;
    private NetworkCapabilities netCaps;
    private String alertSignature = "";

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            refreshAlerts(); // storage is cheap to re-check once a minute
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Installs often fire several broadcasts in a row; reload once.
            main.removeCallbacks(reloadTask);
            main.postDelayed(reloadTask, 400);
        }
    };

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            main.post(() -> showNetwork(caps));
        }

        @Override
        public void onLost(Network network) {
            main.post(() -> showNetwork(null));
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        connectivity = getSystemService(ConnectivityManager.class);

        scroll = findViewById(R.id.scroll);
        favScroll = findViewById(R.id.fav_scroll);
        favRow = findViewById(R.id.fav_row);
        recentHeader = findViewById(R.id.recent_header);
        recentScroll = findViewById(R.id.recent_scroll);
        recentRow = findViewById(R.id.recent_row);
        grid = findViewById(R.id.grid);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        net = findViewById(R.id.net);
        appCount = findViewById(R.id.app_count);
        hiddenChip = findViewById(R.id.hidden_chip);
        alerts = findViewById(R.id.alerts);
        badge = findViewById(R.id.badge);

        findViewById(R.id.btn_notifs).setOnClickListener(v -> showNotifications());
        findViewById(R.id.btn_search).setOnClickListener(v -> voiceSearch());
        findViewById(R.id.btn_inputs).setOnClickListener(v -> showInputs());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showQuickSettings());
        hiddenChip.setOnClickListener(this::showHidden);
        NotificationService.setListener(this::refreshNotifications);

        applyTheme();
        measure();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        register(packageReceiver, pf);

        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, tf);
        updateClock();

        try {
            connectivity.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException ignored) {
        }
        showNetwork(connectivity.getNetworkCapabilities(connectivity.getActiveNetwork()));
        refreshNotifications();

        // Coming back from an app: refresh Recent / Most-used ordering.
        if (usageChanged && rendered) {
            usageChanged = false;
            render(focusedKey());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregister(timeReceiver);
        try {
            connectivity.unregisterNetworkCallback(netCallback);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        unregister(packageReceiver);
        NotificationService.setListener(null);
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Home pressed while we're already in front: reset to the top. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu != null && menu.isShowing()) menu.dismiss();
        endMove();
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        // A launcher never finishes; Back just returns to the top.
        if (movingPkg != null) {
            endMove();
            return;
        }
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    // ---------------------------------------------------------------- data

    private void reload() {
        Context app = getApplicationContext();
        worker.execute(() -> {
            List<AppEntry> loaded = AppLoader.load(app);
            main.post(() -> {
                if (isDestroyed()) return;
                apps.clear();
                for (AppEntry e : loaded) apps.put(e.pkg, e);
                seedFavourites();
                render(focusedKey());
            });
        });
    }

    private void seedFavourites() {
        if (prefs.seeded()) return;
        List<String> favs = new ArrayList<>();
        for (String p : DEFAULT_FAVOURITES) if (apps.containsKey(p)) favs.add(p);
        prefs.setFavourites(favs);
        prefs.markSeeded();
    }

    private String label(AppEntry e) {
        String custom = prefs.name(e.pkg);
        return custom != null ? custom : e.label;
    }

    private List<AppEntry> sortedApps(int sort) {
        List<AppEntry> list = new ArrayList<>(apps.values());
        Collator collator = Collator.getInstance();
        Comparator<AppEntry> az = (a, b) -> collator.compare(label(a), label(b));
        if (sort == Prefs.SORT_MOST_USED) {
            list.sort(Comparator.<AppEntry>comparingInt(e -> -prefs.launchCount(e.pkg)).thenComparing(az));
        } else if (sort == Prefs.SORT_NEWEST) {
            list.sort(Comparator.<AppEntry>comparingLong(e -> -e.installed).thenComparing(az));
        } else {
            list.sort(az);
        }
        return list;
    }

    // ---------------------------------------------------------------- layout

    private void applyTheme() {
        getWindow().setBackgroundDrawable(Themes.background(this, prefs.theme()));
    }

    private void measure() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int pad = getResources().getDimensionPixelSize(R.dimen.pad_h);
        gap = getResources().getDimensionPixelSize(R.dimen.gap);
        columns = prefs.columns();
        int content = width - 2 * pad;
        favW = (content - gap * (FAV_COLUMNS - 1)) / FAV_COLUMNS;
        favH = favW * 9 / 16;
        appW = (content - gap * (columns - 1)) / columns;
        appH = appW * 9 / 16;
        grid.removeAllViews(); // column count can't shrink below existing children
        grid.setColumnCount(columns);
    }

    private void render(String focusKey) {
        View before = getCurrentFocus();
        movingPkg = null;
        List<String> favs = prefs.favourites();
        Set<String> hidden = prefs.hidden();

        // Favourites, then the "+ Add" tile
        favRow.removeAllViews();
        favCount = 0;
        for (String pkg : favs) {
            AppEntry e = apps.get(pkg);
            if (e == null) continue;
            favRow.addView(tile(e, TileView.FAVOURITES), rowParams());
            favCount++;
        }
        TileView add = new TileView(this, null, getString(R.string.add_app), favW, favH, TileView.FAVOURITES, true);
        add.setOnClickListener(this::showAddFavourite);
        favRow.addView(add, rowParams());

        // Recent: most recently opened apps that aren't already favourites
        recentRow.removeAllViews();
        if (prefs.showRecents()) {
            List<AppEntry> recent = new ArrayList<>();
            for (AppEntry e : apps.values()) {
                if (prefs.lastLaunch(e.pkg) > 0 && !favs.contains(e.pkg)) recent.add(e);
            }
            recent.sort(Comparator.comparingLong(e -> -prefs.lastLaunch(e.pkg)));
            for (int i = 0; i < Math.min(RECENT_LIMIT, recent.size()); i++) {
                recentRow.addView(tile(recent.get(i), TileView.RECENT), rowParams());
            }
        }
        int recentVisibility = recentRow.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        recentHeader.setVisibility(recentVisibility);
        recentScroll.setVisibility(recentVisibility);

        // All apps
        grid.removeAllViews();
        int shown = 0, hiddenCount = 0;
        for (AppEntry e : sortedApps(prefs.sort())) {
            if (hidden.contains(e.pkg)) {
                hiddenCount++;
                continue;
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = appW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(0, 0, shown % columns == columns - 1 ? 0 : gap, gap);
            grid.addView(tile(e, TileView.APPS), lp);
            shown++;
        }
        appCount.setText(String.valueOf(shown));
        hiddenChip.setVisibility(hiddenCount > 0 ? View.VISIBLE : View.GONE);
        hiddenChip.setText(getString(R.string.hidden_apps, hiddenCount));

        boolean first = !rendered;
        rendered = true;
        View target = focusKey != null ? findTile(focusKey) : null;
        if (first) {
            focusFirst();
        } else if (target != null) {
            target.requestFocus();
        } else if (before != null && !(before instanceof TileView) && before.isShown()) {
            before.requestFocus();
        } else {
            focusFirst();
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(gap);
        return lp;
    }

    private TileView tile(AppEntry e, char section) {
        boolean fav = section == TileView.FAVOURITES;
        TileView t = new TileView(this, e, label(e), fav ? favW : appW, fav ? favH : appH, section,
                fav || prefs.labelsAlways());
        t.setOnClickListener(v -> launch(e));
        t.setOnLongClickListener(v -> {
            showMenu(t);
            return true;
        });
        t.setOnKeyListener((v, code, ev) -> onTileKey(t, code, ev));
        return t;
    }

    private String focusedKey() {
        View f = getCurrentFocus();
        return f instanceof TileView ? ((TileView) f).key() : null;
    }

    private View findTile(String key) {
        for (ViewGroup parent : new ViewGroup[]{favRow, recentRow, grid}) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                if (v instanceof TileView && ((TileView) v).key().equals(key)) return v;
            }
        }
        return null;
    }

    private void focusFirst() {
        if (!rendered) return; // apps not loaded yet; the first render will place focus
        favScroll.scrollTo(0, 0);
        recentScroll.scrollTo(0, 0);
        favRow.getChildAt(0).requestFocus(); // first favourite, or the "+ Add" tile
    }

    // ---------------------------------------------------------------- keys & app menu

    private boolean onTileKey(TileView t, int code, KeyEvent ev) {
        if (t.app != null && t.section == TileView.FAVOURITES && t.app.pkg.equals(movingPkg)) {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveFavourite(t, -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveFavourite(t, 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    endMove();
                    return true;
                default:
                    return false;
            }
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (ev.getAction() == KeyEvent.ACTION_UP) showMenu(t);
            return true;
        }
        return false;
    }

    private void showMenu(TileView t) {
        AppEntry e = t.app;
        if (e == null) {
            showAddFavourite(t);
            return;
        }
        List<String> favs = prefs.favourites();
        TvMenu p = new TvMenu(this, label(e));
        p.add(R.string.open, () -> launch(e));
        if (favs.contains(e.pkg)) {
            p.add(R.string.fav_remove, () -> {
                favs.remove(e.pkg);
                prefs.setFavourites(favs);
                render(t.section == TileView.FAVOURITES ? null : t.key());
            });
        } else {
            p.add(R.string.fav_add, () -> {
                favs.add(e.pkg);
                prefs.setFavourites(favs);
                render(t.key());
            });
        }
        if (t.section == TileView.FAVOURITES && favCount > 1) p.add(R.string.reorder, () -> startMove(t));
        p.add(R.string.rename, () -> rename(e, t.key()));
        if (t.section == TileView.RECENT) {
            p.add(R.string.forget_recent, () -> {
                prefs.forgetLaunches(e.pkg);
                render(null);
            });
        }
        if (t.section == TileView.APPS) {
            p.add(R.string.hide, () -> {
                Set<String> hidden = prefs.hidden();
                hidden.add(e.pkg);
                prefs.setHidden(hidden);
                render(null);
            });
        }
        p.add(R.string.app_info, () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", e.pkg, null))));
        if (!e.system) {
            p.add(R.string.uninstall, () -> start(new Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", e.pkg, null))));
        }
        present(p, t);
    }

    private void showAddFavourite(View anchor) {
        List<String> favs = prefs.favourites();
        TvMenu p = new TvMenu(this, getString(R.string.add_title));
        for (AppEntry e : sortedApps(Prefs.SORT_AZ)) {
            if (favs.contains(e.pkg)) continue;
            p.add(label(e), () -> {
                List<String> f = prefs.favourites();
                f.add(e.pkg);
                prefs.setFavourites(f);
                render("f:" + e.pkg);
            });
        }
        present(p, anchor);
    }

    private void rename(AppEntry e, String focusKey) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(label(e));
        input.setSelectAllOnFocus(true);
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.rename_title)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    prefs.setName(e.pkg, name.equals(e.label) ? null : name);
                    render(focusKey);
                })
                .setNegativeButton(R.string.cancel, null);
        if (prefs.name(e.pkg) != null) {
            b.setNeutralButton(R.string.reset_name, (d, w) -> {
                prefs.setName(e.pkg, null);
                render(focusKey);
            });
        }
        AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    private void showHidden(View anchor) {
        TvMenu p = new TvMenu(this, getString(R.string.hidden_title)).hint(getString(R.string.hidden_hint));
        for (String pkg : prefs.hidden()) {
            AppEntry e = apps.get(pkg);
            if (e == null) continue;
            p.add(label(e), () -> {
                Set<String> h = prefs.hidden();
                h.remove(pkg);
                prefs.setHidden(h);
                render("a:" + pkg);
            });
        }
        present(p, anchor);
    }

    private void present(TvMenu m, View anchor) {
        if (menu != null && menu.isShowing()) menu.dismiss();
        menu = m;
        m.show(anchor);
    }

    private View gear() {
        return findViewById(R.id.btn_settings);
    }

    // ---------------------------------------------------------------- settings

    private void showQuickSettings() {
        TvMenu p = new TvMenu(this, getString(R.string.settings)).width(300);
        p.add(getString(R.string.customize), this::showCustomize);
        p.add(getString(R.string.qs_network), networkSummary(), () -> startFirst(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        p.add(getString(R.string.qs_picture), () -> startFirst(
                new Intent(Intent.ACTION_MAIN).setComponent(PICTURE_SETTINGS),
                new Intent(Settings.ACTION_SOUND_SETTINGS)));
        p.add(R.string.qs_display_sound, () -> startFirst(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        p.add(getString(R.string.qs_remotes), () -> startFirst(
                new Intent("com.google.android.intent.action.CONNECT_INPUT"),
                new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        p.add(getString(R.string.qs_storage), getString(R.string.qs_storage_detail,
                        Formatter.formatShortFileSize(this, fs.getAvailableBytes()),
                        Formatter.formatShortFileSize(this, fs.getTotalBytes())),
                () -> startFirst(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        p.add(getString(R.string.qs_apps),
                () -> startFirst(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        p.add(R.string.qs_datetime, () -> startFirst(new Intent(Settings.ACTION_DATE_SETTINGS)));
        p.add(R.string.qs_all, () -> startFirst(new Intent(Settings.ACTION_SETTINGS)));
        present(p, gear());
    }

    private CharSequence networkSummary() {
        NetworkCapabilities c = netCaps;
        if (c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return getString(R.string.offline);
        if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return getString(R.string.alert_no_internet, transportName(c));
        }
        return getString(R.string.qs_online, transportName(c));
    }

    private void showCustomize() {
        TvMenu p = new TvMenu(this, getString(R.string.customize)).width(300);
        p.addSetting(getString(R.string.c_background), () -> Themes.NAMES[Math.floorMod(prefs.theme(), Themes.NAMES.length)], () -> {
            prefs.setTheme((prefs.theme() + 1) % Themes.NAMES.length);
            applyTheme();
        });
        p.addSetting(getString(R.string.c_columns), () -> String.valueOf(prefs.columns()), () -> {
            prefs.setColumns(prefs.columns() >= 6 ? 4 : prefs.columns() + 1);
            measure();
            render(null);
        });
        p.addSetting(getString(R.string.c_labels), () -> getString(prefs.labelsAlways()
                ? R.string.c_labels_always : R.string.c_labels_focus), () -> {
            prefs.setLabelsAlways(!prefs.labelsAlways());
            render(null);
        });
        p.addSetting(getString(R.string.c_sort), () -> getString(prefs.sort() == Prefs.SORT_MOST_USED
                ? R.string.c_sort_used : prefs.sort() == Prefs.SORT_NEWEST ? R.string.c_sort_new : R.string.c_sort_az), () -> {
            prefs.setSort((prefs.sort() + 1) % 3);
            render(null);
        });
        p.addSetting(getString(R.string.c_recents), () -> getString(prefs.showRecents() ? R.string.on : R.string.off), () -> {
            prefs.setShowRecents(!prefs.showRecents());
            render(null);
        });
        p.addSetting(getString(R.string.c_clock), () -> getString(prefs.clock() == Prefs.CLOCK_12H
                ? R.string.c_clock_12 : prefs.clock() == Prefs.CLOCK_24H ? R.string.c_clock_24 : R.string.c_clock_system), () -> {
            prefs.setClock((prefs.clock() + 1) % 3);
            updateClock();
        });
        int hiddenCount = 0;
        for (String pkg : prefs.hidden()) if (apps.containsKey(pkg)) hiddenCount++;
        if (hiddenCount > 0) {
            p.add(getString(R.string.hidden_title), getString(R.string.c_hidden_detail, hiddenCount), () -> showHidden(gear()));
        }
        p.add(getString(R.string.c_reset), getString(R.string.c_reset_detail), this::confirmReset);
        present(p, gear());
    }

    private void confirmReset() {
        TvMenu p = new TvMenu(this, getString(R.string.c_reset)).hint(getString(R.string.c_reset_detail)).width(300);
        p.add(R.string.cancel, () -> {
        });
        p.add(R.string.reset_confirm, () -> {
            prefs.reset();
            applyTheme();
            measure();
            seedFavourites();
            render(null);
            updateClock();
        });
        present(p, gear());
    }

    // ---------------------------------------------------------------- inputs & search

    private void showInputs() {
        TvInputManager tim = getSystemService(TvInputManager.class);
        TvMenu p = new TvMenu(this, getString(R.string.inputs)).width(220);
        List<Object[]> inputs = new ArrayList<>(); // {label, connected, uri}
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence label = info.loadCustomLabel(this);
                if (TextUtils.isEmpty(label)) label = info.loadLabel(this);
                boolean connected = tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
                inputs.add(new Object[]{label.toString(), connected,
                        TvContract.buildChannelUriForPassthroughInput(info.getId())});
            }
        }
        // Connected sources first, then natural order (HDMI1 before HDMI2).
        inputs.sort((a, b) -> !a[1].equals(b[1]) ? ((Boolean) a[1] ? -1 : 1) : ((String) a[0]).compareToIgnoreCase((String) b[0]));
        for (Object[] in : inputs) {
            String label = (String) in[0];
            Uri uri = (Uri) in[2];
            p.add((Boolean) in[1] ? label + "  ·  " + getString(R.string.connected) : label,
                    () -> start(new Intent(Intent.ACTION_VIEW, uri)));
        }
        if (inputs.isEmpty()) {
            toast(R.string.no_inputs);
            return;
        }
        present(p, findViewById(R.id.btn_inputs));
    }

    private void voiceSearch() {
        startFirst(new Intent(Intent.ACTION_ASSIST),
                new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
                new Intent(Intent.ACTION_WEB_SEARCH));
    }

    // ---------------------------------------------------------------- reorder

    private void startMove(TileView t) {
        endMove();
        movingPkg = t.app.pkg;
        t.setMoving(true);
        t.requestFocus();
    }

    private void endMove() {
        if (movingPkg == null) return;
        View v = findTile("f:" + movingPkg);
        if (v instanceof TileView) ((TileView) v).setMoving(false);
        movingPkg = null;
    }

    private void moveFavourite(TileView t, int delta) {
        int from = favRow.indexOfChild(t);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= favCount) return; // never past the "+ Add" tile
        // Move the neighbour rather than the focused tile so focus never leaves it.
        View neighbour = favRow.getChildAt(to);
        favRow.removeView(neighbour);
        favRow.addView(neighbour, from);

        List<String> order = new ArrayList<>();
        for (int i = 0; i < favCount; i++) order.add(((TileView) favRow.getChildAt(i)).app.pkg);
        prefs.setFavourites(order);
        t.post(() -> t.requestRectangleOnScreen(new Rect(-gap, 0, t.getWidth() + gap, t.getHeight())));
    }

    // ---------------------------------------------------------------- actions

    private void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(e.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (start(i)) {
            prefs.recordLaunch(e.pkg);
            usageChanged = true;
        }
    }

    private boolean start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException ex) {
            toast(R.string.cannot_open);
            return false;
        }
    }

    /** Opens the first intent this TV can handle; firmware differs in which settings screens exist. */
    private void startFirst(Intent... candidates) {
        for (Intent i : candidates) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        toast(R.string.cannot_open);
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        Date now = new Date();
        boolean h24 = prefs.clock() == Prefs.CLOCK_SYSTEM ? DateFormat.is24HourFormat(this)
                : prefs.clock() == Prefs.CLOCK_24H;
        String time = DateFormat.format(h24 ? "HH:mm" : "h:mm", now).toString();
        if (h24) {
            clock.setText(time);
        } else {
            SpannableString s = new SpannableString(time + " " + DateFormat.format("a", now));
            s.setSpan(new RelativeSizeSpan(0.36f), time.length(), s.length(), 0);
            s.setSpan(new ForegroundColorSpan(getColor(R.color.text_secondary)), time.length(), s.length(), 0);
            clock.setText(s);
        }
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
    }

    private void showNetwork(NetworkCapabilities caps) {
        netCaps = caps;
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (!online) net.setText(R.string.offline);
        else net.setText(transportName(caps));
        net.setAlpha(online ? 1f : 0.5f);
        refreshAlerts();
    }

    private String transportName(NetworkCapabilities caps) {
        return getString(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? R.string.ethernet : R.string.wifi);
    }

    /** Rebuilds the alert pills under the clock, only when something actually changed. */
    private void refreshAlerts() {
        List<Object[]> list = new ArrayList<>(); // {critical, text, settings action}
        NetworkCapabilities c = netCaps;
        if (c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            list.add(new Object[]{true, getString(R.string.alert_offline), Settings.ACTION_WIFI_SETTINGS});
        } else if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            list.add(new Object[]{false, getString(R.string.alert_portal), Settings.ACTION_WIFI_SETTINGS});
        } else if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            list.add(new Object[]{true, getString(R.string.alert_no_internet, transportName(c)), Settings.ACTION_WIFI_SETTINGS});
        }

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long free = fs.getAvailableBytes(), total = fs.getTotalBytes();
        String freeText = Formatter.formatShortFileSize(this, free);
        if (free < 500 * MB || free * 100 < total * 5) {
            list.add(new Object[]{true, getString(R.string.alert_storage_full, freeText), Settings.ACTION_INTERNAL_STORAGE_SETTINGS});
        } else if (free < 1024 * MB || free * 100 < total * 10) {
            list.add(new Object[]{false, getString(R.string.alert_storage_low, freeText), Settings.ACTION_INTERNAL_STORAGE_SETTINGS});
        }

        StringBuilder sig = new StringBuilder();
        for (Object[] a : list) sig.append(a[0]).append(a[1]).append('|');
        if (sig.toString().equals(alertSignature)) return;
        alertSignature = sig.toString();

        boolean hadFocus = alerts.hasFocus();
        alerts.removeAllViews();
        for (Object[] a : list) alerts.addView(alertPill((Boolean) a[0], (String) a[1], (String) a[2]));
        if (!list.isEmpty()) alerts.getChildAt(alerts.getChildCount() - 1).setNextFocusRightId(R.id.btn_notifs);
        alerts.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (hadFocus) focusFirst();
    }

    private View alertPill(boolean critical, String text, String action) {
        int tone = getColor(critical ? R.color.alert_red : R.color.alert_amber);
        int h = Ui.dp(this, 32);

        TextView pill = new TextView(this);
        pill.setText(text);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setSingleLine(true);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 14), 0);
        pill.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{getColor(R.color.ink), tone}));

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, Ui.roundRect(getColor(R.color.text_primary), h));
        bg.addState(new int[]{}, Ui.roundRect((tone & 0x00FFFFFF) | 0x24000000, h));
        pill.setBackground(bg);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(tone);
        dot.setSize(Ui.dp(this, 8), Ui.dp(this, 8));
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        pill.setCompoundDrawablePadding(Ui.dp(this, 8));

        pill.setOnClickListener(v -> startFirst(new Intent(action), new Intent(Settings.ACTION_SETTINGS)));
        // The pills sit under the clock; give the D-pad a clear path to the top-bar buttons.
        pill.setNextFocusUpId(R.id.btn_notifs);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h);
        lp.setMarginEnd(Ui.dp(this, 10));
        pill.setLayoutParams(lp);
        return pill;
    }

    // ---------------------------------------------------------------- notifications

    private void refreshNotifications() {
        if (isDestroyed()) return;
        int n = NotificationService.active().size();
        badge.setText(n > 9 ? "9+" : String.valueOf(n));
        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    }

    private void showNotifications() {
        if (!NotificationService.isConnected()) {
            startFirst(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            return;
        }
        List<StatusBarNotification> list = NotificationService.active();
        TvMenu p = new TvMenu(this, getString(R.string.notifications)).width(360)
                .hint(list.isEmpty() ? getString(R.string.no_notifications) : null);
        boolean clearable = false;
        for (StatusBarNotification sbn : list) {
            Bundle x = sbn.getNotification().extras;
            CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
            AppEntry owner = apps.get(sbn.getPackageName());
            CharSequence detail = owner == null ? text
                    : TextUtils.isEmpty(text) ? label(owner) : label(owner) + "  ·  " + text;
            p.add(title, detail, () -> openNotification(sbn));
            clearable |= sbn.isClearable();
        }
        if (clearable) p.add(R.string.clear_all, NotificationService::clearAll);
        present(p, findViewById(R.id.btn_notifs));
    }

    private void openNotification(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        PendingIntent pi = n.contentIntent;
        if (pi != null) {
            try {
                ActivityOptions o = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                }
                pi.send(this, 0, null, null, null, null, o.toBundle());
            } catch (PendingIntent.CanceledException ignored) {
            }
        } else {
            AppEntry owner = apps.get(sbn.getPackageName());
            if (owner != null) launch(owner);
        }
        if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) NotificationService.cancel(sbn.getKey());
    }

    // ---------------------------------------------------------------- helpers

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    private void unregister(BroadcastReceiver r) {
        try {
            unregisterReceiver(r);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
