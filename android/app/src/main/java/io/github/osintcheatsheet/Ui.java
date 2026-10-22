package io.github.osintcheatsheet;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {

    static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String value, float sp, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
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
