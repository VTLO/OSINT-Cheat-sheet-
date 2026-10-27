package io.github.osintcheatsheet;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.TreeSet;

/** On-device equivalent of `exiftool file` / `exiftool -all= file` from the cheat sheet. */
public class ExifActivity extends Activity {

    private static final int PICK_IMAGE = 1;
    private static final int SAVE_CLEAN = 2;

    private static final TreeSet<String> TAGS = new TreeSet<>();

    static {
        for (Field f : ExifInterface.class.getFields()) {
            if (f.getName().startsWith("TAG_") && f.getType() == String.class
                    && Modifier.isStatic(f.getModifiers())) {
                try {
                    TAGS.add((String) f.get(null));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private TextView output;
    private Button strip;
    private Button map;
    private Uri image;
    private String imageName = "image";
    private String mimeType = "image/jpeg";
    private File cleaned;
    private double[] latLong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.enableUp(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        Button pick = new Button(this);
        pick.setText("Choose image");
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK_IMAGE));
        root.addView(pick);

        LinearLayout actions = new LinearLayout(this);
        strip = new Button(this);
        strip.setText("Save clean copy");
        strip.setEnabled(false);
        strip.setOnClickListener(v -> stripMetadata());
        actions.addView(strip, new LinearLayout.LayoutParams(0, -2, 1f));
        map = new Button(this);
        map.setText("Show GPS on map");
        map.setEnabled(false);
        map.setOnClickListener(v -> Ui.openInApp(this, String.format(Locale.ROOT,
                "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=16/%f/%f",
                latLong[0], latLong[1], latLong[0], latLong[1])));
        actions.addView(map, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(actions);

        output = new TextView(this);
        output.setTypeface(Flipper.pixel(this));
        output.setTextSize(18);
        output.setTextIsSelectable(true);
        output.setText("Pick a photo to read its EXIF metadata (camera, date, GPS, software…).\n\n"
                + "\"Save clean copy\" removes all EXIF tags, like `exiftool -all= file`.\n\n"
                + "Tip: share photos from the Files app, not the gallery — many apps strip "
                + "location before handing the image over.");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_IMAGE) {
            image = data.getData();
            readMetadata();
        } else if (requestCode == SAVE_CLEAN && cleaned != null) {
            try (InputStream in = new FileInputStream(cleaned);
                 OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                copy(in, out);
                Ui.toast(this, "Clean copy saved");
            } catch (IOException e) {
                Ui.toast(this, "Save failed: " + e.getMessage());
            }
        }
    }

    private void readMetadata() {
        String type = getContentResolver().getType(image);
        mimeType = type == null ? "image/jpeg" : type;
        try (Cursor c = getContentResolver().query(image, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) imageName = c.getString(col);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(imageName).append("\nType: ").append(mimeType).append("\n\n");
        try (InputStream in = getContentResolver().openInputStream(image)) {
            ExifInterface exif = new ExifInterface(in);
            int found = 0;
            for (String tag : TAGS) {
                String value = exif.getAttribute(tag);
                if (value != null && !value.trim().isEmpty()) {
                    sb.append(String.format(Locale.ROOT, "%-28s %s%n", tag, value.trim()));
                    found++;
                }
            }
            float[] ll = new float[2];
            latLong = exif.getLatLong(ll) ? new double[]{ll[0], ll[1]} : null;
            if (latLong != null) {
                sb.insert(0, String.format(Locale.ROOT, "⚠ GPS location: %.6f, %.6f%n%n", latLong[0], latLong[1]));
            }
            if (found == 0) sb.append("No EXIF metadata found.");
            else sb.append('\n').append(found).append(" tags");
        } catch (IOException | RuntimeException e) {
            sb.append("Could not read metadata: ").append(e.getMessage());
        }
        output.setText(sb);
        map.setEnabled(latLong != null);
        strip.setEnabled(true);
    }

    private void stripMetadata() {
        String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
        cleaned = new File(getCacheDir(), "clean" + ext);
        try (InputStream in = getContentResolver().openInputStream(image);
             OutputStream out = new FileOutputStream(cleaned)) {
            copy(in, out);
            ExifInterface exif = new ExifInterface(cleaned.getAbsolutePath());
            for (String tag : TAGS) {
                if (exif.getAttribute(tag) != null) exif.setAttribute(tag, null);
            }
            exif.saveAttributes();
        } catch (IOException | RuntimeException e) {
            Ui.toast(this, "Cannot strip this format: " + e.getMessage());
            return;
        }
        String base = imageName.replaceFirst("\\.[^.]+$", "");
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_TITLE, base + "_clean" + ext), SAVE_CLEAN);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) throw new IOException("stream unavailable");
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        return Ui.handleUp(this, item) || super.onOptionsItemSelected(item);
    }
}
