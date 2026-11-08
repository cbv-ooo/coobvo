# Smart Island feature port status

Last updated: 2026-11-08.

This is an implementation inventory of the supplied Smart Island reference and its adaptation to coobvo's Java/Xposed/Canvas architecture. It is not a claim that the whole Smart Island app has been ported.

## Porting rule

Use the reference app's parsing rules and local feature behaviour, but keep coobvo's original runtime: Samsung System UI hooks, Java event sources, IslandSources/IslandController, OaCardBuilder, Scene/Director/Springs, Canvas painters and the existing Settings/SharedPreferences code.

Do not import Smart Island's separate Kotlin/Compose overlay, notification-listener service, Hilt/DataStore architecture, AccessibilityService overlay, Shizuku integration, separate window system or separate settings UI.

## Implemented ports

| Reference behaviour | Coobvo implementation | Honest state |
|---|---|---|
| Timer parsing | island/source/TimerStopwatchParser.java, connected through OaCardBuilder.java | Implemented; device behaviour not separately confirmed |
| Stopwatch parsing | TimerStopwatchParser.java; native Samsung Chronometer data remains preferred | Implemented; device behaviour not separately confirmed |
| Navigation parsing | island/source/NavigationParser.java, connected through OaCardBuilder.java | Implemented; device behaviour not separately confirmed |
| Per-app ordinary-notification burst cooldown | island/NotificationCooldownManager.java and island/HeadsUpTakeover.java | Implemented; opt-in; targeted JVM tests added to CI |
| Cooldown setting | settings/ModuleSettings.java, SettingsActivity.java and res/values/strings.xml | Implemented; off by default |

## Notification cooldown behaviour

The port follows the reference app's tested defaults:

- Count ordinary notification postings per app.
- Allow the third posting when it arrives within a 30-second detection window; that posting starts a three-minute quiet period.
- During the quiet period, leave Android's own banner/sound behaviour unchanged and do not take later ordinary banners into the island.
- Keep only the latest still-active posting per app.
- After the quiet period, show the latest buffered message only if it remains active and the island can display it.
- De-duplicate the same posting when it is encountered by both the heads-up path and the all-notifications snapshot path.

The setting is opt-in. It affects coobvo's ordinary notification cards only; it does not cancel Android notifications, change notification-channel settings or suppress notification sounds. OTP cards are handled before the limiter. Calls, alarms, media, navigation, ongoing activities, progress/transfer notifications and recognized download/cloud/store notifications are kept out of this limiter.

## Remaining Smart Island candidates

| Candidate | State / next work |
|---|---|
| Rich live-activity parsing | Timer, stopwatch and navigation are integrated. Inspect the reference parser inventory for more card types that map cleanly to Samsung System UI data. |
| Notification history | Not ported. Design a bounded local history store and card/settings without importing the reference notification-listener service. |
| Quick-launch shortcuts | Not ported. Reuse coobvo's existing touch controller and settings model; do not add a second overlay. |
| Rotating idle widgets | Not ported. Use the existing idle/Director state and Canvas renderer. |
| System-event cooldown | Not ported. Separate from ordinary notification cooldown; define lifecycle and exemptions first. |
| Network speed and diagnostics widgets | Not ported. Check data availability, permissions and battery cost before choosing a minimal implementation. |

## Validation

A successful assembleDebug proves source compilation and APK packaging; it does not prove that a parser or card behaves correctly in Samsung System UI. Notification cooldown has focused JVM tests for threshold handling, duplicate postings, latest-buffer replacement and expiry. The latest Debug workflow is configured to run those tests. Real-device verification remains outstanding.