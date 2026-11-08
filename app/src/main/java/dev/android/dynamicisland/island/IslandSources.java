package dev.android.dynamicisland.island;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import dev.android.dynamicisland.island.graphics.GlyphIcon;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.source.CallSource;
import dev.android.dynamicisland.island.source.WhatsAppCallClock;
import dev.android.dynamicisland.island.source.CastSource;
import dev.android.dynamicisland.island.source.EventSource;
import dev.android.dynamicisland.island.source.ChargingSource;
import dev.android.dynamicisland.island.source.HeadsetSource;
import dev.android.dynamicisland.island.source.HotspotSource;
import dev.android.dynamicisland.island.source.UnlockSource;
import dev.android.dynamicisland.island.source.MediaSource;
import dev.android.dynamicisland.island.source.OaSource;
import dev.android.dynamicisland.island.source.PeekSource;
import dev.android.dynamicisland.island.source.PrivacySource;
import dev.android.dynamicisland.island.source.PromotedSource;
import dev.android.dynamicisland.island.source.ProgressSource;
import dev.android.dynamicisland.island.source.RecorderNotificationSource;
import dev.android.dynamicisland.island.source.RuleSource;
import dev.android.dynamicisland.island.source.TorchSource;
import dev.android.dynamicisland.island.source.DemoSource;
import dev.android.dynamicisland.island.source.IdleSource;
import dev.android.dynamicisland.island.source.QuickLaunchSource;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.xposed.XLog;
import dev.android.dynamicisland.xposed.hooks.HideDuplicated;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The set of data sources behind the island and the step that gathers their items into one list.
 * Sources are isolated from each other: one that throws only loses its own islands.
 */
final class IslandSources {
    static final class Gathered {
        final List<IslandItem> items = new ArrayList<>();
        IslandItem glowSession;
        final Set<String> hiddenByForeground = new HashSet<>();
        final Set<String> packages = new LinkedHashSet<>();
    }

    private static final long PREVIEW_RECHECK_MS = 2500;
    private final Context ctx;
    private final Handler main;
    private final Runnable onChange;
    final OaSource oa;
    private final ForegroundApp foregroundApp;
    final MediaSource media;
    final CastSource cast;
    final EventSource events;
    final ChargingSource charging;
    final HeadsetSource headset;
    final HotspotSource hotspot;
    final UnlockSource unlock;
    final TorchSource torch;
    final PrivacySource privacy;
    final ProgressSource progress;
    final PromotedSource promoted;
    final RecorderNotificationSource recorder;
    final RuleSource rules;
    final CallSource calls;
    final PeekSource peeks;
    final DemoSource demo;
    final IdleSource idle;
    final QuickLaunchSource quickLaunch;

    IslandSources(Context ctx, ClassLoader classLoader, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.main = main;
        this.onChange = onChange;
        WhatsAppCallClock callClock = new WhatsAppCallClock();
        oa = new OaSource(ctx, classLoader, callClock);
        foregroundApp = new ForegroundApp(ctx, oa::foregroundPackage);
        media = new MediaSource(ctx, main, onChange);
        media.setRecordingFilter(oa::isVoiceRecording);
        cast = new CastSource(ctx);
        events = new EventSource(ctx, main, onChange);
        charging = new ChargingSource(ctx, main, onChange);
        headset = new HeadsetSource(ctx, main, onChange);
        hotspot = new HotspotSource(ctx, main, onChange);
        unlock = new UnlockSource(ctx, main, onChange);
        torch = new TorchSource(ctx, main, onChange);
        privacy = new PrivacySource(ctx, main, onChange);
        progress = new ProgressSource(ctx, main, onChange);
        promoted = new PromotedSource(ctx, main, onChange);
        recorder = new RecorderNotificationSource(ctx, onChange);
        recorder.setLifecycleSession(cast::recorderLifecycle);
        recorder.setOnShownChanged(() -> main.post(HideDuplicated::rebuild));
        promoted.setOnShownChanged(() -> main.post(HideDuplicated::rebuild));
        rules = new RuleSource(ctx, main, onChange);
        progress.setOnShownChanged(() -> main.post(HideDuplicated::rebuild));
        rules.setOnShownChanged(() -> main.post(HideDuplicated::rebuild));
        cast.onRecordersChanged = () -> main.post(HideDuplicated::rebuild);
        torch.onShownChanged = () -> main.post(HideDuplicated::rebuild);
        calls = new CallSource(ctx, main, onChange, callClock);
        peeks = new PeekSource(ctx, main, onChange);
        demo = new DemoSource(ctx, main, onChange);
        idle = new IdleSource(ctx, main, onChange);
        quickLaunch = new QuickLaunchSource(ctx, main, onChange);
    }

