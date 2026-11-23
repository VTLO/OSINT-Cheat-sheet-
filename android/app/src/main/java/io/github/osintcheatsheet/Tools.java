package io.github.osintcheatsheet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Actions on tools shared by the home, phase and search screens. */
final class Tools {

    private static final String PREFS = "osint";
    private static final String KEY_FAVORITES = "favorites";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Set<String> favorites(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    /** Returns true when the tool was added, false when it was removed. */
    static boolean toggleFavorite(Context c, String url) {
        Set<String> favorites = favorites(c);
        boolean added = favorites.add(url);
        if (!added) favorites.remove(url);
        prefs(c).edit().putStringSet(KEY_FAVORITES, favorites).apply();
        Ui.toast(c, added ? "Ajouté aux favoris" : "Retiré des favoris");
        return added;
    }

    static boolean isTerminal(CheatSheet.Tool tool) {
        return tool.termuxScript() != null;
    }

    /** Web tools open in the built-in browser; command-line tools show their options. */
    static void open(Activity a, CheatSheet.Tool tool, Runnable onChange) {
        if (isTerminal(tool)) menu(a, tool, onChange);
        else Ui.openInApp(a, tool.url);
    }

    static void menu(Activity a, CheatSheet.Tool tool, Runnable onChange) {
        String script = tool.termuxScript();
        boolean favorite = favorites(a).contains(tool.url);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (script != null) {
            labels.add("▶ Installer et lancer dans Termux");
            actions.add(() -> confirmScript(a, tool, script));
        }
        labels.add("Ouvrir dans le navigateur intégré");
        actions.add(() -> Ui.openInApp(a, tool.url));
        labels.add("Ouvrir dans le navigateur externe");
        actions.add(() -> Ui.openExternal(a, tool.url));
        labels.add("Copie archivée (Wayback Machine)");
        actions.add(() -> Ui.openInApp(a, Ui.waybackUrl(tool.url)));
        labels.add(favorite ? "Retirer des favoris" : "Ajouter aux favoris");
        actions.add(() -> {
            toggleFavorite(a, tool.url);
            if (onChange != null) onChange.run();
        });
        labels.add("Copier le lien");
        actions.add(() -> Ui.copy(a, tool.url));
        labels.add("Partager");
        actions.add(() -> Ui.share(a, tool.name, tool.name + " — " + tool.url));

        new AlertDialog.Builder(a)
                .setTitle(tool.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** Shows (and lets the user edit) the exact script before it is sent to Termux. */
    private static void confirmScript(Activity a, CheatSheet.Tool tool, String script) {
        EditText editor = new EditText(a);
        editor.setText(script);
        editor.setTypeface(Panel.mono(a));
        editor.setTextSize(18);
        editor.setHorizontallyScrolling(true);
        editor.setGravity(Gravity.TOP | Gravity.START);
        new AlertDialog.Builder(a)
                .setTitle("Lancer " + tool.name + " dans Termux")
                .setView(editor)
                .setPositiveButton("Lancer", (d, w) -> Termux.run(a, editor.getText().toString()))
                .setNeutralButton("Copier", (d, w) -> Ui.copy(a, editor.getText().toString()))
                .setNegativeButton("Annuler", null)
                .show();
    }

    static String detail(CheatSheet.Tool t) {
        return t.host() + (t.description.isEmpty() ? "" : " — " + t.description);
    }

    /**
     * A section as a tabbed card listing its notes and tools, or null when the query
     * (lower-case, may be empty) matches nothing in it.
     */
    static Panel.Card sectionCard(Activity a, CheatSheet.Section s, String query, Runnable onChange) {
        boolean searching = query != null && !query.isEmpty();
        Panel.Card card = new Panel.Card(a, s.title.toUpperCase(Locale.ROOT));
        int rows = 0;
        if (s.hasNotes() && (!searching || s.notes.toString().toLowerCase(Locale.ROOT).contains(query)
                || s.title.toLowerCase(Locale.ROOT).contains(query))) {
            String notes = s.notes.toString();
            LinearLayout row = Panel.toolRow(a, "≡ Notes, requêtes et commandes", firstLine(notes), null);
            clickable(row);
            row.setOnClickListener(v -> TextActivity.show(a, s.title, notes));
            card.content.addView(row);
            rows++;
        }
        for (CheatSheet.Tool t : s.tools) {
            if (searching && !t.matches(query)) continue;
            if (rows > 0) card.content.addView(Panel.divider(a));
            LinearLayout row = Panel.toolRow(a, t.name, detail(t), isTerminal(t));
            clickable(row);
            row.setOnClickListener(v -> open(a, t, onChange));
            row.setOnLongClickListener(v -> {
                menu(a, t, onChange);
                return true;
            });
            card.content.addView(row);
            rows++;
        }
        return rows == 0 ? null : card;
    }

    static void clickable(View v) {
        TypedValue tv = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setBackgroundResource(tv.resourceId);
    }

    /** Touch feedback drawn over a view that already has its own background. */
    static void rippleOver(View v) {
        TypedValue tv = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(v.getContext().getDrawable(tv.resourceId));
    }

    static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    private Tools() {
    }
}
