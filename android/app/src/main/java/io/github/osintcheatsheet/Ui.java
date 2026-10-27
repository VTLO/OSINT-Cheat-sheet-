package io.github.osintcheatsheet;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {

    static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    /** Pixel-font text; VT323 renders small, so regular text is scaled to match system-font sizes. */
    static TextView text(Context c, String value, float sp, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextColor(Flipper.ORANGE);
        tv.setTypeface(bold ? Flipper.title(c) : Flipper.pixel(c));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, bold ? sp : sp * 1.35f);
        return tv;
    }

    /** Shows the "<" up arrow in the orange title bar; pair with {@link #handleUp}. */
    static void enableUp(Activity a) {
        if (a.getActionBar() != null) a.getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    static boolean handleUp(Activity a, MenuItem item) {
        if (item.getItemId() != android.R.id.home) return false;
        a.finish();
        return true;
    }

    static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }

    static void copy(Context c, String value) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("OSINT", value));
        toast(c, "Copied to clipboard");
    }

    static void share(Context c, String subject, String value) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, value);
        c.startActivity(Intent.createChooser(send, "Share"));
    }

    static void openInApp(Context c, String url) {
        c.startActivity(new Intent(c, BrowserActivity.class).putExtra(BrowserActivity.EXTRA_URL, url));
    }

    static void openExternal(Context c, String url) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            toast(c, "No app can open this link");
        }
    }

    static String waybackUrl(String url) {
        return "https://web.archive.org/web/" + url;
    }

    private Ui() {
    }
}
