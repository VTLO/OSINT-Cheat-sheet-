package io.github.osintcheatsheet;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Short animated guide in French, shown at first launch and from the Guide button. */
public class GuideActivity extends Activity {

    static final String KEY_SEEN = "guide_seen";
    private static final long TICK_MS = 700;

    /** One step of the guide: its text and an animated illustration. */
    private interface Step {
        String title();

        String body();

        View build(Context c);

        /** Advances the illustration's loop; called every TICK_MS. */
        void tick(int n);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Step[] steps;
    private int index;
    private int ticks;

    private TextView stepTab;
    private FrameLayout stage;
    private TextView title;
    private TextView body;
    private LinearLayout dots;
    private Button back;
    private Button next;
    private GestureDetector swipes;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            steps[index].tick(ticks++);
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        List<Phases.Phase> phases = Phases.load(this);
        steps = new Step[]{new Welcome(phases), new PhaseTour(phases), new Sections(), new Options(),
                new Search(), new Builtins(), new Ethics()};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 22);
        root.setPadding(side, Ui.dp(this, 18), side, Ui.dp(this, 18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        stepTab = Panel.tab(this, "");
        top.addView(stepTab);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView skip = Panel.text(this, 14, Panel.TEXT_DIM, true);
        skip.setText("PASSER");
        skip.setLetterSpacing(0.08f);
        int p = Ui.dp(this, 10);
        skip.setPadding(p, p, p, p);
        skip.setOnClickListener(v -> done());
        top.addView(skip);
        root.addView(top);

        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        title = Panel.text(this, 24, Panel.TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        body = Panel.text(this, 15, Panel.TEXT_DIM, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = Ui.dp(this, 10);
        root.addView(body, bodyLp);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < steps.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dots.addView(dot, lp);
        }
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-1, -2);
        dotsLp.topMargin = Ui.dp(this, 18);
        root.addView(dots, dotsLp);

        LinearLayout buttons = new LinearLayout(this);
        back = new Button(this);
        back.setText("Précédent");
        back.setOnClickListener(v -> show(index - 1));
        buttons.addView(back, new LinearLayout.LayoutParams(0, -2, 1f));
        next = new Button(this);
        next.setOnClickListener(v -> {
            if (index == steps.length - 1) done();
            else show(index + 1);
        });
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        nextLp.leftMargin = Ui.dp(this, 8);
        buttons.addView(next, nextLp);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(-1, -2);
        buttonsLp.topMargin = Ui.dp(this, 14);
        root.addView(buttons, buttonsLp);

        swipes = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || Math.abs(vx) < Math.abs(vy) * 1.5f) return false;
                if (vx < 0 && index < steps.length - 1) show(index + 1);
                else if (vx > 0 && index > 0) show(index - 1);
                return true;
            }
        });

        setContentView(root);
        show(0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        swipes.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    private void show(int target) {
        if (target < 0 || target >= steps.length) return;
        int direction = target >= index ? 1 : -1;
        index = target;
        ticks = 0;
        handler.removeCallbacks(loop);

        Step step = steps[index];
        stepTab.setText(String.format(Locale.ROOT, "GUIDE %d / %d", index + 1, steps.length));
        stage.removeAllViews();
        View art = step.build(this);
        FrameLayout.LayoutParams artLp = art.getLayoutParams() instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) art.getLayoutParams() : new FrameLayout.LayoutParams(-2, -2);
        artLp.gravity = Gravity.CENTER;
        stage.addView(art, artLp);
        title.setText(step.title());
        body.setText(step.body());

        float shift = Ui.dp(this, 40) * direction;
        for (View v : new View[]{art, title, body}) {
            v.setAlpha(0f);
            v.setTranslationX(shift);
            v.animate().alpha(1f).translationX(0f).setDuration(320).start();
        }
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackground(Panel.rounded(i == index ? Panel.BLUE : Panel.SURFACE,
                    i == index ? 0 : 1, Panel.BLUE_DIM, 4, this));
        }
        back.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(index == steps.length - 1 ? "C'est parti !" : "Suivant");
        step.tick(ticks++);
        handler.postDelayed(loop, TICK_MS);
    }

    private void done() {
        Tools.prefs(this).edit().putBoolean(KEY_SEEN, true).apply();
        finish();
    }

    @Override
    public void onBackPressed() {
        if (index > 0) show(index - 1);
        else done();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ steps

    private static LinearLayout column(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        return col;
    }

    /** The six phases as a small spine whose nodes light up in turn. */
    private static final class Welcome implements Step {
        private final List<Phases.Phase> phases;
        private final LinearLayout[] rows = new LinearLayout[6];
        private final Panel.SpineView[] nodes = new Panel.SpineView[6];

        Welcome(List<Phases.Phase> phases) {
            this.phases = phases;
        }

        public String title() { return "Bienvenue sur VTLOsint"; }

        public String body() {
            return "Plus de 550 outils OSINT rangés comme une enquête : six phases, "
                    + "de la recherche brute à la vérification.";
        }

        public View build(Context c) {
            LinearLayout col = column(c);
            TextView name = Panel.text(c, 30, Panel.BLUE, true);
            name.setText("VTLOSINT");
            col.addView(name);
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < Math.min(6, phases.size()); i++) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                nodes[i] = new Panel.SpineView(c, i == 0, i == phases.size() - 1);
                nodes[i].setNodeY(17);
                row.addView(nodes[i], new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 34)));
                TextView label = Panel.text(c, 14, Panel.TEXT, true);
                label.setText("PHASE " + phases.get(i).id + " · " + phases.get(i).kicker);
                row.addView(label);
                list.addView(row);
                rows[i] = row;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(c, 12);
            col.addView(list, lp);
            return col;
        }

        public void tick(int n) {
            int lit = n % 8;
            for (int i = 0; i < 6 && rows[i] != null; i++) {
                rows[i].setAlpha(i <= lit ? 1f : 0.3f);
                nodes[i].setPulse(i == lit ? 1.6f : 1f);
            }
        }
    }

    /** A phase card cycling through the six phases. */
    private static final class PhaseTour implements Step {
        private final List<Phases.Phase> phases;
        private FrameLayout holder;

        PhaseTour(List<Phases.Phase> phases) {
            this.phases = phases;
        }

        public String title() { return "Six phases"; }

        public String body() {
            return "Recherche → Identités → Réseaux sociaux → Images & Géo → Infrastructure → Vérification.\n"
                    + "Touchez une phase pour ouvrir ses sections.";
        }

        public View build(Context c) {
            holder = new FrameLayout(c);
            holder.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 300), -2));
            return holder;
        }

        public void tick(int n) {
            if (n % 2 != 0) return;
            Context c = holder.getContext();
            Phases.Phase p = phases.get((n / 2) % phases.size());
            Panel.Card card = new Panel.Card(c, "PHASE " + p.id);
            ImageView icon = new ImageView(c);
            icon.setImageResource(p.iconRes());
            card.content.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
            card.heading(p.kicker, p.headline, null);
            holder.removeAllViews();
            holder.addView(card);
            card.setAlpha(0f);
            card.setTranslationY(Ui.dp(c, 12));
            card.animate().alpha(1f).translationY(0).setDuration(300).start();
        }
    }

    /** A section card whose rows light up: web tools and terminal tools. */
    private static final class Sections implements Step {
        private final View[] rows = new View[3];

        public String title() { return "Sections et outils"; }

        public String body() {
            return "Chaque phase regroupe des sections. WEB s'ouvre dans le navigateur intégré, "
                    + ">_ TERMINAL s'installe et se lance via Termux.";
        }

        public View build(Context c) {
            Panel.Card card = new Panel.Card(c, "IP ADDRESSES");
            String[][] tools = {{"Shodan", "shodan.io", "0"}, {"Sherlock", "github.com", "1"}, {"Wayback Machine", "web.archive.org", "0"}};
            for (int i = 0; i < 3; i++) {
                if (i > 0) card.content.addView(Panel.divider(c));
                rows[i] = Panel.toolRow(c, tools[i][0], tools[i][1], "1".equals(tools[i][2]));
                card.content.addView(rows[i]);
            }
            card.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 300), -2));
            return card;
        }

        public void tick(int n) {
            for (int i = 0; i < 3; i++) {
                rows[i].setBackgroundColor(i == n % 3 ? 0x332F9BEF : 0);
            }
        }
    }

    /** The long-press menu, with its entries highlighted in turn. */
    private static final class Options implements Step {
        private final TextView[] items = new TextView[4];

        public String title() { return "Appui long : options"; }

        public String body() {
            return "Favoris, copie archivée sur la Wayback Machine, navigateur externe, partage… "
                    + "Vos favoris apparaissent en tête du pipeline.";
        }

        public View build(Context c) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(Panel.rounded(Panel.BG, 1.5f, Panel.BLUE, 4, c));
            int p = Ui.dp(c, 16);
            box.setPadding(p, p, p, p);
            TextView head = Panel.text(c, 17, Panel.TEXT, true);
            head.setText("Sherlock");
            box.addView(head);
            String[] labels = {"★  Ajouter aux favoris", "Copie archivée (Wayback Machine)",
                    "Ouvrir dans le navigateur externe", "Partager"};
            for (int i = 0; i < labels.length; i++) {
                items[i] = Panel.text(c, 14, Panel.TEXT, false);
                items[i].setText(labels[i]);
                items[i].setPadding(Ui.dp(c, 6), Ui.dp(c, 9), Ui.dp(c, 6), Ui.dp(c, 9));
                box.addView(items[i]);
            }
            box.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 290), -2));
            return box;
        }

        public void tick(int n) {
            for (int i = 0; i < items.length; i++) {
                items[i].setBackgroundColor(i == n % items.length ? 0x332F9BEF : 0);
                items[i].setTextColor(i == n % items.length ? Panel.BLUE : Panel.TEXT);
            }
        }
    }

    /** The search box typing a query, then a matching result. */
    private static final class Search implements Step {
        private static final String WORD = "sherlock";
        private TextView field;
        private View result;

        public String title() { return "Rechercher"; }

        public String body() {
            return "La barre de recherche filtre tous les outils par nom, site ou description, "
                    + "toutes phases confondues.";
        }

        public View build(Context c) {
            LinearLayout col = column(c);
            field = Panel.text(c, 16, Panel.TEXT, false);
            field.setBackground(Panel.rounded(Panel.SURFACE, 1.5f, Panel.BLUE, 4, c));
            int p = Ui.dp(c, 12);
            field.setPadding(p, p, p, p);
            col.addView(field, new LinearLayout.LayoutParams(Ui.dp(c, 290), -2));
            Panel.Card card = new Panel.Card(c, "PHASE 3 · SOCIAL NETWORKS");
            card.content.addView(Panel.toolRow(c, "Sherlock", "github.com", true));
            result = card;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 290), -2);
            lp.topMargin = Ui.dp(c, 14);
            col.addView(result, lp);
            return col;
        }

        public void tick(int n) {
            int shown = Math.min(n % (WORD.length() + 5), WORD.length());
            field.setText(shown == 0 ? "Rechercher…" : WORD.substring(0, shown) + "▌");
            field.setTextColor(shown == 0 ? Panel.TEXT_DIM : Panel.TEXT);
            result.setAlpha(shown == WORD.length() ? 1f : 0f);
        }
    }

    /** The quick-access buttons bouncing one after the other. */
    private static final class Builtins implements Step {
        private final View[] buttons = new View[4];

        public String title() { return "Outils intégrés"; }

        public String body() {
            return "Générateur de Google Dorks, lecteur et nettoyeur EXIF, configuration de Termux "
                    + "pour lancer Sherlock, Maigret, GHunt… et ce guide.";
        }

        public View build(Context c) {
            LinearLayout row = new LinearLayout(c);
            int[] icons = {R.drawable.ic_globe, R.drawable.ic_camera, R.drawable.ic_terminal, R.drawable.ic_guide};
            String[] labels = {"Dorks", "EXIF", "Termux", "Guide"};
            for (int i = 0; i < 4; i++) {
                buttons[i] = Panel.quickButton(c, icons[i], labels[i], v -> { });
                row.addView(buttons[i], new LinearLayout.LayoutParams(Ui.dp(c, 74), -2));
            }
            return row;
        }

        public void tick(int n) {
            View b = buttons[n % 4];
            b.setTranslationY(0);
            b.animate().translationY(-Ui.dp(b.getContext(), 14)).setDuration(180)
                    .withEndAction(() -> b.animate().translationY(0).setInterpolator(new OvershootInterpolator())
                            .setDuration(260).start())
                    .start();
        }
    }

    /** The highlight quote from the home pipeline. */
    private static final class Ethics implements Step {
        private TextView quote;

        public String title() { return "OSINT éthique"; }

        public String body() {
            return "Utilisez ces outils légalement, uniquement sur des cibles que vous êtes autorisé à étudier. "
                    + "Ce guide reste accessible avec le bouton Guide.";
        }

        public View build(Context c) {
            quote = Panel.quote(c, "La vérification croisée des sources est le cœur de l'OSINT.");
            quote.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 290), -2));
            return quote;
        }

        public void tick(int n) {
            quote.animate().scaleX(n % 2 == 0 ? 1.03f : 1f).scaleY(n % 2 == 0 ? 1.03f : 1f).setDuration(TICK_MS).start();
        }
    }
}
