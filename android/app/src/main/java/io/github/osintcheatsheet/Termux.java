package io.github.osintcheatsheet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs command-line OSINT tools through Termux (https://termux.dev), which
 * provides the Linux userland (python, git, go, ...) that Android lacks.
 */
final class Termux {

    static final String PACKAGE = "com.termux";
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final int PERMISSION_REQUEST = 4242;
    static final String SETUP_COMMAND =
            "mkdir -p ~/.termux && echo 'allow-external-apps = true' >> ~/.termux/termux.properties"
                    + " && termux-reload-settings";

    private static final String BIN = "/data/data/com.termux/files/usr/bin/";
    private static final String HOME = "/data/data/com.termux/files/home";

    /** Tools that ship as pip packages: owner/repo -> {package, command to show help}. */
    private static final Map<String, String[]> PIP_PACKAGES = new HashMap<>();
    /** Non-GitHub links that still map to something runnable in Termux. */
    private static final Map<String, String> HOST_SCRIPTS = new HashMap<>();

    static {
        PIP_PACKAGES.put("sherlock-project/sherlock", new String[]{"sherlock-project", "sherlock --help"});
        PIP_PACKAGES.put("soxoj/maigret", new String[]{"maigret", "maigret --help"});
        PIP_PACKAGES.put("mxrch/ghunt", new String[]{"ghunt", "ghunt --help"});
        PIP_PACKAGES.put("megadose/toutatis", new String[]{"toutatis", "toutatis -h"});

        HOST_SCRIPTS.put("exiftool.org", "pkg install -y exiftool && exiftool -ver");
        HOST_SCRIPTS.put("testssl.sh", gitScript("https://github.com/testssl/testssl.sh", "testssl.sh"));
    }

    private static String pendingScript;

    static String scriptFor(CheatSheet.Tool tool) {
        Uri uri = Uri.parse(tool.url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");

        if (host.equals("github.com")) {
            List<String> parts = uri.getPathSegments();
            if (parts.size() < 2) return null;
            String repo = parts.get(1).replaceFirst("\\.git$", "");
            String key = (parts.get(0) + "/" + repo).toLowerCase(Locale.ROOT);
            String[] pip = PIP_PACKAGES.get(key);
            if (pip != null) {
                return wrap("pkg install -y python && pip install --upgrade " + pip[0] + " && " + pip[1]);
            }
            return wrap(gitScript("https://github.com/" + parts.get(0) + "/" + repo, repo));
        }
        if (tool.url.contains("kali.org/tools/metagoofil")) {
            return wrap(gitScript("https://github.com/kurobeats/metagoofil", "metagoofil"));
        }
        String script = HOST_SCRIPTS.get(host);
        return script == null ? null : wrap(script);
    }

    /** Clones a repo and installs its dependencies based on the files it contains. */
    private static String gitScript(String repoUrl, String dir) {
        String d = quote(dir);
        return "command -v git >/dev/null || pkg install -y git\n"
                + "cd ~\n"
                + "if [ -d " + d + " ]; then cd " + d + " && git pull --ff-only; "
                + "else git clone --depth 1 " + quote(repoUrl) + " " + d + " && cd " + d + "; fi\n"
                + "if ls requirements*.txt REQUIREMENTS setup.py pyproject.toml >/dev/null 2>&1; then\n"
                + "  command -v python >/dev/null || pkg install -y python\n"
                + "  for f in requirements.txt REQUIREMENTS; do [ -f \"$f\" ] && pip install -r \"$f\"; done\n"
                + "  if [ -f setup.py ] || [ -f pyproject.toml ]; then pip install .; fi\n"
                + "elif [ -f go.mod ]; then\n"
                + "  pkg install -y golang && go build ./...\n"
                + "elif [ -f package.json ]; then\n"
                + "  pkg install -y nodejs && npm install\n"
                + "elif [ -f Gemfile ]; then\n"
                + "  pkg install -y ruby && gem install bundler && bundle install\n"
                + "fi\n"
                + "echo; echo \"[+] Installed in $(pwd) - see README.md for usage\"; ls";
    }

    /** Keeps the Termux session open in an interactive shell after the script finishes. */
    private static String wrap(String body) {
        return body + "\necho\nexec bash";
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static boolean isInstalled(Activity activity) {
        try {
            activity.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static void run(Activity activity, String script) {
        if (!isInstalled(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle("Termux required")
                    .setMessage("Command-line tools run inside Termux. Install Termux from F-Droid "
                            + "(the Play Store build is outdated), open it once, then come back.\n\n"
                            + "The command has been copied to the clipboard.")
                    .setPositiveButton("Get Termux", (d, w) -> Ui.openExternal(activity,
                            "https://f-droid.org/packages/com.termux/"))
                    .setNegativeButton("Close", null)
                    .show();
            Ui.copy(activity, script);
            return;
        }
        if (activity.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            pendingScript = script;
            activity.requestPermissions(new String[]{PERMISSION}, PERMISSION_REQUEST);
            return;
        }

        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName(PACKAGE, "com.termux.app.RunCommandService");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", BIN + "bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", script});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            activity.startService(intent);
            Intent open = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (open != null) activity.startActivity(open);
            Ui.toast(activity, "Sent to Termux");
        } catch (RuntimeException e) {
            Ui.copy(activity, script);
            showSetupHelp(activity, "Termux refused the command (" + e.getClass().getSimpleName()
                    + "). The command was copied to the clipboard; paste it into Termux, or finish the setup below.");
        }
    }

    /** Call from Activity.onRequestPermissionsResult. */
    static void onPermissionResult(Activity activity, int requestCode, int[] results) {
        if (requestCode != PERMISSION_REQUEST || pendingScript == null) return;
        String script = pendingScript;
        pendingScript = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            run(activity, script);
        } else {
            Ui.copy(activity, script);
            Ui.toast(activity, "Permission denied - command copied, paste it into Termux");
        }
    }

    static void showSetupHelp(Activity activity, String intro) {
        new AlertDialog.Builder(activity)
                .setTitle("Termux setup")
                .setMessage((intro == null ? "" : intro + "\n\n")
                        + "One-time setup so this app can start tools in Termux:\n\n"
                        + "1. Install Termux from F-Droid and open it once.\n"
                        + "2. Run this in Termux:\n\n" + SETUP_COMMAND + "\n\n"
                        + "3. Android 10+: allow Termux \"Display over other apps\".\n"
                        + "4. Grant this app the \"Run commands in Termux\" permission when asked.")
                .setPositiveButton("Copy setup command", (d, w) -> Ui.copy(activity, SETUP_COMMAND))
                .setNegativeButton("Close", null)
                .show();
    }

    private Termux() {
    }
}
