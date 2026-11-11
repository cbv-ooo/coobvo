package dev.android.dynamicisland.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.Shadows;

/** Exercises app-side crash breadcrumbs and the launcher recovery route. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AppDiagnosticsTest {
    @Test
    public void reportContainsAppEventsAndNonFatalExceptions() {
        Context context = RuntimeEnvironment.getApplication();
        AppDiagnostics.install(context);
        AppDiagnostics.event("test_feature_toggle=true");
        AppDiagnostics.error("test_settings_failure", new IllegalStateException("test_failure_marker"));

        String report = AppDiagnostics.report(context);
        assertTrue(report.contains("COOBVO DEBUG REPORT"));
        assertTrue(report.contains("test_feature_toggle=true"));
        assertTrue(report.contains("test_settings_failure"));
        assertTrue(report.contains("test_failure_marker"));
        assertTrue(report.contains("SYSTEM UI / LSPOSED REPORT"));
    }

    @Test
    public void settingsPreferenceWritesAreRecorded() throws Exception {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        java.lang.reflect.Field field = SettingsActivity.class.getDeclaredField("prefs");
        field.setAccessible(true);
        SharedPreferences prefs = (SharedPreferences) field.get(activity);
        prefs.edit().putBoolean("diagnostics.test_toggle", true).commit();

        assertTrue(AppDiagnostics.report(activity).contains("preference changed: diagnostics.test_toggle"));
        activity.finish();
    }

    @Test
    public void launcherRoutesNormalStartToSettings() {
        Context context = RuntimeEnvironment.getApplication();
        AppDiagnostics.dismissPendingCrash(context);

        AppLauncherActivity activity = Robolectric.buildActivity(AppLauncherActivity.class).setup().get();
        ShadowActivity shadow = Shadows.shadowOf(activity);
        Intent next = shadow.getNextStartedActivity();

        assertNotNull(next);
        assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void launcherRoutesPendingCrashToRecoveryScreen() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        java.io.File dir = new java.io.File(context.getFilesDir(), "diagnostics");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        java.io.File marker = new java.io.File(dir, "crash-pending.flag");
        assertTrue(marker.createNewFile() || marker.isFile());

        AppLauncherActivity activity = Robolectric.buildActivity(AppLauncherActivity.class).setup().get();
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();

        assertNotNull(next);
        assertEquals(DiagnosticsActivity.class.getName(), next.getComponent().getClassName());
        AppDiagnostics.dismissPendingCrash(context);
    }

    @Test
    public void standaloneDiagnosticsScreenCanBeCreated() {
        DiagnosticsActivity activity = Robolectric.buildActivity(DiagnosticsActivity.class).setup().get();
        assertNotNull(activity.findViewById(android.R.id.content));
        assertTrue(AppDiagnostics.report(activity).contains("COOBVO DEBUG REPORT"));
    }
}
