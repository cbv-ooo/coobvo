package dev.android.dynamicisland.settings;

import android.app.Application;

/** Application entry point; diagnostics are installed before any Activity can fail. */
public final class CoobvoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDiagnostics.install(this);
    }
}
