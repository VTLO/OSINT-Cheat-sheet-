package io.github.osintcheatsheet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Home screen laid out like an investigation pipeline: phases in tabbed cards along a circuit
 * spine (Recherche → Identités → Réseaux sociaux → Images & Géo → Infrastructure → Vérification).
 * Typing in the search box swaps the pipeline for matching sections.
 */
public class MainActivity extends Activity {

    private List<CheatSheet.Section> sections;
    private Map<String, CheatSheet.Tool> toolsByUrl;
    private List<Phases.Phase> phases;
    private LinearLayout pipeline;
    private LinearLayout results;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sections = CheatSheet.load(this);
        toolsByUrl = CheatSheet.toolsByUrl(sections);
        phases = Phases.load(this);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 16);
        column.setPadding(side, Ui.dp(this, 10), side, Ui.dp(this, 24));
        column.addView(buildHeader());

        EditText search = new EditText(this);
        search.setHint("Rechercher parmi " + toolsByUrl.size() + " outils…");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                refresh();
            }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
        searchLp.topMargin = Ui.dp(this, 14);
        column.addView(search, searchLp);

        LinearLayout quick = new LinearLayout(this);
        quick.addView(Panel.quickButton(this, R.drawable.ic_globe, "Dorks",
                v -> startActivity(new Intent(this, DorkActivity.class))), weight());
        quick.addView(Panel.quickButton(this, R.drawable.ic_camera, "EXIF",
                v -> startActivity(new Intent(this, ExifActivity.class))), weight());
        quick.addView(Panel.quickButton(this, R.drawable.ic_terminal, "Termux",
                v -> Termux.showSetupHelp(this, null)), weight());
        quick.addView(Panel.quickButton(this, R.drawable.ic_guide, "Guide",
                v -> startActivity(new Intent(this, GuideActivity.class))), weight());
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(-1, -2);
        quickLp.topMargin = Ui.dp(this, 14);
        quickLp.bottomMargin = Ui.dp(this, 18);
        column.addView(quick, quickLp);

        pipeline = new LinearLayout(this);
        pipeline.setOrientation(LinearLayout.VERTICAL);
        column.addView(pipeline);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        column.addView(results);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);

        if (savedInstanceState == null && !Tools.prefs(this).getBoolean(GuideActivity.KEY_SEEN, false)) {
            startActivity(new Intent(this, GuideActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // favorites may have changed on another screen
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    /** Big blue two-line title like the infographic's, with the overflow menu. */
    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Panel.text(this, 38, Panel.BLUE, true);
        title.setText("VTLOSINT");
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        titles.addView(title);
        TextView sub = Panel.text(this, 19, Panel.BLUE, true);
        sub.setText("PIPELINE D'ENQUÊTE OSINT");
        sub.setGravity(Gravity.CENTER);
        titles.addView(sub);
        TextView stats = Panel.text(this, 11, Panel.TEXT_DIM, true);
        stats.setLetterSpacing(0.1f);
        stats.setText(String.format(Locale.ROOT, "%d OUTILS · %d SECTIONS · %d PHASES",
                toolsByUrl.size(), sections.size(), phases.size()));
        stats.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-2, -2);
        statsLp.topMargin = Ui.dp(this, 4);
        titles.addView(stats, statsLp);
        FrameLayout.LayoutParams titlesLp = new FrameLayout.LayoutParams(-1, -2);
        titlesLp.topMargin = Ui.dp(this, 14);
        header.addView(titles, titlesLp);

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more);
        int p = Ui.dp(this, 10);
        more.setPadding(p, p, p, p);
        more.setContentDescription("Plus d'options");
        more.setOnClickListener(this::showOverflow);
        header.addView(more, new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.END));
        return header;
    }

    private void refresh() {
        boolean searching = !query.isEmpty();
        pipeline.setVisibility(searching ? View.GONE : View.VISIBLE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (searching) buildResults();
        else buildPipeline();
    }

    private void buildPipeline() {
        pipeline.removeAllViews();
        Set<String> favorites = Tools.favorites(this);
        boolean hasFavorites = false;
        Panel.Card favCard = new Panel.Card(this, "FAVORIS");
        int n = 0;
        for (String url : favorites) {
            CheatSheet.Tool t = toolsByUrl.get(url);
            if (t == null) continue;
            if (n++ > 0) favCard.content.addView(Panel.divider(this));
            LinearLayout row = Panel.toolRow(this, t.name, Tools.detail(t), Tools.isTerminal(t));
            Tools.clickable(row);
            row.setOnClickListener(v -> Tools.open(this, t, this::refresh));
            row.setOnLongClickListener(v -> {
                Tools.menu(this, t, this::refresh);
                return true;
            });
            favCard.content.addView(row);
            hasFavorites = true;
        }
        if (hasFavorites) pipeline.addView(Panel.spineRow(this, favCard, true, false, 30));

        for (int i = 0; i < phases.size(); i++) {
            Phases.Phase phase = phases.get(i);
            boolean first = i == 0 && !hasFavorites;
            boolean last = i == phases.size() - 1;
            pipeline.addView(Panel.spineRow(this, phaseCard(phase, i), first, last, 30));
            if (i == 2) {
                TextView quote = Panel.quote(this, "La vérification croisée des sources est le cœur de l'OSINT.");
                pipeline.addView(Panel.spineRow(this, quote, false, false, 50));
            }
        }
    }

    private View phaseCard(Phases.Phase phase, int index) {
        Panel.Card card = new Panel.Card(this, "PHASE " + phase.id);
        ImageView icon = new ImageView(this);
        icon.setImageResource(phase.iconRes());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64));
        iconLp.bottomMargin = Ui.dp(this, 8);
        card.content.addView(icon, iconLp);
        card.heading(phase.kicker, phase.headline, phase.body);
        TextView footer = Panel.text(this, 13, Panel.BLUE, true);
        footer.setText(String.format(Locale.ROOT, "%d sections · %d outils  →", phase.sections.size(), phase.toolCount()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(this, 10);
        card.content.addView(footer, lp);
        Tools.rippleOver(card.content); // keeps the card's blue outline
        Panel.press(card, v -> startActivity(new Intent(this, PhaseActivity.class)
                .putExtra(PhaseActivity.EXTRA_INDEX, index)));
        return card;
    }

    private void buildResults() {
        results.removeAllViews();
        int matches = 0;
        TextView count = Panel.text(this, 13, Panel.TEXT_DIM, true);
        results.addView(count);
        for (Phases.Phase phase : phases) {
            for (CheatSheet.Section s : phase.sections) {
                Panel.Card card = Tools.sectionCard(this, s, query, this::refresh);
                if (card == null) continue;
                card.tab.setText("PHASE " + phase.id + " · " + s.title.toUpperCase(Locale.ROOT));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.dp(this, 10);
                results.addView(card, lp);
                matches += countRows(card);
            }
        }
        count.setText(matches == 0 ? "Aucun outil ne correspond." : matches + " résultat(s)");
    }

    private static int countRows(Panel.Card card) {
        int rows = 0;
        for (int i = 0; i < card.content.getChildCount(); i++) {
            if (card.content.getChildAt(i) instanceof LinearLayout) rows++;
        }
        return rows;
    }

    private void promptUrl() {
        EditText input = new EditText(this);
        input.setHint("exemple.com ou https://…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Ouvrir une URL")
                .setView(input)
                .setPositiveButton("Ouvrir", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) Ui.openInApp(this, CheatSheet.normalizeUrl(url));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Ouvrir une URL");
        menu.getMenu().add(0, 2, 0, "Guide");
        menu.getMenu().add(0, 3, 0, "À propos");
        menu.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == 1) promptUrl();
            else if (mi.getItemId() == 2) startActivity(new Intent(this, GuideActivity.class));
            else showAbout();
            return true;
        });
        menu.show();
    }

    private void showAbout() {
        TextActivity.show(this, "À propos", "VTLOsint — pipeline d'enquête OSINT\n\n"
                + toolsByUrl.size() + " outils dans " + sections.size() + " sections, rangées en "
                + phases.size() + " phases. La liste est dans data/tools.md du projet VTLOsint.\n\n"
                + "• Touchez une phase pour voir ses sections et ses outils.\n"
                + "• WEB : l'outil s'ouvre dans le navigateur intégré.\n"
                + "• >_ TERMINAL : l'outil s'installe et se lance dans Termux.\n"
                + "• Appui long sur un outil : favoris, Wayback Machine, partage…\n\n"
                + "Utilisez ces outils légalement, uniquement sur des cibles que vous êtes autorisé à étudier.\n\n"
                + "Polices : Montserrat et VT323, SIL Open Font License 1.1.\n"
                + "Liste d'origine : https://github.com/Jieyab89/OSINT-Cheat-sheet");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        Termux.onPermissionResult(this, requestCode, results);
    }
}
