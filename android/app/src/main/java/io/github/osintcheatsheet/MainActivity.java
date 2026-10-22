package io.github.osintcheatsheet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Searchable, collapsible list of every tool in the cheat sheet plus the built-in tools. */
public class MainActivity extends Activity {

    private static final String PREFS = "osint";
    private static final String KEY_FAVORITES = "favorites";
    private static final String BUILT_IN = "Built-in tools";
    private static final String FAVORITES = "Favorites";

    private static final int ROW_HEADER = 0;
    private static final int ROW_ITEM = 1;

    /** One list row: a section header, a tool, or an action (built-in tool / notes). */
    private static final class Row {
        final int type;
        final String title;
        final String subtitle;
        final CheatSheet.Tool tool;
        final Runnable action;

        Row(int type, String title, String subtitle, CheatSheet.Tool tool, Runnable action) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.tool = tool;
            this.action = action;
        }
    }

    private List<CheatSheet.Section> sections;
    private Map<String, CheatSheet.Tool> toolsByUrl;
    private final Set<String> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private String query = "";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sections = CheatSheet.load(this);
        toolsByUrl = CheatSheet.toolsByUrl(sections);
        expanded.add(BUILT_IN);
        expanded.add(FAVORITES);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        EditText search = new EditText(this);
        search.setHint("Search " + toolsByUrl.size() + " tools…");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        int pad = Ui.dp(this, 12);
        search.setPadding(pad, pad, pad, pad);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuild();
            }
        });
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setFastScrollEnabled(true);
        list.setOnItemClickListener((parent, view, position, id) -> onRowClick(rows.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (row.tool == null) return false;
            showToolMenu(row.tool);
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        boolean searching = !query.isEmpty();

        List<Row> builtIns = new ArrayList<>();
        addBuiltIn(builtIns, "Google Dork Builder", "Build dorks, Facebook/LinkedIn/MySpace queries and FQL links",
                () -> startActivity(new Intent(this, DorkActivity.class)));
        addBuiltIn(builtIns, "EXIF Metadata Viewer / Remover", "Read GPS, camera & date tags; save a clean copy",
                () -> startActivity(new Intent(this, ExifActivity.class)));
        addBuiltIn(builtIns, "Open URL in OSINT browser", "Built-in browser with Wayback, share & external open",
                this::promptUrl);
        addBuiltIn(builtIns, "Termux setup (for CLI tools)", "Run Sherlock, Maigret, GHunt, theHarvester… on your phone",
                () -> Termux.showSetupHelp(this, null));
        addSection(BUILT_IN, builtIns, searching);

        List<Row> favorites = new ArrayList<>();
        for (String url : prefs.getStringSet(KEY_FAVORITES, new HashSet<>())) {
            CheatSheet.Tool t = toolsByUrl.get(url);
            if (t != null && (!searching || t.matches(query))) favorites.add(toolRow(t));
        }
        if (!favorites.isEmpty()) addSection(FAVORITES, favorites, searching);

        for (CheatSheet.Section s : sections) {
            List<Row> items = new ArrayList<>();
            if (s.hasNotes() && (!searching || s.notes.toString().toLowerCase(Locale.ROOT).contains(query)
                    || s.title.toLowerCase(Locale.ROOT).contains(query))) {
                String notes = s.notes.toString();
                items.add(new Row(ROW_ITEM, "📝 Notes, queries & commands", firstLine(notes), null,
                        () -> TextActivity.show(this, s.title, notes)));
            }
            for (CheatSheet.Tool t : s.tools) {
                if (!searching || t.matches(query)) items.add(toolRow(t));
            }
            addSection(s.title, items, searching);
        }
        adapter.notifyDataSetChanged();
    }

    private void addBuiltIn(List<Row> out, String title, String subtitle, Runnable action) {
        if (query.isEmpty() || (title + " " + subtitle).toLowerCase(Locale.ROOT).contains(query)) {
            out.add(new Row(ROW_ITEM, "★ " + title, subtitle, null, action));
        }
    }

    private void addSection(String title, List<Row> items, boolean searching) {
        if (items.isEmpty()) return;
        boolean open = searching || expanded.contains(title);
        rows.add(new Row(ROW_HEADER, (open ? "▾  " : "▸  ") + title, String.valueOf(items.size()), null,
                () -> {
                    if (!expanded.remove(title)) expanded.add(title);
                    rebuild();
                }));
        if (open) rows.addAll(items);
    }

    private Row toolRow(CheatSheet.Tool t) {
        boolean cli = t.termuxScript() != null;
        String subtitle = (cli ? "⌨ Termux · " : "") + t.host()
                + (t.description.isEmpty() ? "" : " — " + t.description);
        return new Row(ROW_ITEM, t.name, subtitle, t, null);
    }

    private void onRowClick(Row row) {
        if (row.action != null) {
            row.action.run();
        } else if (row.tool != null) {
            if (row.tool.termuxScript() != null) showToolMenu(row.tool);
            else Ui.openInApp(this, row.tool.url);
        }
    }

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
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setHorizontallyScrolling(true);
        editor.setGravity(Gravity.TOP | Gravity.START);
        int pad = Ui.dp(this, 16);
        editor.setPadding(pad, pad, pad, pad);
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
        if (!favorites.remove(url)) favorites.add(url);
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        rebuild();
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Expand all");
        menu.add(0, 2, 0, "Collapse all");
        menu.add(0, 3, 0, "About");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                for (CheatSheet.Section s : sections) expanded.add(s.title);
                rebuild();
                return true;
            case 2:
                expanded.clear();
                expanded.add(BUILT_IN);
                expanded.add(FAVORITES);
                rebuild();
                return true;
            case 3:
                TextActivity.show(this, "About", "OSINT Cheat Sheet — all-in-one\n\n"
                        + toolsByUrl.size() + " tools in " + sections.size() + " sections, generated from "
                        + "README.md and awesome-article.md of the OSINT-Cheat-sheet repository.\n\n"
                        + "• Tap a web tool to open it in the built-in browser.\n"
                        + "• Tools marked ⌨ are command-line programs: tap to install & run them in Termux.\n"
                        + "• Long-press any tool for Wayback, favorites, share and more.\n\n"
                        + "Use these tools legally and ethically, only on targets you are authorized to investigate.\n\n"
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
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setBackgroundColor(getColor(R.color.header_bg));
                int h = Ui.dp(MainActivity.this, 14), v = Ui.dp(MainActivity.this, 12);
                layout.setPadding(h, v, h, v);
                TextView title = Ui.text(MainActivity.this, "", 16, true);
                title.setTextColor(getColor(R.color.accent));
                layout.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
                layout.addView(Ui.text(MainActivity.this, "", 13, false));
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
                int h = Ui.dp(MainActivity.this, 22), v = Ui.dp(MainActivity.this, 9);
                layout.setPadding(h, v, h, v);
                layout.addView(Ui.text(MainActivity.this, "", 16, false));
                TextView sub = Ui.text(MainActivity.this, "", 12, false);
                sub.setAlpha(0.7f);
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
