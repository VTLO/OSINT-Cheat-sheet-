package io.github.osintcheatsheet;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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

/** Short animated guide in French, shown at first launch and from the ⋮ menu. */
public class GuideActivity extends Activity {

    static final String KEY_SEEN = "guide_seen";
    private static final long TICK_MS = 650;

    /** One step of the guide: its text and an animated illustration. */
    private interface Step {
        String title();

        String body();

        View build(Context c);

        /** Advances the illustration's loop; called every TICK_MS. */
        void tick(int n);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Step[] steps = {new Welcome(), new Pads(), new Cross(), new Transport(), new Tools(), new Ready()};
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new Panel.CircuitDrawable(this));
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
        stepTab.setText(String.format(java.util.Locale.ROOT, "GUIDE %d / %d", index + 1, steps.length));
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
            dots.getChildAt(i).setBackground(Flipper.rounded(i == index ? Panel.BLUE : Panel.SURFACE,
                    i == index ? 0 : 1, Panel.BLUE_DIM, 4, this));
        }
        back.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(index == steps.length - 1 ? "C'est parti !" : "Suivant");
        handler.postDelayed(loop, 400);
    }

    private void done() {
        getSharedPreferences("osint", MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply();
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

    private static TextView caption(Context c) {
        TextView t = Panel.text(c, 15, Panel.BLUE, true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        t.setLayoutParams(lp);
        return t;
    }

    private static final class Welcome implements Step {
        private TextView lcd;
        private static final String WORD = "VTLOSINT";

        public String title() { return "Bienvenue sur VTLOsint"; }

        public String body() {
            return "Plus de 550 outils OSINT réunis dans une seule application, rangés par sections.";
        }

        public View build(Context c) {
            LinearLayout col = column(c);
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.mipmap.ic_launcher);
            col.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 120), Ui.dp(c, 120)));
            ObjectAnimator pulse = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.08f);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setDuration(900);
            pulse.addUpdateListener(a -> icon.setScaleY((float) a.getAnimatedValue()));
            pulse.start();

            LinearLayout screen = new LinearLayout(c);
            lcd = Flipper.lcdText(c, 34, true);
            lcd.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4));
            screen.addView(lcd);
            View frame = Flipper.lcd(c, screen);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 240), -2);
            lp.topMargin = Ui.dp(c, 24);
            col.addView(frame, lp);
            return col;
        }

        public void tick(int n) {
            int shown = Math.min(n % (WORD.length() + 4), WORD.length());
            lcd.setText(WORD.substring(0, shown) + (n % 2 == 0 ? "_" : " "));
        }
    }

    private static final class Pads implements Step {
        private static final int[] STATES = {Panel.PAD_STATE_WEB, Panel.PAD_STATE_WEB, Panel.PAD_STATE_CLI,
                Panel.PAD_STATE_NOTES, Panel.PAD_STATE_WEB, Panel.PAD_STATE_CLI, Panel.PAD_STATE_WEB,
                Panel.PAD_STATE_EMPTY};
        private static final String[] LABELS = {"Shodan", "Wayback", "Sherlock", "Notes", "TinEye", "Maigret",
                "Pimeyes", ""};
        private final TextView[] pads = new TextView[8];

        public String title() { return "Les pads"; }

        public String body() {
            return "Chaque section affiche ses outils par banque de 8 pads.\n"
                    + "Gris = site web · Vert = outil Termux · Jaune = notes.\n"
                    + "Touchez un pad pour le choisir, retouchez-le pour l’ouvrir.";
        }

        public View build(Context c) {
            LinearLayout grid = new LinearLayout(c);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setBackground(Panel.chassis(c));
            int gap = Ui.dp(c, 6);
            grid.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
            for (int r = 0; r < 2; r++) {
                LinearLayout row = new LinearLayout(c);
                for (int col = 0; col < 4; col++) {
                    TextView pad = Panel.pad(c);
                    pads[r * 4 + col] = pad;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 66), Ui.dp(c, 56));
                    if (col > 0) lp.leftMargin = gap;
                    row.addView(pad, lp);
                }
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-2, -2);
                if (r > 0) rowLp.topMargin = gap;
                grid.addView(row, rowLp);
            }
            tick(0);
            return grid;
        }

        public void tick(int n) {
            int selected = n % 7;
            for (int i = 0; i < pads.length; i++) {
                Panel.stylePad(pads[i], STATES[i], i == selected, LABELS[i]);
            }
            pads[selected].setScaleX(0.9f);
            pads[selected].setScaleY(0.9f);
            pads[selected].animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        }
    }

    private static final class Cross implements Step {
        private static final int[] KEYS = {Flipper.DPad.RIGHT, Flipper.DPad.LEFT, Flipper.DPad.DOWN,
                Flipper.DPad.UP, Flipper.DPad.OK, Flipper.DPad.NONE};
        private static final String[] CAPTIONS = {"▶  Outil suivant", "◀  Outil précédent", "▼  Section suivante",
                "▲  Section précédente", "●  Ouvrir · appui long : options", " "};
        private Flipper.DPad pad;
        private TextView caption;

        public String title() { return "La croix directionnelle"; }

        public String body() {
            return "Naviguez comme sur un Flipper Zero : les flèches parcourent les outils et les sections, "
                    + "le bouton central ouvre l’outil affiché sur l’écran orange.";
        }

        public View build(Context c) {
            LinearLayout col = column(c);
            pad = new Flipper.DPad(c);
            col.addView(pad, new LinearLayout.LayoutParams(Ui.dp(c, 190), Ui.dp(c, 190)));
            caption = caption(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(c, 18);
            col.addView(caption, lp);
            return col;
        }

        public void tick(int n) {
            int i = n % KEYS.length;
            pad.highlight(KEYS[i]);
            caption.setText(CAPTIONS[i]);
        }
    }

    private static final class Transport implements Step {
        private Panel.TransportKey rec;
        private TextView caption;
        private static final String[] CAPTIONS = {"REC  →  ajouter aux favoris", "PLAY  →  ouvrir l’outil",
                "STOP  →  revenir à l’outil précédent", "BANK ◀ ▶  →  changer de banque"};

        public String title() { return "REC · PLAY · STOP"; }

        public String body() {
            return "Les touches de la boîte à rythmes pilotent l’outil sélectionné. "
                    + "Le voyant rouge de REC s’allume quand l’outil est dans vos favoris.";
        }

        public View build(Context c) {
            LinearLayout col = column(c);
            LinearLayout box = new LinearLayout(c);
            box.setBackground(Panel.chassis(c));
            int p = Ui.dp(c, 14);
            box.setPadding(p, p, p, p);
            rec = new Panel.TransportKey(c, "REC", Panel.LED_RED, Panel.REC_RED, v -> { });
            box.addView(rec, new LinearLayout.LayoutParams(Ui.dp(c, 76), -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 76), -2);
            lp.leftMargin = Ui.dp(c, 8);
            box.addView(new Panel.TransportKey(c, "PLAY", Panel.LED_GREEN, Panel.KEY, v -> { }), lp);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(Ui.dp(c, 76), -2);
            lp2.leftMargin = Ui.dp(c, 8);
            box.addView(new Panel.TransportKey(c, "STOP", 0, Panel.KEY, v -> { }), lp2);
            col.addView(box);
            caption = caption(c);
            LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
            capLp.topMargin = Ui.dp(c, 22);
            col.addView(caption, capLp);
            return col;
        }

        public void tick(int n) {
            rec.setLed(n % 2 == 0);
            caption.setText(CAPTIONS[(n / 2) % CAPTIONS.length]);
        }
    }

    private static final class Tools implements Step {
        private final View[] buttons = new View[4];

        public String title() { return "Outils intégrés"; }

        public String body() {
            return "Recherche dans tous les outils, générateur de Google Dorks, lecteur et nettoyeur EXIF, "
                    + "et Termux pour installer et lancer Sherlock, Maigret, GHunt… sur votre téléphone.";
        }

        public View build(Context c) {
            LinearLayout row = new LinearLayout(c);
            int[] icons = {R.drawable.ic_search, R.drawable.ic_globe, R.drawable.ic_camera, R.drawable.ic_terminal};
            String[] labels = {"Search", "Dorks", "EXIF", "Termux"};
            for (int i = 0; i < 4; i++) {
                buttons[i] = Flipper.iconButton(c, icons[i], labels[i], v -> { });
                row.addView(buttons[i], new LinearLayout.LayoutParams(Ui.dp(c, 78), -2));
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

    private static final class Ready implements Step {
        private Panel.SectionCard card;

        public String title() { return "Prêt ?"; }

        public String body() {
            return "Utilisez ces outils légalement, uniquement sur des cibles que vous êtes autorisé à étudier. "
                    + "Ce guide reste disponible dans le menu (les trois points en haut à droite).";
        }

        public View build(Context c) {
            card = new Panel.SectionCard(c);
            card.set("OSINT ÉTHIQUE", "Mission", "Chercher, vérifier, documenter.");
            card.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(c, 280), -2));
            return card;
        }

        public void tick(int n) {
            card.headline.setAlpha(n % 2 == 0 ? 1f : 0.55f);
            card.headline.animate().alpha(n % 2 == 0 ? 0.55f : 1f).setDuration(TICK_MS).start();
        }
    }
}
