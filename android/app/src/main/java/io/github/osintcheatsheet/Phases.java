package io.github.osintcheatsheet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Groups the cheat sheet's sections into investigation phases, using the rules in
 * data/phases.json (the PC web app reads the same file).
 */
final class Phases {

    static final class Phase {
        final int id;
        final String icon;
        final String kicker;
        final String headline;
        final String body;
        final List<CheatSheet.Section> sections = new ArrayList<>();

        Phase(JSONObject o) throws JSONException {
            id = o.getInt("id");
            icon = o.getString("icon");
            kicker = o.getString("kicker");
            headline = o.getString("headline");
            body = o.getString("body");
        }

        int toolCount() {
            int n = 0;
            for (CheatSheet.Section s : sections) n += s.tools.size();
            return n;
        }

        int iconRes() {
            switch (icon) {
                case "radar": return R.drawable.ph_radar;
                case "identity": return R.drawable.ph_identity;
                case "network": return R.drawable.ph_network;
                case "media": return R.drawable.ph_media;
                case "stack": return R.drawable.ph_stack;
                default: return R.drawable.ph_funnel;
            }
        }
    }

    private static List<Phase> cached;

    static synchronized List<Phase> load(Context context) {
        if (cached != null) return cached;
        List<Phase> phases = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readAsset(context, "phases.json"));
            JSONArray list = root.getJSONArray("phases");
            for (int i = 0; i < list.length(); i++) phases.add(new Phase(list.getJSONObject(i)));
            JSONArray rules = root.getJSONArray("rules");
            int fallback = root.getInt("default");
            for (CheatSheet.Section s : CheatSheet.load(context)) {
                String title = s.title.toLowerCase(Locale.ROOT);
                int id = fallback;
                for (int i = 0; i < rules.length(); i++) {
                    JSONArray rule = rules.getJSONArray(i);
                    if (title.contains(rule.getString(0))) {
                        id = rule.getInt(1);
                        break;
                    }
                }
                for (Phase p : phases) if (p.id == id) p.sections.add(s);
            }
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("phases.json is missing or invalid", e);
        }
        cached = phases;
        return phases;
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Phases() {
    }
}
