package io.github.osintcheatsheet;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the repository's Markdown cheat sheets (bundled as assets) into
 * sections of tools, so the app always lists exactly what README.md lists.
 */
final class CheatSheet {

    static final class Tool {
        final String name;
        final String url;
        final String section;
        String description = "";

        Tool(String name, String url, String section) {
            this.name = name;
            this.url = url;
            this.section = section;
        }

        String host() {
            String host = Uri.parse(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        }

        /** Shell script that installs/runs this tool in Termux, or null for web-only tools. */
        String termuxScript() {
            return Termux.scriptFor(this);
        }

        boolean matches(String query) {
            return contains(name, query) || contains(url, query)
                    || contains(description, query) || contains(section, query);
        }
    }

    static final class Section {
        final String title;
        final List<Tool> tools = new ArrayList<>();
        final StringBuilder notes = new StringBuilder();

        Section(String title) {
            this.title = title;
        }

        boolean hasNotes() {
            return notes.length() > 0;
        }
    }

    private static final Pattern HEADING = Pattern.compile("^#\\s+(.+?)\\s*$");
    private static final Pattern LINK_ITEM =
            Pattern.compile("^\\s*[-*]\\s*\\[([^\\]]+)]\\(([^)\\s]+)\\)(.*)$");

    private static List<Section> cached;

    static synchronized List<Section> load(Context context) {
        if (cached == null) {
            List<Section> all = new ArrayList<>();
            all.addAll(parse(readAsset(context, "README.md"), ""));
            all.addAll(parse(readAsset(context, "awesome-article.md"), "Articles · "));
            cached = all;
        }
        return cached;
    }

    /** Unique tools across all sections, keyed by URL (first occurrence wins). */
    static Map<String, Tool> toolsByUrl(List<Section> sections) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Section s : sections) {
            for (Tool t : s.tools) {
                if (!map.containsKey(t.url)) map.put(t.url, t);
            }
        }
        return map;
    }

    static List<Section> parse(String markdown, String titlePrefix) {
        List<Section> out = new ArrayList<>();
        Section current = null;
        Tool lastTool = null;
        boolean previousWasTool = false;
        Set<String> seenInSection = new HashSet<>();

        for (String raw : markdown.split("\\r?\\n")) {
            String line = raw.trim();
            Matcher heading = HEADING.matcher(raw);
            if (heading.matches()) {
                current = new Section(titlePrefix + heading.group(1).trim());
                out.add(current);
                seenInSection.clear();
                previousWasTool = false;
                continue;
            }
            if (current == null || line.startsWith("[![")) continue;
            if (line.isEmpty()) {
                previousWasTool = false;
                continue;
            }

            Matcher link = LINK_ITEM.matcher(raw);
            if (link.matches()) {
                String url = normalizeUrl(link.group(2));
                if (!seenInSection.add(url.toLowerCase(Locale.ROOT))) {
                    previousWasTool = false;
                    continue;
                }
                lastTool = new Tool(link.group(1).trim(), url, current.title);
                lastTool.description = cleanDescription(link.group(3));
                current.tools.add(lastTool);
                previousWasTool = true;
                continue;
            }

            boolean quote = line.startsWith(">");
            String text = quote ? line.substring(1).trim() : line;
            if (text.isEmpty()) continue;
            if (previousWasTool && !quote) {
                // A plain line directly under a list item describes that tool.
                lastTool.description = lastTool.description.isEmpty()
                        ? text : lastTool.description + " " + text;
                continue;
            }
            previousWasTool = false;
            current.notes.append(text).append('\n');
        }

        List<Section> nonEmpty = new ArrayList<>();
        for (Section s : out) {
            if (!s.tools.isEmpty() || s.hasNotes()) nonEmpty.add(s);
        }
        return nonEmpty;
    }

    static String normalizeUrl(String url) {
        url = url.trim();
        if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) url = "https://" + url;
        return url;
    }

    private static String cleanDescription(String trailing) {
        return trailing.trim().replaceFirst("^[-–:]\\s*", "").trim();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
