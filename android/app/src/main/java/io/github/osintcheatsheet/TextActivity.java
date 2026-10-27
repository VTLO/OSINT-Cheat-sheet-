package io.github.osintcheatsheet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

/** Displays a section's notes (EXIF commands, dork queries, FQL URLs…) with copy support. */
public class TextActivity extends Activity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_TEXT = "text";

    static void show(Context context, String title, String text) {
        context.startActivity(new Intent(context, TextActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text));
    }

    private String text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        Ui.enableUp(this);
        text = getIntent().getStringExtra(EXTRA_TEXT);

        TextView view = new TextView(this);
        int pad = Ui.dp(this, 16);
        view.setPadding(pad, pad, pad, pad);
        view.setTypeface(Flipper.pixel(this));
        view.setTextSize(20);
        view.setLineSpacing(0, 1.15f);
        view.setTextIsSelectable(true);
        view.setText(text);
        Linkify.addLinks(view, Linkify.WEB_URLS);
        view.setMovementMethod(LinkMovementMethod.getInstance());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        setContentView(scroll);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Copy all");
        menu.add(0, 2, 0, "Share");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (Ui.handleUp(this, item)) return true;
        if (item.getItemId() == 1) {
            Ui.copy(this, text);
            return true;
        }
        if (item.getItemId() == 2) {
            Ui.share(this, getTitle().toString(), text);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
