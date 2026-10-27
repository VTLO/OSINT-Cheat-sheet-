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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
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
 * Home screen, a mix of three devices: an infographic header and section card, an MPC-style
 * chassis with an 8-pad bank of tools and REC/PLAY/STOP keys, and a Flipper LCD and D-pad.
 * "Search" swaps the deck for the searchable, collapsible list.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "osint";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_GROUP = "remote_group";
    private static final String KEY_ITEM = "remote_item";
    private static final String FAVORITES = "Favorites";
    private static final int HISTORY_LIMIT = 50;
    private static final int BANK_SIZE = 8;

    /** A navigable item: either a tool or a section's notes page. */
    private static final class Entry {
        final String section;
        final CheatSheet.Tool tool;
        final String notes;

        Entry(String section, CheatSheet.Tool tool, String notes) {
            this.section = section;
            this.tool = tool;
            this.notes = notes;
        }

        int padState() {
            if (tool == null) return Panel.PAD_STATE_NOTES;
            return tool.termuxScript() != null ? Panel.PAD_STATE_CLI : Panel.PAD_STATE_WEB;
        }

        String label() {
            return tool == null ? "Notes" : tool.name;
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

    // Deck mode
    private final List<Group> groups = new ArrayList<>();
    private final Deque<int[]> history = new ArrayDeque<>();
    private int group;
    private int item;
    private View deckView;
    private Panel.SectionCard sectionCard;
    private TextView lcdStatus;
    private TextView lcdName;
    private TextView lcdHost;
    private TextView lcdType;
    private final TextView[] pads = new TextView[BANK_SIZE];
    private Panel.TransportKey recKey;

    // Search mode
    private final Set<String> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private View listView;
    private RowAdapter adapter;
    private String query = "";
    private LinearLayout modeButton;

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
        root.setBackground(new Panel.CircuitDrawable(this));
        root.addView(buildHeader());

        LinearLayout top = new LinearLayout(this);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 10));
        modeButton = addTopButton(top, R.drawable.ic_search, "Search", v -> toggleMode());
        addTopButton(top, R.drawable.ic_globe, "Dorks", v -> startActivity(new Intent(this, DorkActivity.class)));
        addTopButton(top, R.drawable.ic_camera, "EXIF", v -> startActivity(new Intent(this, ExifActivity.class)));
        addTopButton(top, R.drawable.ic_terminal, "Termux", v -> Termux.showSetupHelp(this, null));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        deckView = buildDeck();
        listView = buildList();
        listView.setVisibility(View.GONE);
        content.addView(deckView);
        content.addView(listView);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        showEntry();
    }

    /** Infographic-style title block with an overflow menu. */
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 8), 0);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Panel.text(this, 30, Panel.BLUE, true);
        title.setText("VTLOSINT");
        title.setLetterSpacing(0.02f);
        title.setIncludeFontPadding(false);
        titles.addView(title);
        TextView subtitle = Panel.text(this, 11, Panel.TEXT_DIM, true);
        subtitle.setLetterSpacing(0.12f);
        subtitle.setText(String.format(Locale.ROOT, "OSINT TOOLKIT · %d TOOLS · %d SECTIONS",
                toolsByUrl.size(), sections.size()));
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more);
        int p = Ui.dp(this, 10);
        more.setPadding(p, p, p, p);
        more.setContentDescription("More options");
        more.setOnClickListener(this::showOverflow);
        header.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return header;
    }

    private LinearLayout addTopButton(LinearLayout top, int icon, String label, View.OnClickListener click) {
        LinearLayout button = Flipper.iconButton(this, icon, label, click);
        top.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
        return button;
    }

    // ---------------------------------------------------------------- deck mode

    private View buildDeck() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 16);
        column.setPadding(side, 0, side, Ui.dp(this, 16));

        sectionCard = new Panel.SectionCard(this);
        sectionCard.setOnClickListener(v -> jumpGroup(1));
        column.addView(sectionCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chassis = new LinearLayout(this);
        chassis.setOrientation(LinearLayout.VERTICAL);
        chassis.setBackground(Panel.chassis(this));
        int cp = Ui.dp(this, 12);
        chassis.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams chassisLp = new LinearLayout.LayoutParams(-1, -2);
        chassisLp.topMargin = Ui.dp(this, 14);
        column.addView(chassis, chassisLp);

        chassis.addView(buildLcd(), new LinearLayout.LayoutParams(-1, -2));

        // 4 x 2 pad grid on a dark grid background, like the MPC's pad matrix.
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackground(Flipper.rounded(Panel.NAVY_GRID, 0, 0, 6, this));
        int gap = Ui.dp(this, 5);
        grid.setPadding(gap, gap, gap, gap);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 4; c++) {
                int index = r * 4 + c;
                TextView pad = Panel.pad(this);
                pad.setOnClickListener(v -> onPad(index));
                pad.setOnLongClickListener(v -> {
                    selectPad(index);
                    showCurrentMenu();
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f);
                if (c > 0) lp.leftMargin = gap;
                row.addView(pad, lp);
                pads[index] = pad;
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            if (r > 0) rowLp.topMargin = gap;
            grid.addView(row, rowLp);
        }
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.topMargin = Ui.dp(this, 12);
        chassis.addView(grid, gridLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Flipper.DPad dpad = new Flipper.DPad(this);
        dpad.setListener(new Flipper.DPad.Listener() {
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
        int padSize = Ui.dp(this, 148);
        controls.addView(dpad, new LinearLayout.LayoutParams(padSize, padSize));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout banks = new LinearLayout(this);
        banks.setGravity(Gravity.CENTER);
        banks.addView(Panel.pill(this, "BANK ◀", Panel.KEY, v -> jumpBank(-1)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        banks.addView(Panel.pill(this, "BANK ▶", Flipper.ORANGE, v -> jumpBank(1)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        right.addView(banks, new LinearLayout.LayoutParams(-1, -2));
        right.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout transport = new LinearLayout(this);
        recKey = new Panel.TransportKey(this, "REC", Panel.LED_RED, Panel.REC_RED, v -> toggleFavoriteCurrent());
        transport.addView(recKey, keyLp(false));
        transport.addView(new Panel.TransportKey(this, "PLAY", Panel.LED_GREEN, Panel.KEY, v -> openCurrent()),
                keyLp(true));
        transport.addView(new Panel.TransportKey(this, "STOP", 0, Panel.KEY, v -> goBack()), keyLp(true));
        right.addView(transport, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, padSize, 1f);
        rightLp.leftMargin = Ui.dp(this, 10);
        controls.addView(right, rightLp);

        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.topMargin = Ui.dp(this, 12);
        chassis.addView(controls, controlsLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column);
        return scroll;
    }

    private LinearLayout.LayoutParams keyLp(boolean gapBefore) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (gapBefore) lp.leftMargin = Ui.dp(this, 6);
        return lp;
    }

    /** Orange Flipper screen with an MPC-style status strip on top. */
    private View buildLcd() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        lcdStatus = Flipper.lcdText(this, 18, false);
        lcdStatus.setSingleLine(true);
        lcdStatus.setEllipsize(TextUtils.TruncateAt.END);
        lcdStatus.setBackgroundColor(Flipper.LCD_BAR);
        lcdStatus.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        screen.addView(lcdStatus, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 10);
        body.setPadding(pad, Ui.dp(this, 6), pad, Ui.dp(this, 8));
        lcdName = Flipper.lcdText(this, 30, true);
        lcdName.setSingleLine(true);
        lcdName.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(lcdName);
        lcdHost = Flipper.lcdText(this, 22, false);
        lcdHost.setSingleLine(true);
        lcdHost.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(lcdHost);
        lcdType = Flipper.lcdText(this, 22, false);
        lcdType.setSingleLine(true);
        lcdType.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(lcdType);

        LinearLayout keys = new LinearLayout(this);
        keys.addView(Flipper.softKey(this, "◀ Prev.", v -> move(-1)));
        keys.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        keys.addView(Flipper.softKey(this, "Next ▶", v -> move(1)));
        LinearLayout.LayoutParams keysLp = new LinearLayout.LayoutParams(-1, -2);
        keysLp.topMargin = Ui.dp(this, 6);
        body.addView(keys, keysLp);
        screen.addView(body);

        View lcd = Flipper.lcd(this, screen);
        lcd.setOnClickListener(v -> openCurrent());
        lcd.setOnLongClickListener(v -> {
            showCurrentMenu();
            return true;
        });
        return lcd;
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

    /** Switches to the previous/next bank of eight pads within the section, wrapping around. */
    private void jumpBank(int step) {
        int size = groups.get(group).entries.size();
        int banks = (size + BANK_SIZE - 1) / BANK_SIZE;
        if (banks <= 1) {
            Ui.toast(this, "This section has a single bank");
            return;
        }
        remember();
        int bank = (item / BANK_SIZE + step + banks) % banks;
        item = bank * BANK_SIZE;
        showEntry();
    }

    private void onPad(int index) {
        int target = item / BANK_SIZE * BANK_SIZE + index;
        if (target == item) openCurrent();
        else selectPad(index);
    }

    private void selectPad(int index) {
        int target = item / BANK_SIZE * BANK_SIZE + index;
        if (target >= groups.get(group).entries.size() || target == item) return;
        remember();
        item = target;
        showEntry();
    }

    private void goBack() {
        if (history.isEmpty()) {
            Ui.toast(this, "Nothing to go back to");
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
        int bank = item / BANK_SIZE;
        int banks = (g.entries.size() + BANK_SIZE - 1) / BANK_SIZE;
        int tools = 0;
        for (Entry x : g.entries) if (x.tool != null) tools++;

        sectionCard.set(String.format(Locale.ROOT, "SECTION %02d / %02d", group + 1, groups.size()),
                tools + (tools == 1 ? " tool" : " tools") + (tools < g.entries.size() ? " · notes" : ""),
                g.title);

        lcdStatus.setText(String.format(Locale.ROOT, "Tool [%d/%d]  BANK %c%s",
                item + 1, g.entries.size(), (char) ('A' + bank % 26), banks > 1 ? "/" + banks : ""));
        if (e.tool != null) {
            lcdName.setText(e.tool.name);
            lcdHost.setText("Host: " + e.tool.host());
            String type = e.tool.termuxScript() != null ? "CLI · Termux" : "Web tool";
            lcdType.setText(e.tool.description.isEmpty() ? "Type: " + type : type + " · " + e.tool.description);
        } else {
            lcdName.setText("Notes & commands");
            lcdHost.setText("Host: cheat sheet");
            lcdType.setText("Type: Notes");
        }

        for (int i = 0; i < BANK_SIZE; i++) {
            int index = bank * BANK_SIZE + i;
            if (index < g.entries.size()) {
                Entry pe = g.entries.get(index);
                Panel.stylePad(pads[i], pe.padState(), index == item, pe.label());
            } else {
                Panel.stylePad(pads[i], Panel.PAD_STATE_EMPTY, false, "");
            }
        }
        recKey.setLed(e.tool != null && favorites().contains(e.tool.url));
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

    private void toggleFavoriteCurrent() {
        Entry e = current();
        if (e.tool == null) Ui.toast(this, "Notes can't be favorited");
        else toggleFavorite(e.tool.url);
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
        int m = Ui.dp(this, 16);
        searchLp.setMargins(m, 0, m, Ui.dp(this, 8));
        column.addView(search, searchLp);

        ListView list = new ListView(this);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setFastScrollEnabled(true);
        list.setDivider(new ColorDrawable(Panel.SURFACE));
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
        deckView.setVisibility(toList ? View.GONE : View.VISIBLE);
        ((ImageView) modeButton.getChildAt(0)).setImageResource(toList ? R.drawable.ic_dpad : R.drawable.ic_search);
        ((TextView) modeButton.getChildAt(1)).setText(toList ? "DECK" : "SEARCH");
        modeButton.setContentDescription(toList ? "Deck" : "Search");
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
        rows.add(new Row(ROW_HEADER, (open ? "▾ " : "▸ ") + title.toUpperCase(Locale.ROOT),
                String.valueOf(items.size()), null,
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
            // Keep the deck in sync with what was picked from the list.
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
        editor.setTypeface(Flipper.pixel(this));
        editor.setTextColor(Flipper.ORANGE);
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

    private void showOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Open URL");
        menu.getMenu().add(0, 2, 0, "Expand all sections");
        menu.getMenu().add(0, 3, 0, "Collapse all sections");
        menu.getMenu().add(0, 4, 0, "About");
        menu.setOnMenuItemClickListener(mi -> {
            switch (mi.getItemId()) {
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
                default:
                    showAbout();
                    return true;
            }
        });
        menu.show();
    }

    private void showAbout() {
        TextActivity.show(this, "About", "VTLOsint — all-in-one OSINT toolkit\n\n"
                + toolsByUrl.size() + " tools in " + sections.size() + " sections, generated from "
                + "README.md and awesome-article.md of the OSINT-Cheat-sheet repository.\n\n"
                + "DECK\n"
                + "Pads      the current bank of 8 tools; tap to select, tap again to open\n"
                + "          grey = web · green = Termux · yellow = notes\n"
                + "◀ ▶       previous / next tool\n"
                + "▲ ▼       previous / next section (or tap the section card)\n"
                + "●         open the tool (hold for options)\n"
                + "BANK ◀ ▶  previous / next bank of 8\n"
                + "REC       add/remove favorite (red LED = favorite)\n"
                + "PLAY      open the tool\n"
                + "STOP      back to the tool you saw before\n\n"
                + "SEARCH\n"
                + "• Tap a web tool to open it in the built-in browser.\n"
                + "• Tools marked >_ Termux are command-line programs: tap to install & run them in Termux.\n"
                + "• Long-press any tool for Wayback, favorites, share and more.\n\n"
                + "Use these tools legally and ethically, only on targets you are authorized to investigate.\n\n"
                + "Fonts: Montserrat and VT323, SIL Open Font License 1.1.\n"
                + "Original list: https://github.com/Jieyab89/OSINT-Cheat-sheet");
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

        /** Section header in the infographic's angled blue tab. */
        private View header(Row row, View convertView) {
            LinearLayout layout = (LinearLayout) convertView;
            if (layout == null) {
                layout = new LinearLayout(MainActivity.this);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                int h = Ui.dp(MainActivity.this, 16), v = Ui.dp(MainActivity.this, 10);
                layout.setPadding(h, v, h, Ui.dp(MainActivity.this, 6));
                TextView tab = Panel.tab(MainActivity.this, "");
                LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, -2, 1f);
                tabLp.rightMargin = Ui.dp(MainActivity.this, 40);
                LinearLayout tabBox = new LinearLayout(MainActivity.this);
                tabBox.addView(tab, new LinearLayout.LayoutParams(-2, -2));
                layout.addView(tabBox, tabLp);
                layout.addView(Panel.text(MainActivity.this, 13, Panel.BLUE, true));
            }
            ((TextView) ((LinearLayout) layout.getChildAt(0)).getChildAt(0)).setText(row.title);
            ((TextView) layout.getChildAt(1)).setText(row.subtitle);
            return layout;
        }

        private View item(Row row, View convertView) {
            LinearLayout layout = (LinearLayout) convertView;
            if (layout == null) {
                layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int h = Ui.dp(MainActivity.this, 20), v = Ui.dp(MainActivity.this, 10);
                layout.setPadding(h, v, h, v);
                layout.addView(Panel.text(MainActivity.this, 16, Panel.TEXT, true));
                TextView sub = Panel.text(MainActivity.this, 12.5f, Panel.TEXT_DIM, false);
                sub.setMaxLines(2);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                layout.addView(sub);
            }
            ((TextView) layout.getChildAt(0)).setText(row.title);
            TextView sub = (TextView) layout.getChildAt(1);
            sub.setText(row.subtitle);
            sub.setTextColor(row.subtitle.startsWith(">_") ? Panel.PAD_GREEN : Panel.TEXT_DIM);
            return layout;
        }
    }
}
