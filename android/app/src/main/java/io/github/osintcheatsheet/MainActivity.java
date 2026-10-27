package io.github.osintcheatsheet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Flipper-style home screen. "Remote" mode shows one tool at a time on an LCD
 * panel driven by a D-pad; "Search" mode is the searchable, collapsible list.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "osint";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_GROUP = "remote_group";
    private static final String KEY_ITEM = "remote_item";
    private static final String FAVORITES = "Favorites";
    private static final int HISTORY_LIMIT = 50;

    /** A navigable item on the LCD: either a tool or a section's notes page. */
    private static final class Entry {
        final String section;
        final CheatSheet.Tool tool;
        final String notes;

        Entry(String section, CheatSheet.Tool tool, String notes) {
            this.section = section;
            this.tool = tool;
            this.notes = notes;
        }
    }

    private static final class Group {
        final String title;
        final List<Entry> entries = new ArrayList<>();

        Group(String title) {
            this.title = title;
        }
    }

    private static final int ROW_HEADER = 0;
    private static final int ROW_ITEM = 1;

    /** One list row in search mode: a section header, a tool, or a notes page. */
    private static final class Row {
        final int type;
        final String title;
        final String subtitle;
        final Entry entry;
        final Runnable action;

        Row(int type, String title, String subtitle, Entry entry, Runnable action) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.entry = entry;
            this.action = action;
        }
    }

    private List<CheatSheet.Section> sections;
    private Map<String, CheatSheet.Tool> toolsByUrl;
    private SharedPreferences prefs;

    // Remote mode
    private final List<Group> groups = new ArrayList<>();
    private final Deque<int[]> history = new ArrayDeque<>();
    private int group;
    private int item;
    private View remoteView;
    private TextView lcdSection;
    private TextView lcdCounter;
    private TextView lcdName;
    private TextView lcdHost;
    private TextView lcdType;
    private TextView lcdDescription;

    // Search mode
    private final Set<String> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private View listView;
    private RowAdapter adapter;
    private String query = "";
    private ImageLabel modeButton;

    /** The first top button flips between Search and Remote, so it needs its parts. */
    private static final class ImageLabel {
        final LinearLayout box;

        ImageLabel(LinearLayout box) {
            this.box = box;
        }

        void set(int icon, String label) {
            ((android.widget.ImageView) box.getChildAt(0)).setImageResource(icon);
            ((TextView) box.getChildAt(1)).setText(label);
            box.setContentDescription(label);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sections = CheatSheet.load(this);
        toolsByUrl = CheatSheet.toolsByUrl(sections);
        expanded.add(FAVORITES);
        buildGroups();
        group = prefs.getInt(KEY_GROUP, 0);
        item = prefs.getInt(KEY_ITEM, 0);
        clampPosition();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        modeButton = new ImageLabel(addTopButton(top, R.drawable.ic_search, "Search", v -> toggleMode()));
        addTopButton(top, R.drawable.ic_globe, "Dorks", v -> startActivity(new Intent(this, DorkActivity.class)));
        addTopButton(top, R.drawable.ic_camera, "EXIF", v -> startActivity(new Intent(this, ExifActivity.class)));
        addTopButton(top, R.drawable.ic_terminal, "Termux", v -> Termux.showSetupHelp(this, null));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        remoteView = buildRemote();
        listView = buildList();
        listView.setVisibility(View.GONE);
        content.addView(remoteView);
        content.addView(listView);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        showEntry();
    }

    private LinearLayout addTopButton(LinearLayout top, int icon, String label, View.OnClickListener click) {
        LinearLayout button = Flipper.iconButton(this, icon, label, click);
        top.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
        return button;
    }

    // ---------------------------------------------------------------- remote mode

    private View buildRemote() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 20);
        column.setPadding(side, 0, side, Ui.dp(this, 16));
        column.addView(new Space(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        lcdSection = Flipper.lcdText(this, 20, false);
        lcdSection.setSingleLine(true);
        lcdSection.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(lcdSection, new LinearLayout.LayoutParams(0, -2, 1f));
        lcdCounter = Flipper.lcdText(this, 20, false);
        header.addView(lcdCounter);
        screen.addView(header);

        lcdName = Flipper.lcdText(this, 32, true);
        lcdName.setLines(2);
        lcdName.setEllipsize(TextUtils.TruncateAt.END);
        lcdName.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = Ui.dp(this, 6);
        screen.addView(lcdName, nameLp);

        lcdHost = Flipper.lcdText(this, 24, false);
        lcdHost.setSingleLine(true);
        lcdHost.setEllipsize(TextUtils.TruncateAt.END);
        screen.addView(lcdHost);
        lcdType = Flipper.lcdText(this, 24, false);
        lcdType.setSingleLine(true);
        screen.addView(lcdType);
        lcdDescription = Flipper.lcdText(this, 21, false);
        lcdDescription.setLines(2);
        lcdDescription.setEllipsize(TextUtils.TruncateAt.END);
        screen.addView(lcdDescription);

        LinearLayout keys = new LinearLayout(this);
        keys.addView(Flipper.softKey(this, "◀ Prev.", v -> move(-1)));
        keys.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        keys.addView(Flipper.softKey(this, "Next ▶", v -> move(1)));
        LinearLayout.LayoutParams keysLp = new LinearLayout.LayoutParams(-1, -2);
        keysLp.topMargin = Ui.dp(this, 8);
        screen.addView(keys, keysLp);

        View lcd = Flipper.lcd(this, screen);
        lcd.setOnClickListener(v -> openCurrent());
        lcd.setOnLongClickListener(v -> {
            showCurrentMenu();
            return true;
        });
        column.addView(lcd, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(-1, -2);
        markLp.topMargin = Ui.dp(this, 10);
        markLp.bottomMargin = Ui.dp(this, 14);
        column.addView(Flipper.wordmark(this, "VTLOSINT"), markLp);

        LinearLayout controls = new LinearLayout(this);
        controls.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Flipper.DPad pad = new Flipper.DPad(this);
        pad.setListener(new Flipper.DPad.Listener() {
            @Override
            public void onPress(int key) {
                switch (key) {
                    case Flipper.DPad.LEFT: move(-1); break;
                    case Flipper.DPad.RIGHT: move(1); break;
                    case Flipper.DPad.UP: jumpGroup(-1); break;
                    case Flipper.DPad.DOWN: jumpGroup(1); break;
                    default: openCurrent(); break;
                }
            }

            @Override
            public void onLongPressOk() {
                showCurrentMenu();
            }
        });
        int padSize = Ui.dp(this, 190);
        controls.addView(pad, new LinearLayout.LayoutParams(padSize, padSize));
        FrameLayout backSlot = new FrameLayout(this);
        Flipper.BackButton back = new Flipper.BackButton(this);
        back.setOnClickListener(v -> goBack());
        int backSize = Ui.dp(this, 66);
        backSlot.addView(back, new FrameLayout.LayoutParams(backSize, backSize,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        controls.addView(backSlot, new LinearLayout.LayoutParams(0, padSize, 1f));
        column.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column);
        return scroll;
    }

    private void buildGroups() {
        groups.clear();
        Group favorites = new Group(FAVORITES);
        for (String url : prefs.getStringSet(KEY_FAVORITES, new HashSet<>())) {
            CheatSheet.Tool t = toolsByUrl.get(url);
            if (t != null) favorites.entries.add(new Entry(FAVORITES, t, null));
        }
        if (!favorites.entries.isEmpty()) groups.add(favorites);
        for (CheatSheet.Section s : sections) {
            Group g = new Group(s.title);
            if (s.hasNotes()) g.entries.add(new Entry(s.title, null, s.notes.toString()));
            for (CheatSheet.Tool t : s.tools) g.entries.add(new Entry(s.title, t, null));
            groups.add(g);
        }
    }

    private void clampPosition() {
        if (group < 0 || group >= groups.size()) group = 0;
        int size = groups.get(group).entries.size();
        if (item < 0 || item >= size) item = 0;
    }

    private Entry current() {
        return groups.get(group).entries.get(item);
    }

    private void remember() {
        history.push(new int[]{group, item});
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    /** Steps through entries, continuing into the neighbouring section at either end. */
    private void move(int step) {
        remember();
        item += step;
        if (item < 0) {
            group = (group - 1 + groups.size()) % groups.size();
            item = groups.get(group).entries.size() - 1;
        } else if (item >= groups.get(group).entries.size()) {
            group = (group + 1) % groups.size();
            item = 0;
        }
        showEntry();
    }

    private void jumpGroup(int step) {
        remember();
        group = (group + step + groups.size()) % groups.size();
        item = 0;
        showEntry();
    }

    private void goBack() {
        if (history.isEmpty()) {
            Ui.toast(this, "Nothing to go back to — hold OK for tool options");
            return;
        }
        int[] previous = history.pop();
        group = previous[0];
        item = previous[1];
        clampPosition();
        showEntry();
    }

    private void showEntry() {
        Group g = groups.get(group);
        Entry e = current();
        lcdSection.setText(g.title);
        lcdCounter.setText(String.format(Locale.ROOT, "  %d/%d", item + 1, g.entries.size()));
        if (e.tool != null) {
            boolean cli = e.tool.termuxScript() != null;
            lcdName.setText(e.tool.name);
            lcdHost.setText("Host: " + e.tool.host());
            lcdType.setText(cli ? "Type: CLI · Termux" : "Type: Web tool");
            lcdDescription.setText(e.tool.description);
        } else {
            lcdName.setText("Notes & commands");
            lcdHost.setText("Host: cheat sheet");
            lcdType.setText("Type: Notes");
            lcdDescription.setText(e.notes.trim());
        }
        prefs.edit().putInt(KEY_GROUP, group).putInt(KEY_ITEM, item).apply();
    }

    private void openCurrent() {
        open(current());
    }

    private void showCurrentMenu() {
        Entry e = current();
        if (e.tool != null) showToolMenu(e.tool);
        else open(e);
    }

    private void open(Entry e) {
        if (e.tool == null) {
            TextActivity.show(this, e.section, e.notes);
        } else if (e.tool.termuxScript() != null) {
            showToolMenu(e.tool);
        } else {
            Ui.openInApp(this, e.tool.url);
        }
    }

    // ---------------------------------------------------------------- search mode

    private View buildList() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        EditText search = new EditText(this);
        search.setHint("Search " + toolsByUrl.size() + " tools…");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuildRows();
            }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
        int m = Ui.dp(this, 12);
        searchLp.setMargins(m, 0, m, Ui.dp(this, 8));
        column.addView(search, searchLp);

        ListView list = new ListView(this);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setFastScrollEnabled(true);
        list.setDivider(new ColorDrawable(Flipper.DARK));
        list.setDividerHeight(Ui.dp(this, 1));
        list.setOnItemClickListener((parent, view, position, id) -> onRowClick(rows.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (row.entry == null || row.entry.tool == null) return false;
            showToolMenu(row.entry.tool);
            return true;
        });
        column.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        rebuildRows();
        return column;
    }

    private void toggleMode() {
        boolean toList = listView.getVisibility() != View.VISIBLE;
        listView.setVisibility(toList ? View.VISIBLE : View.GONE);
        remoteView.setVisibility(toList ? View.GONE : View.VISIBLE);
        if (toList) modeButton.set(R.drawable.ic_dpad, "Remote");
        else modeButton.set(R.drawable.ic_search, "Search");
    }

    @Override
    public void onBackPressed() {
        if (listView.getVisibility() == View.VISIBLE) toggleMode();
        else super.onBackPressed();
    }

    private void rebuildRows() {
        rows.clear();
        boolean searching = !query.isEmpty();
        for (Group g : groups) {
            List<Row> items = new ArrayList<>();
            for (Entry e : g.entries) {
                if (e.tool != null) {
                    if (!searching || e.tool.matches(query)) items.add(toolRow(e));
                } else if (!searching || e.notes.toLowerCase(Locale.ROOT).contains(query)
                        || g.title.toLowerCase(Locale.ROOT).contains(query)) {
                    items.add(new Row(ROW_ITEM, "≡ Notes, queries & commands", firstLine(e.notes), e, null));
                }
            }
            addSection(g.title, items, searching);
        }
        adapter.notifyDataSetChanged();
    }

    private void addSection(String title, List<Row> items, boolean searching) {
        if (items.isEmpty()) return;
        boolean open = searching || expanded.contains(title);
        rows.add(new Row(ROW_HEADER, (open ? "▾ " : "▸ ") + title, String.valueOf(items.size()), null,
                () -> {
                    if (!expanded.remove(title)) expanded.add(title);
                    rebuildRows();
                }));
        if (open) rows.addAll(items);
    }

    private Row toolRow(Entry e) {
        CheatSheet.Tool t = e.tool;
        boolean cli = t.termuxScript() != null;
        String subtitle = (cli ? ">_ Termux · " : "") + t.host()
                + (t.description.isEmpty() ? "" : " — " + t.description);
        return new Row(ROW_ITEM, t.name, subtitle, e, null);
    }

    private void onRowClick(Row row) {
        if (row.action != null) {
            row.action.run();
        } else if (row.entry != null) {
            // Keep the remote screen in sync with what was picked from the list.
            for (int g = 0; g < groups.size(); g++) {
                int i = groups.get(g).entries.indexOf(row.entry);
                if (i >= 0) {
                    remember();
                    group = g;
                    item = i;
                    showEntry();
                    break;
                }
            }
            open(row.entry);
        }
    }

    // ---------------------------------------------------------------- shared actions

    private void showToolMenu(CheatSheet.Tool tool) {
        String script = tool.termuxScript();
        boolean favorite = favorites().contains(tool.url);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (script != null) {
            labels.add("▶ Install & run in Termux");
            actions.add(() -> confirmScript(tool, script));
        }
        labels.add("Open in OSINT browser");
        actions.add(() -> Ui.openInApp(this, tool.url));
        labels.add("Open in external browser");
        actions.add(() -> Ui.openExternal(this, tool.url));
        labels.add("Open archived copy (Wayback Machine)");
        actions.add(() -> Ui.openInApp(this, Ui.waybackUrl(tool.url)));
        labels.add(favorite ? "Remove from favorites" : "Add to favorites");
        actions.add(() -> toggleFavorite(tool.url));
        labels.add("Copy link");
        actions.add(() -> Ui.copy(this, tool.url));
        labels.add("Share");
        actions.add(() -> Ui.share(this, tool.name, tool.name + " — " + tool.url));

        new AlertDialog.Builder(this)
                .setTitle(tool.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** Shows (and lets the user edit) the exact script before it is sent to Termux. */
    private void confirmScript(CheatSheet.Tool tool, String script) {
        EditText editor = new EditText(this);
        editor.setText(script);
        editor.setTextSize(18);
        editor.setHorizontallyScrolling(true);
        editor.setGravity(Gravity.TOP | Gravity.START);
        new AlertDialog.Builder(this)
                .setTitle("Run " + tool.name + " in Termux")
                .setView(editor)
                .setPositiveButton("Run", (d, w) -> Termux.run(this, editor.getText().toString()))
                .setNeutralButton("Copy", (d, w) -> Ui.copy(this, editor.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptUrl() {
        EditText input = new EditText(this);
        input.setHint("example.com or https://…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Open URL")
                .setView(input)
                .setPositiveButton("Open", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) Ui.openInApp(this, CheatSheet.normalizeUrl(url));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    private void toggleFavorite(String url) {
        Set<String> favorites = favorites();
        boolean added = favorites.add(url);
        if (!added) favorites.remove(url);
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();

        // Favorites is group 0 when present, so keep pointing at the same entry.
        Entry shown = current();
        buildGroups();
        history.clear();
        group = 0;
        item = 0;
        for (int g = 0; g < groups.size(); g++) {
            List<Entry> entries = groups.get(g).entries;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                boolean same = e.section.equals(shown.section)
                        && (e.tool == null ? shown.tool == null : shown.tool != null && e.tool.url.equals(shown.tool.url));
                if (same) {
                    group = g;
                    item = i;
                }
            }
        }
        showEntry();
        rebuildRows();
        Ui.toast(this, added ? "Added to favorites" : "Removed from favorites");
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Open URL");
        menu.add(0, 2, 0, "Expand all sections");
        menu.add(0, 3, 0, "Collapse all sections");
        menu.add(0, 4, 0, "About");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                promptUrl();
                return true;
            case 2:
                for (Group g : groups) expanded.add(g.title);
                rebuildRows();
                return true;
            case 3:
                expanded.clear();
                expanded.add(FAVORITES);
                rebuildRows();
                return true;
            case 4:
                TextActivity.show(this, "About", "VTLOsint — all-in-one OSINT toolkit\n\n"
                        + toolsByUrl.size() + " tools in " + sections.size() + " sections, generated from "
                        + "README.md and awesome-article.md of the OSINT-Cheat-sheet repository.\n\n"
                        + "REMOTE\n"
                        + "◀ ▶  previous / next tool\n"
                        + "▲ ▼  previous / next category\n"
                        + "●    open the tool (hold for options)\n"
                        + "↩    back to the tool you saw before\n\n"
                        + "SEARCH\n"
                        + "• Tap a web tool to open it in the built-in browser.\n"
                        + "• Tools marked >_ Termux are command-line programs: tap to install & run them in Termux.\n"
                        + "• Long-press any tool for Wayback, favorites, share and more.\n\n"
                        + "Use these tools legally and ethically, only on targets you are authorized to investigate.\n\n"
                        + "Fonts: VT323 and Silkscreen, SIL Open Font License 1.1.\n"
                        + "Original list: https://github.com/Jieyab89/OSINT-Cheat-sheet");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        Termux.onPermissionResult(this, requestCode, results);
    }

    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return rows.get(position).type; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            return row.type == ROW_HEADER ? header(row, convertView) : item(row, convertView);
        }

        private View header(Row row, View convertView) {
            LinearLayout layout = (LinearLayout) convertView;
            if (layout == null) {
                layout = new LinearLayout(MainActivity.this);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                layout.setBackgroundColor(Flipper.LCD);
                int h = Ui.dp(MainActivity.this, 14), v = Ui.dp(MainActivity.this, 10);
                layout.setPadding(h, v, h, v);
                TextView title = Flipper.lcdText(MainActivity.this, 24, true);
                layout.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
                layout.addView(Flipper.lcdText(MainActivity.this, 20, false));
            }
            ((TextView) layout.getChildAt(0)).setText(row.title);
            ((TextView) layout.getChildAt(1)).setText(row.subtitle);
            return layout;
        }

        private View item(Row row, View convertView) {
            LinearLayout layout = (LinearLayout) convertView;
            if (layout == null) {
                layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int h = Ui.dp(MainActivity.this, 20), v = Ui.dp(MainActivity.this, 8);
                layout.setPadding(h, v, h, v);
                layout.addView(Ui.text(MainActivity.this, "", 16, false));
                TextView sub = Ui.text(MainActivity.this, "", 12, false);
                sub.setTextColor(getColor(R.color.orange_dim));
                sub.setMaxLines(2);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                layout.addView(sub);
            }
            ((TextView) layout.getChildAt(0)).setText(row.title);
            ((TextView) layout.getChildAt(1)).setText(row.subtitle);
            return layout;
        }
    }
}
