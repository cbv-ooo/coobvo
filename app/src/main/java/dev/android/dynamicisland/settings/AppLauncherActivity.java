package dev.android.dynamicisland.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Lightweight launcher router. If the previous settings-app session crashed, send the next launch
 * to the diagnostics screen instead of immediately repeating the same failure.
 */
public final class AppLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean crashed = AppDiagnostics.hasPendingCrash(this);
        AppDiagnostics.event("launcher routed to " + (crashed ? "diagnostics recovery" : "settings"));
        startActivity(new Intent(this, crashed ? DiagnosticsActivity.class : SettingsActivity.class));
        finish();
    }
}