    void start(Runnable onChange) {
        media.start();
        syncOptionalSources();
        try { demo.start(); } catch (Throwable ignored) {}
        try { idle.start(); } catch (Throwable ignored) {}
        try { quickLaunch.start(); } catch (Throwable ignored) {}
        try {
            events.start();
            torch.start();
            privacy.start();
        } catch (Throwable t) {
            XLog.e("event islands failed to start", t);
        }
        oa.onChanged = onChange;
    }

    void syncOptionalSources() {
        guarded("charging settings", charging::syncSettings);
        guarded("headset settings", headset::syncSettings);
        guarded("hotspot settings", hotspot::syncSettings);
    }

    String foregroundPackage() {
        return oa.foregroundPackage();
    }

    Gathered gather() {
        Gathered out = new Gathered();
        List<IslandItem> items = out.items;
        ForegroundApp.State state = foregroundApp.read();
        final String foreground = state.packageName;
        try {
            oa.collect(items, out.hiddenByForeground, out.packages, foreground, state.confirmed);
        } catch (Throwable t) {
            XLog.once("collect live notifications", t);
        }
        if (!ModuleSettings.progressNotifications()) {
            items.removeIf(item -> item.transfer);
        }
        guarded("media", () -> media.collect(items, foreground, state.confirmed, out.hiddenByForeground, out.packages));
        if (ModuleSettings.ambientGlow()) guarded("music glow", () -> out.glowSession = media.glowSession());
        guarded("cast", () -> cast.collect(items));
        guarded("recorder notification", () -> recorder.collect(items));
        guarded("events", () -> events.collect(items));
        guarded("charging info", () -> charging.collect(items));
        guarded("headset", () -> headset.collect(items));
        guarded("hotspot", () -> hotspot.collect(items));
        guarded("unlock notice", () -> unlock.collect(items));
        if (charging.isOffering() && ModuleSettings.stockChargingAnimation()) {
            items.removeIf(item -> "event:charging".equals(item.key));
        }
        guarded("torch", () -> torch.collect(items));
        if (ModuleSettings.placementPreviewActive()) {
            items.add(placementSample());
            main.removeCallbacks(onChange);
            main.postDelayed(onChange, PREVIEW_RECHECK_MS);
        }
        guarded("privacy", () -> privacy.collect(items, foreground));
        guarded("live notifications", () -> promoted.collect(items, foreground));
        guarded("progress", () -> progress.collect(items, foreground));
        guarded("rules", () -> rules.collect(items, foreground));
        guarded("calls", () -> calls.collect(items, foreground));
        guarded("peeks", () -> peeks.collect(items));
        guarded("demo", () -> demo.collect(items));
        guarded("idle", () -> idle.collectIfIdle(items));
        guarded("quick launch", () -> quickLaunch.collect(items));
        if (torch.replacesStockNotification()) {
            items.removeIf(TorchSource::isStockItem);
        }
        if (ModuleSettings.accentSystem()) ModuleSettings.refreshSystemAccent(ctx);
        int accent = ModuleSettings.accentOverride();
        for (IslandItem item : items) {
            item.customAccent = accent != 0 && !item.isMedia
                    ? dev.android.dynamicisland.island.graphics.Colors.readableOnBlack(accent) : 0;
        }
        SwitchGate.apply(items);
        ItemDedupe.apply(items, HideDuplicated::copyKeysOf);
        return out;
    }

    private IslandItem placementSample() {
        IslandItem it = new IslandItem("preview:position");
        it.kind = IslandItem.Kind.EVENT;
        it.iconIsImage = false;
        it.title = IslandText.get(ctx, "island_island_position");
        it.subtitle = IslandText.get(ctx, "island_move_it_with_the_sliders");
        it.icon = GlyphIcon.of(GlyphIcon.Kind.HOTSPOT);
        it.accent = 0xFF3E91FF;
        it.trailing = IslandItem.Trailing.TEXT;
        it.trailingText = ModuleSettings.previewSampleLong()
                ? IslandText.get(ctx, "island_preview_a_long_text_stretches_the_pill_this_far")
                : IslandText.get(ctx, "island_preview");
        it.postTime = SystemClock.uptimeMillis();
        return it;
    }

    private static void guarded(String source, Runnable collect) {
        try {
            collect.run();
        } catch (Throwable t) {
            XLog.once("collect " + source, t);
        }
    }
}
