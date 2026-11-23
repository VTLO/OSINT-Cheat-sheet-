package io.github.osintcheatsheet;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.Locale;

/** One investigation phase: its summary card, then each section's tools along the spine. */
public class PhaseActivity extends Activity {

    static final String EXTRA_INDEX = "phase";

    private Phases.Phase phase;
    private LinearLayout column;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.enableUp(this);
        phase = Phases.load(this).get(getIntent().getIntExtra(EXTRA_INDEX, 0));
        setTitle("Phase " + phase.id + " · " + phase.kicker);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 16);
        column.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 24));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        build();
    }

    private void build() {
        column.removeAllViews();
        Panel.Card summary = new Panel.Card(this, "PHASE " + phase.id);
        ImageView icon = new ImageView(this);
        icon.setImageResource(phase.iconRes());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72));
        iconLp.bottomMargin = Ui.dp(this, 8);
        summary.content.addView(icon, iconLp);
        summary.heading(phase.kicker, phase.headline, phase.body + String.format(Locale.ROOT,
                "\n%d sections · %d outils", phase.sections.size(), phase.toolCount()));
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.bottomMargin = Ui.dp(this, 18);
        column.addView(summary, summaryLp);

        for (int i = 0; i < phase.sections.size(); i++) {
            Panel.Card card = Tools.sectionCard(this, phase.sections.get(i), "", this::build);
            if (card == null) continue;
            column.addView(Panel.spineRow(this, card, i == 0, i == phase.sections.size() - 1, 30));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return Ui.handleUp(this, item) || super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        Termux.onPermissionResult(this, requestCode, results);
    }
}
