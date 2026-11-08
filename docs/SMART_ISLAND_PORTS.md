# Smart Island feature port status

Last updated: 2026-11-08.

Implementation inventory only. Smart Island is a **behaviour reference**. **Dynamic Island is the foundation** — if DI already handles something pretty well, do not re-port it. Full hierarchy: `docs/SMART_ISLAND_PORT_CONTEXT.md`.

**License:** personal / experimental only. Clean-room behaviour ports preferred.

## Porting rule

1. Keep coobvo runtime (hooks, IslandSources, Director, Canvas, settings).
2. Do **not** import SI Compose, NotificationListenerService stack, Hilt, Shizuku, or SI window system.
3. Prefer extending existing DI sources (`ProgressSource`, `RuleSource`, `EventSource`, `OaSource`, …) over parallel SI clones.

## Phase 0 (soft-fail)

| Piece | State |
|---|---|
| Diagnostics.fact / CrashGuard / guarded gather | Exists |
| DemoSource | Optional **dev** hook only — not a product requirement |

## Hardening

1. No source exception escapes gather. 2) XLog.once. 3) No INTERNET for idle (TrafficStats). 4) Free-form default OFF. 5) Unknown hooks skip. 6) S10e right-anchor → bubbles left.

## Implemented SI gap fills

| Behaviour | Implementation | Notes |
|---|---|---|
| Timer / stopwatch / nav text | TimerStopwatchParser, NavigationParser → OaCardBuilder | Extends DI OA path |
| Ordinary notif burst cooldown | NotificationCooldownManager + HeadsUpTakeover | Opt-in; JVM tests in CI |
| Notification history | NotificationHistoryStore + bridge | Opt-in; local cards only |
| Idle rotation | IdleSource + NetSpeedTracker | Only when always-visible + empty |
| Quick-launch | QuickLaunchSource | Opt-in prefs; **card actions only**; no TouchController changes |

## DI already pretty well (do not duplicate)

| Area | DI owner | SI action |
|---|---|---|
| Progress / transfers / downloads | ProgressSource | **Skip** clone; extend only if gap proven on S10e |
| Staged live cards | RuleSource + rules JSON | **Skip** SI live-activity stack |
| System events (charge, BT, ringer) | EventSource timed popups | **Skip** system-event cooldown unless spam proven |
| Heads-up / messages | PeekSource | Only cooldown/history gates |
| Media, calls, torch, privacy, charging | Dedicated sources | Keep DI |
| Gestures | TouchController | Never repurpose for launcher |

## Remaining candidates (strict)

| Candidate | Decision |
|---|---|
| Settings UI for quick_launch pin list | Optional polish — prefs keys already work |
| History list/detail on device | Owner verify, not more source |
| Dual-bubble touch/spacing | **DI polish**, not SI |
| Battery end-to-end / M3 tokens | **DI polish**, not SI |
| Prism / LyricSync | Parallel track — not SI |
| Weather, SI overlay, gesture editor | **Skip** |

## Validation

assembleDebug / CI ≠ on-device proof. Owner confirms on S10e after LSPosed soft reboot.
