package dev.android.dynamicisland.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small recovery UI kept independent from the larger settings page builders. */
public final class DiagnosticsActivity extends Activity {
    private static final int REQUEST_CREATE_REPORT = 7401;
    private TextView reportView;
    private String pendingExport;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppDiagnostics.event("diagnostics recovery screen opened");
        buildScreen();
    }

    private void buildScreen() {
        int background = themeColor(android.R.attr.colorBackground, 0xFF101114);
        int foreground = themeColor(android.R.attr.textColorPrimary, 0xFFF3F3F3);
        int muted = themeColor(android.R.attr.textColorSecondary, 0xFFB7B7B7);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(AppDiagnostics.hasPendingCrash(this) ? "Coobvo crashed" : "Coobvo diagnostics");
        title.setTextSize(24);
        title.setTextColor(foreground);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView explanation = new TextView(this);
        explanation.setText("A local report was saved. Export it as a .txt file and share it for debugging. Nothing is uploaded automatically.");
        explanation.setTextSize(14);
        explanation.setTextColor(muted);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.topMargin = dp(8);
        explanationParams.bottomMargin = dp(12);
        root.addView(explanation, explanationParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        reportView = new TextView(this);
        reportView.setTextColor(foreground);
        reportView.setTextSize(11);
        reportView.setTypeface(android.graphics.Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(12), dp(12), dp(12), dp(12));
        reportView.setBackgroundColor(themeColor(android.R.attr.colorForeground, 0xFF202126) & 0x22FFFFFF | 0x11000000);
        scroll.addView(reportView, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(button("Export debug report (.txt)", v -> exportReport()));
        root.addView(button("Copy report", v -> copyReport()));
        root.addView(button("Report something not working", v -> AppDiagnostics.promptForIssue(this)));
        root.addView(button("Continue to settings", v -> continueToSettings()));
        setContentView(root);
        refreshReport();
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(4);
        return addButtonMargins(button, params);
    }

    private Button addButtonMargins(Button button, LinearLayout.LayoutParams params) {
        button.setTag(params);
        return button;
    }

    private void refreshReport() {
        if (reportView != null) reportView.setText(AppDiagnostics.report(this));
    }

    private void exportReport() {
        pendingExport = AppDiagnostics.report(this);
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("text/plain");
        create.putExtra(Intent.EXTRA_TITLE, "coobvo-debug-report.txt");
        try {
            startActivityForResult(create, REQUEST_CREATE_REPORT);
        } catch (RuntimeException unavailable) {
            AppDiagnostics.error("diagnostics.export.launch", unavailable);
            Toast.makeText(this, "No file picker is available. Use Copy report instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void copyReport() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Coobvo debug report", AppDiagnostics.report(this)));
        Toast.makeText(this, "Debug report copied", Toast.LENGTH_SHORT).show();
    }

    private void continueToSettings() {
        AppDiagnostics.dismissPendingCrash(this);
        AppDiagnostics.event("user acknowledged previous crash and continued to settings");
        startActivity(new Intent(this, SettingsActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReport();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_REPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new java.io.IOException("The file could not be opened for writing");
            String text = pendingExport == null ? AppDiagnostics.report(this) : pendingExport;
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, "Debug report saved", Toast.LENGTH_LONG).show();
            AppDiagnostics.event("debug report exported");
        } catch (Exception failure) {
            AppDiagnostics.error("diagnostics.export.write", failure);
            Toast.makeText(this, "Could not save the debug report", Toast.LENGTH_LONG).show();
        } finally {
            pendingExport = null;
        }
    }

    private int themeColor(int attribute, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        return getTheme().resolveAttribute(attribute, value, true)
                ? (value.resourceId != 0 ? getResources().getColor(value.resourceId, getTheme()) : value.data)
                : fallback;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
