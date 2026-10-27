package io.github.osintcheatsheet;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.view.View;

/**
 * Builds search-engine dorks from the operators and query templates in the
 * README (Dorking, Google Queries for Facebook, FQL, LinkedIn, MySpace).
 */
public class DorkActivity extends Activity {

    private static final String TARGET = "{TARGET}";

    /** {label, template}; templates starting with http are opened directly. */
    private static final String[][] TEMPLATES = {
            {"— Pick a template —", ""},
            {"Dork · Text on a site", "intext:\"{TARGET}\" site:example.com"},
            {"Dork · Documents on a site", "site:{TARGET} ext:doc | ext:docx | ext:odt | ext:rtf | ext:sxw | ext:psw | ext:ppt | ext:pptx | ext:pps | ext:csv"},
            {"Dork · Open directory (index of)", "intitle:\"index of\" \"{TARGET}\""},
            {"Dork · All words in title", "allintitle: {TARGET}"},
            {"Dork · All words in URL", "allinurl: {TARGET}"},
            {"Dork · Pages linking to", "link:{TARGET}"},
            {"Dork · Define term", "define:{TARGET}"},
            {"Facebook · Group search", "site:facebook.com inurl:group \"{TARGET}\""},
            {"Facebook · Group wall posts", "site:facebook.com inurl:wall \"{TARGET}\""},
            {"Facebook · Pages", "site:facebook.com inurl:pages \"{TARGET}\""},
            {"Facebook · Public profiles", "allinurl: people \"{TARGET}\" site:facebook.com"},
            {"Facebook · By ID", "id {TARGET} site:facebook.com *"},
            {"FQL · Photos by (ID)", "https://www.facebook.com/search/{TARGET}/photos-by"},
            {"FQL · Photos liked (ID)", "https://www.facebook.com/search/{TARGET}/photos-liked"},
            {"FQL · Photos of (ID)", "https://www.facebook.com/search/{TARGET}/photos-of"},
            {"FQL · Photos commented (ID)", "https://www.facebook.com/search/{TARGET}/photos-commented"},
            {"FQL · Friends (ID)", "https://www.facebook.com/search/{TARGET}/friends"},
            {"FQL · Videos tagged (ID)", "https://www.facebook.com/search/{TARGET}/videos"},
            {"FQL · Videos by (ID)", "https://www.facebook.com/search/{TARGET}/videos-by"},
            {"FQL · Videos liked (ID)", "https://www.facebook.com/search/{TARGET}/videos-liked"},
            {"FQL · Videos commented (ID)", "https://www.facebook.com/search/{TARGET}/videos-commented"},
            {"FQL · Events attended (ID)", "https://www.facebook.com/search/{TARGET}/events-joined"},
            {"FQL · Relatives (ID)", "https://www.facebook.com/search/{TARGET}/relatives"},
            {"LinkedIn · Public profiles", "site:linkedin.com inurl:pub \"{TARGET}\""},
            {"LinkedIn · Updated profiles", "site:linkedin.com inurl:updates \"{TARGET}\""},
            {"LinkedIn · Company profiles", "site:linkedin.com inurl:companies \"{TARGET}\""},
            {"MySpace · Profiles", "site:myspace.com inurl:profile \"{TARGET}\""},
            {"MySpace · Blogs", "site:myspace.com inurl:blogs \"{TARGET}\""},
            {"MySpace · Videos", "site:myspace.com \"{TARGET}\" \"videos\""},
            {"MySpace · Comments", "site:myspace.com \"{TARGET}\" \"comments\""},
            {"MySpace · Friends", "site:myspace.com \"{TARGET}\" \"friends\""},
            {"Telegram · Channels & users", "site:t.me \"{TARGET}\""},
            {"Pastebin · Mentions", "site:pastebin.com \"{TARGET}\""},
    };

    private static final String[][] ENGINES = {
            {"Google", "https://www.google.com/search?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Yandex", "https://yandex.com/search/?text="},
    };

