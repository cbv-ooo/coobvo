# Smart Island feature port status

Last updated: 2026-11-08.

This is an implementation inventory of the supplied Smart Island reference and its adaptation to coobvo's Java/Xposed/Canvas architecture. It is not a claim that the whole Smart Island app has been ported.

**License stance:** personal / experimental only. Owner will not compete, publicize for profit, or distribute commercially. Prefer clean-room behaviour ports over copying GPLv3 source.

## Porting rule

Use the reference app's parsing rules and local feature behaviour, but keep coobvo's original runtime: Samsung System UI hooks, Java event sources, IslandSources/IslandController, OaCardBuilder, Scene/Director/Springs, Canvas painters and the existing Settings/SharedPreferences code.

Do not import Smart Island's separate Kotlin/Compose overlay, notification-listener service, Hilt/DataStore architecture, AccessibilityService overlay, Shizuku integration, separate window system or separate settings UI.

## Phase 0 — Proof system (System UI soft-fail)

| Piece | Role | State |
|---|---|---|
| Diagnostics.fact | Structured facts in LSPosed log | Exists |
| CrashGuard | Disables module after crash-loop | Exists |
| IslandSources.guarded | One source throw ≠ kill gather | Exists |
| **DemoSource** | Synthetic item via broadcast | **Implemented 2026-11-08** |

```bash
adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 1
adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 0
```

Key: `demo:proof`. Soft-fail start + collect only. Personal experimental use.

## Hardening rules (One UI 7 / LSPosed)

1. Never let a source exception escape `IslandSources.gather`.
2. Prefer `XLog.once` over spam.
3. No INTERNET permission for idle widgets (TrafficStats only for net speed).
4. Free-form swipe-down default **OFF**.
5. Hooks: ClassNotFound / NoSuchMethod → log once, skip, continue load.
6. S10e: right punch-hole → AUTO follows real top cutout; right-anchor → bubbles left.

## Implemented ports

| Reference behaviour | Coobvo implementation | Honest state |
|---|---|---|
| Timer parsing | island/source/TimerStopwatchParser.java, connected through OaCardBuilder.java | Implemented; device behaviour not separately confirmed |
| Stopwatch parsing | TimerStopwatchParser.java; native Samsung Chronometer data remains preferred | Implemented; device behaviour not separately confirmed |
| Navigation parsing | island/source/NavigationParser.java, connected through OaCardBuilder.java | Implemented; device behaviour not separately confirmed |
| Per-app ordinary-notification burst cooldown | island/NotificationCooldownManager.java and island/HeadsUpTakeover.java | Implemented; opt-in; targeted JVM tests added to CI |
| Cooldown setting | settings/ModuleSettings.java, SettingsActivity.java and res/values/strings.xml | Implemented; off by default |
| Notification history | settings/NotificationHistoryStore.java, settings/SettingsProvider.java, island/NotificationHistoryBridge.java and SettingsActivity.java | Implemented; opt-in; database and retention tests pass; device verification pending |
| Phase 0 Demo proof | island/source/DemoSource.java wired in IslandSources | Implemented; on-device verify pending |

## Notification cooldown behaviour

The port follows the reference app's tested defaults:

- Count ordinary notification postings per app.
- Allow the third posting when it arrives within a 30-second detection window; that posting starts a three-minute quiet period.
- During the quiet period, leave Android's own banner/sound behaviour unchanged and do not take later ordinary banners into the island.
- Keep only the latest still-active posting per app.
- After the quiet period, show the latest buffered message only if it remains active and the island can display it.
- De-duplicate the same posting when it is encountered by both the heads-up path and the all-notifications snapshot path.

The setting is opt-in. It affects coobvo's ordinary notification cards only; it does not cancel Android notifications, change notification-channel settings or suppress notification sounds. OTP cards are handled before the limiter. Calls, alarms, media, navigation, ongoing activities, progress/transfer notifications and recognized download/cloud/store notifications are kept out of this limiter.

## Notification history behaviour

Notification history is intentionally adapted to the existing System UI takeover path rather than importing Smart Island's separate NotificationListenerService/repository:

- Recording is opt-in and off by default.
- A record is sent only after coobvo successfully shows an ordinary notification card; notifications that never reach the island are not captured.
- System UI sends a minimal text record to the existing SettingsProvider on a worker thread. The provider checks the caller package and accepts history writes only from com.android.systemui. Listing and clearing are restricted to the module app's own UID.
- The module app stores the records in its own SQLite database. It retains at most 500 entries for seven days and de-duplicates the same notification key/post-time pair.
- The settings page can list the latest 100 entries, open an entry's text and clear the history.
- OTP-like notifications, secret-visibility notifications, calls, alarms, transport/navigation notifications and ongoing/foreground-service notifications are excluded. Actions, PendingIntents, images and raw notification objects are never stored.

This is a local history of cards shown by the island, not a replacement for Android's Notification History and not a complete archive of every notification received by the phone.

## Quick-launch interaction constraint

The reference `AppShortcutsSection` uses a pinned-app grid, app selection and recent-app support; a plain tap opens the app, while long-press exposes App Info and optional floating-window/split-screen actions. Coobvo's `TouchController` already assigns the compact iMode long-press to hide the island, horizontal drag to page/cycle/minimize/restore, downward drag to open the shade/cards, and taps to the current island item. Reusing those gestures for a launcher would break existing behaviour.

The port should therefore begin with a dedicated explicit entry point (for example, a separate quick-launch control/page) and a bounded pinned-package list. The initial Java version should open selected apps only. App Info, floating windows and split-screen are separate Android/OEM behaviours and should not be copied until Samsung One UI 7 support is confirmed. No launcher gesture has been repurposed yet.

## Remaining Smart Island candidates

| Candidate | State / next work |
|---|---|
| Rich live-activity parsing | Timer, stopwatch and navigation are integrated. Inspect the reference parser inventory for more card types that map cleanly to Samsung System UI data. |
| Notification history | Implemented as a local, opt-in archive of cards coobvo actually showed. Database tests passed; verify list, detail and clear actions on the S10e. |
| Quick-launch shortcuts | Not ported. Reference behaviour is a pinned app grid; a coobvo entry point must preserve existing gestures before implementation. |
| Rotating idle widgets | Not ported. Use the existing idle/Director state and Canvas renderer. |
| System-event cooldown | Not ported. Separate from ordinary notification cooldown; define lifecycle and exemptions first. |
| Network speed and diagnostics widgets | Not ported. Check data availability, permissions and battery cost before choosing a minimal implementation. |

## Validation

A successful assembleDebug proves source compilation and APK packaging; it does not prove that a parser or card behaves correctly in Samsung System UI. The Debug workflow runs NotificationCooldownManagerTest and NotificationHistoryStoreTest. Run #72 passed both classes and uploaded the version-141 APK. History tests cover deduplication, missing identity, seven-day expiry and clearing. Real-device verification remains outstanding.

On-device Phase 0: soft reboot → CrashGuard not stuck off → DEMO broadcast show/clear.
