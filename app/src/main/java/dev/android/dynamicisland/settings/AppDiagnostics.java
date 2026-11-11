package dev.android.dynamicisland.settings;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import dev.android.dynamicisland.BuildConfig;
import dev.android.dynamicisland.xposed.Diagnostics;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Private, on-device diagnostics for the settings application process.
 *
 * <p>This is deliberately separate from the System UI/Xposed Diagnostics report: the two processes
 * have different lifetimes and storage permissions. No report is uploaded. The user chooses when
 * to copy or export it.</p>
 */
public final class AppDiagnostics {
    private static final String TAG = "CoobvoDiagnostics";
    private static final String DIRECTORY = "diagnostics";
    private static final String LOG_FILE = "debug-log.txt";
    private static final String CRASH_FILE = "last-crash.txt";
    private static final String PENDING_FILE = "crash-pending.flag";
    private static final int MAX_LOG_BYTES = 96 * 1024;
    private static final int MAX_CRASH_BYTES = 48 * 1024;

    private static Context appContext;
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static boolean installed;

    private AppDiagnostics() {}

    /** Installs once, from {@link CoobvoApplication}, before the launcher Activity is started. */
    public static void install(Context context) {
        if (context == null) return;
        synchronized (AppDiagnostics.class) {
            appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            if (!installed) {
                previousHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
                    try {
                        recordCrash(thread, failure);
                    } catch (Throwable loggingFailure) {
                        Log.e(TAG, "Could not persist the uncaught exception", loggingFailure);
                    }
                    Thread.UncaughtExceptionHandler previous = previousHandler;
                    if (previous != null) {
                        previous.uncaughtException(thread, failure);
                    } else {
                        Process.killProcess(Process.myPid());
                        System.exit(10);
                    }
                });
                installed = true;
            }
        }
        event("application started; version=" + BuildConfig.VERSION_NAME
                + "; package=" + appContext.getPackageName());
    }

    /** Adds a bounded breadcrumb for navigation, settings changes or a feature action. */
    public static void event(String message) {
        append("EVENT", message);
    }

    /** Stores a non-fatal exception and its stack trace. */
    public static void error(String site, Throwable failure) {
        if (failure == null) {
            append("ERROR", site + ": unknown error");
            return;
        }
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        append("ERROR", site + ": " + failure.getClass().getName() + ": " + failure.getMessage()
                + "\n" + stack, MAX_CRASH_BYTES);
        Log.e(TAG, site, failure);
    }

    private static void recordCrash(Thread thread, Throwable failure) {
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        StringBuilder report = new StringBuilder();
        report.append("COOBVO SETTINGS APP CRASH\n");
        report.append("time: ").append(timestamp()).append('\n');
        report.append("version: ").append(BuildConfig.VERSION_NAME).append('\n');
        report.append("package: ").append(BuildConfig.APPLICATION_ID).append('\n');
        report.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        report.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("thread: ").append(thread == null ? "unknown" : thread.getName()).append('\n');
        report.append("\n").append(stack);
        synchronized (AppDiagnostics.class) {
            Context context = appContext;
            if (context != null) {
                try {
                    File dir = diagnosticsDir(context);
                    writeBounded(new File(dir, CRASH_FILE), report.toString(), MAX_CRASH_BYTES);
                    new File(dir, PENDING_FILE).createNewFile();
                    appendLocked(dir, "FATAL", "uncaught exception on "
                            + (thread == null ? "unknown" : thread.getName()) + ": "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                } catch (Throwable diskFailure) {
                    Log.e(TAG, "Could not save crash report", diskFailure);
                }
            }
        }
    }

    private static void append(String level, String message) {
        append(level, message, 4 * 1024);
    }

    private static void append(String level, String message, int maxEntryChars) {
        synchronized (AppDiagnostics.class) {
            if (appContext == null) {
                Log.w(TAG, "Diagnostics not initialized: " + safe(message, 500));
                return;
            }
            try {
                appendLocked(diagnosticsDir(appContext), level, safe(message, maxEntryChars));
            } catch (Throwable failure) {
                Log.e(TAG, "Could not append diagnostics", failure);
            }
        }
    }

    private static void appendLocked(File dir, String level, String message) throws Exception {
        File file = new File(dir, LOG_FILE);
        byte[] line = (timestamp() + " [" + level + "] " + message + "\n").getBytes(StandardCharsets.UTF_8);
        trimBeforeAppend(file, line.length);
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(line);
            output.flush();
        }
    }

    private static void trimBeforeAppend(File file, int incomingBytes) throws Exception {
        if (!file.exists() || file.length() + incomingBytes <= MAX_LOG_BYTES) return;
        byte[] old;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) bytes.write(buffer, 0, count);
            old = bytes.toByteArray();
        }
        int start = Math.max(0, old.length - (MAX_LOG_BYTES / 2));
        while (start < old.length && old[start] != '\n') start++;
        if (start < old.length) start++;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(old, start, old.length - start);
        }
    }

    private static File diagnosticsDir(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), DIRECTORY);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new java.io.IOException("Cannot create private diagnostics directory");
        }
        return dir;
    }

    private static void writeBounded(File file, String text, int maximum) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, bytes.length - maximum);
        while (start < bytes.length && start > 0 && bytes[start] != '\n') start++;
        if (start < bytes.length && start > 0) start++;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes, start, bytes.length - start);
            output.flush();
        }
    }

    private static String read(File file) {
        if (file == null || !file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return "[Could not read " + file.getName() + "]";
        }
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\u0000', ' ');
        if (normalized.length() > maximum) return normalized.substring(0, maximum) + "\n...[entry truncated]";
        return normalized;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    /** True after an uncaught settings-app crash, until the recovery screen is acknowledged. */
    public static synchronized boolean hasPendingCrash(Context context) {
        try {
            return new File(diagnosticsDir(context), PENDING_FILE).isFile();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Acknowledges the recovery screen; the saved crash itself remains available in the exported report. */
    public static synchronized void dismissPendingCrash(Context context) {
        try {
            new File(diagnosticsDir(context), PENDING_FILE).delete();
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** Complete local report: settings-app log/crash plus the independent System UI/Xposed report. */
    public static String report(Context context) {
        Context safeContext = context == null ? appContext : context.getApplicationContext();
        StringBuilder out = new StringBuilder();
        out.append("COOBVO DEBUG REPORT\n");
        out.append("generated: ").append(timestamp()).append('\n');
        out.append("app version: ").append(BuildConfig.VERSION_NAME).append('\n');
        out.append("package: ").append(safeContext == null ? BuildConfig.APPLICATION_ID : safeContext.getPackageName()).append('\n');
        out.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            File dir = diagnosticsDir(safeContext == null ? appContext : safeContext);
            String crash = read(new File(dir, CRASH_FILE));
            out.append("\n========== LAST APP CRASH ==========\n");
            out.append(crash.isEmpty() ? "No saved app crash.\n" : crash);
            out.append("\n========== SETTINGS APP DEBUG LOG ==========\n");
            String log = read(new File(dir, LOG_FILE));
            out.append(log.isEmpty() ? "No app events recorded yet.\n" : log);
        } catch (Exception failure) {
            out.append("\n[Local app diagnostics unavailable: ").append(failure.getClass().getSimpleName()).append("]\n");
        }
        out.append("\n========== SYSTEM UI / LSPOSED REPORT ==========\n");
        String systemUi = safeContext == null ? null : Diagnostics.read(safeContext);
        out.append(systemUi == null || systemUi.trim().isEmpty()
                ? "No System UI report available. The module may not have started or Android may not allow the report to be read.\n"
                : systemUi);
        out.append("\n========== END REPORT ==========\n");
        return out.toString();
    }

    /** Lets the user explicitly describe a feature that appears broken even when no exception was thrown. */
    public static void promptForIssue(Context context) {
        final String[] categories = {
                "Settings app / controls",
                "Island not showing or not updating",
                "Expanded card / touch / animation",
                "Prism player / preferences",
                "Prism lyrics",
                "Other"
        };
        final int[] selected = {0};
        final android.widget.EditText details = new android.widget.EditText(context);
        details.setHint("What did you expect, and what happened instead?");
        details.setMinLines(2);
        details.setMaxLines(4);
        details.setSingleLine(false);
        android.widget.LinearLayout content = new android.widget.LinearLayout(context);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(18 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        android.widget.TextView note = new android.widget.TextView(context);
        note.setText("Saved only on this device. Do not include passwords or private messages.");
        content.addView(note);
        content.addView(details);
        new android.app.AlertDialog.Builder(context)
                .setTitle("Report a problem")
                .setSingleChoiceItems(categories, 0, (dialog, which) -> selected[0] = which)
                .setView(content)
                .setPositiveButton("Save to debug report", (dialog, which) -> {
                    String description = details.getText() == null ? "" : details.getText().toString().trim();
                    event("USER_REPORTED_PROBLEM category=" + categories[selected[0]]
                            + (description.isEmpty() ? "" : " details=" + safe(description, 1200)));
                    android.widget.Toast.makeText(context, "Problem added to the local debug report", android.widget.Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