    private EditText target;
    private EditText query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.enableUp(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        form.setPadding(pad, pad, pad, pad);

        form.addView(Ui.text(this, "Target (name, username, domain, Facebook ID…)", 13, true));
        target = field(form, "e.g. johndoe or example.com");

        form.addView(Ui.text(this, "Template", 13, true));
        Spinner templates = new Spinner(this);
        String[] labels = new String[TEMPLATES.length];
        for (int i = 0; i < TEMPLATES.length; i++) labels[i] = TEMPLATES[i][0];
        templates.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        templates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) query.setText(TEMPLATES[position][1].replace(TARGET, targetOr("TARGET")));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        form.addView(templates);

        form.addView(Ui.text(this, "Or combine operators", 13, true));
        EditText site = field(form, "site:  (e.g. github.com)");
        EditText inurl = field(form, "inurl:");
        EditText intitle = field(form, "intitle:");
        EditText intext = field(form, "intext:");
        EditText filetype = field(form, "filetype:  (pdf, docx, xls…)");
        EditText exclude = field(form, "exclude words  (-word)");
        Button build = new Button(this);
        build.setText("Generate query");
        build.setOnClickListener(v -> {
            StringBuilder q = new StringBuilder();
            if (!targetOr("").isEmpty()) q.append('"').append(targetOr("")).append("\" ");
            op(q, "site:", site);
            op(q, "inurl:", inurl);
            op(q, "intitle:", intitle);
            op(q, "intext:", intext);
            op(q, "filetype:", filetype);
            for (String word : exclude.getText().toString().trim().split("\\s+")) {
                if (!word.isEmpty()) q.append('-').append(word).append(' ');
            }
            query.setText(q.toString().trim());
        });
        form.addView(build);

        form.addView(Ui.text(this, "Query", 13, true));
        query = new EditText(this);
        query.setMinLines(3);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(query);

        LinearLayout engines = new LinearLayout(this);
        for (String[] engine : ENGINES) {
            Button b = new Button(this);
            b.setText(engine[0]);
            b.setAllCaps(false);
            b.setOnClickListener(v -> search(engine[1]));
            engines.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        form.addView(engines);

        LinearLayout extra = new LinearLayout(this);
        Button copy = new Button(this);
        copy.setText("Copy query");
        copy.setOnClickListener(v -> Ui.copy(this, query.getText().toString()));
        extra.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        Button operators = new Button(this);
        operators.setText("Operator help");
        operators.setOnClickListener(v -> TextActivity.show(this, "Dorking operators", OPERATOR_HELP));
        extra.addView(operators, new LinearLayout.LayoutParams(0, -2, 1f));
        form.addView(extra);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        setContentView(scroll);
    }

    private EditText field(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private static void op(StringBuilder q, String operator, EditText field) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) return;
        if (value.contains(" ")) value = "\"" + value + "\"";
        q.append(operator).append(value).append(' ');
    }

    private String targetOr(String fallback) {
        String t = target.getText().toString().trim();
        return t.isEmpty() ? fallback : t;
    }

    private void search(String engineUrl) {
        String q = query.getText().toString().trim();
        if (q.isEmpty()) {
            Ui.toast(this, "Generate or type a query first");
        } else if (q.startsWith("http://") || q.startsWith("https://")) {
            Ui.openInApp(this, q);
        } else {
            Ui.openInApp(this, engineUrl + Uri.encode(q));
        }
    }

    private static final String OPERATOR_HELP =
            "intitle:  search for specific titles\n"
                    + "inurl:  search for specific URLs or paths\n"
                    + "intext:  search for specific words or content\n"
                    + "filetype: / ext:  search for files\n"
                    + "site:  search within a specific target\n"
                    + "*  wildcard, e.g. seccodeid*\n"
                    + "define:term  definitions of a term\n"
                    + "cache:  snapshot of an indexed page\n"
                    + "allintext:  all terms in the page text\n"
                    + "allinurl:  all terms in the URL\n"
                    + "allintitle:  all terms in the title\n"
                    + "link:  pages linking to a URL\n"
                    + "|  OR operator, e.g. ext:doc | ext:pdf\n"
                    + "+  require/concatenate words\n"
                    + "-  exclude a word, e.g. security -trails\n\n"
                    + "Examples\n"
                    + "intext:\"hacking\" site:seccodeid.com\n"
                    + "site:www.github.com ext:doc | ext:docx | ext:odt | ext:rtf | ext:csv";

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        return Ui.handleUp(this, item) || super.onOptionsItemSelected(item);
    }
}
