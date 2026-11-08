# Smart Island → coobvo — Project Context (start → finish)

**Personal / experimental only.** LSPosed inside System UI on S10e (One UI 7). Pure Java ports. Soft-fail. Opaque island. Owner-on-device verification.

## Finish line

DI springs/hooks/stability + Smart Island **behaviours** as Java sources — no SI Compose overlay, no System UI crash-loops, S10e placement preserved.

## Roadmap status

| Stage | Item | Status |
|-------|------|--------|
| Foundation | CrashGuard, guarded gather, opaque, coobvo identity | In place |
| Foundation | S10e cutout + right-anchor bubbles left | **Owner-confirmed** |
| SI | Timer / stopwatch / nav | Source done |
| SI | Notif cooldown + history | Source done |
| SI | Idle rotation (clock/battery/alarm/DND/TrafficStats) | Source done |
| SI | **Gesture-safe quick-launch** | **Source done 2026-11-08** |
| SI | Richer transfers / system-event cooldown | Later |
| Dual bubbles | Both visible + touch | Partial |
| Skip | Weather, SI overlay/Compose, Shizuku | Out of scope |

### Quick-launch (this step)

- `QuickLaunchSource` — **no TouchController changes**
- Long-press hide, swipe up/down, horizontal drag unchanged
- Prefs: `quick_launch` (bool, default **false**), `quick_launch_apps` (StringSet of packages)
- When enabled + pins set + island has no live content: shows **Apps** card (up to 4 actions)
- Tap content → first pin; expand → action buttons launch apps
- Replaces idle widgets when active; never blocks calls/media/notifs

Settings UI for the pin list can come later; prefs are readable via the module settings file.

## Hardening

1. No source exception escapes gather. 2) XLog.once. 3) No INTERNET for idle. 4) Free-form OFF. 5) Unknown hooks skip. 6) Do not wholesale-replace IslandView.

Last updated: 2026-11-08
