# Smart Island → coobvo — Project Context (start → finish)

**Personal / experimental only.** Not for competition, commercial distribution, or profit.

**Device:** SM-G970F · ExtremeROM Nexus 2.6.1 · One UI 7.0 · Android 15  
**Runtime:** LSPosed inside System UI.  
**Policy:** Pure Java behaviour ports. Soft-fail collectors. Opaque island. Owner-on-device verification (not CI/adb scripts).

---

## Finish line

One LSPosed module that keeps DI springs/hooks/stability and absorbs Smart Island **behaviours** as Java sources — no SI Compose overlay, no System UI crash-loops, S10e right punch-hole placement preserved.

---

## Roadmap status

| Stage | Item | Status |
|-------|------|--------|
| Foundation | CrashGuard, guarded gather, opaque pill, coobvo identity | In place |
| Foundation | S10e cutout + right-anchor bubbles left | **Owner-confirmed** |
| SI ports | Timer / stopwatch / nav | Source done |
| SI ports | Notif cooldown + history | Source done |
| SI ports | **Idle rotation** (clock, battery, alarm, DND, TrafficStats net speed) | **Source done 2026-11-08** |
| SI ports | Gesture-safe quick-launch | Next |
| SI ports | Richer transfers / system-event cooldown | Later |
| Dual bubbles | Both visible + touch | Partial geometry |
| Skip | Weather, SI overlay/Compose, Shizuku | Out of scope |

### Idle rotation (this step)

- `IdleSource` + `NetSpeedTracker`
- Runs only when gather is **empty** and **Always visible island** is on
- Rotates ~6s: clock → battery → next alarm (if set) → DND (if active) → net speed (only above ~50 KB/s)
- Net speed uses **TrafficStats only** (no INTERNET permission)
- Soft-fail start/collect; never blocks live cards

Enable **Always visible island** in settings, clear other island content, soft reboot System UI, watch the pill cycle.

---

## Hardening (always)

1. No source exception escapes `IslandSources.gather`.
2. `XLog.once` over spam.
3. No INTERNET for idle widgets.
4. Free-form swipe-down default OFF.
5. Unknown hooks: skip and continue.
6. Do not replace `IslandView.java` wholesale.

---

## License

Clean-room behaviour ports into DI architecture. Personal experimental use only.

Last updated: 2026-11-08
