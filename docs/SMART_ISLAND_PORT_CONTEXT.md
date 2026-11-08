# Smart Island → coobvo Port Context

**Personal / experimental only.** Not for competition, commercial distribution, or profit.
Owner will not publicize as a competing product. Repo may stay public so other AIs can read it.

**Device:** SM-G970F (beyond0lte) · ExtremeROM Nexus 2.6.1 · One UI 7.0 · Android 15  
**Base:** Dynamic Island LSPosed (Java) · package `dev.android.dynamicisland`  
**Policy:** Pure Java behaviour ports only. No Compose in System UI process. Soft-fail every collector.

Companion docs: `docs/SMART_ISLAND_PORTS.md` (inventory), `docs/FUSION_PROGRESS.md` (honest status), `docs/FUSION_CONTEXT.md` (architecture).

---

## Phase 0 — Proof system (before more feature ports)

Goal: prove a new source can appear, update, and leave **without** risking System UI.

| Piece | Role | Status |
|-------|------|--------|
| Diagnostics.fact | Structured facts in LSPosed log / Tools copy | Exists |
| CrashGuard | Disables module after crash-loop | Exists |
| IslandSources.guarded | One source throw ≠ kill gather | Exists |
| **DemoSource** | Synthetic island item on adb/broadcast | **Done 2026-11-08** |

Demo item key: `demo:proof`  
Broadcast:

```bash
adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 1
adb shell am broadcast -a dev.android.dynamicisland.DEMO --ei show 0
```

Files: `island/source/DemoSource.java`, wired in `IslandSources` (soft-fail `start` + `guarded("demo", …)`).

---

## Hardening rules (One UI 7 / LSPosed)

1. Never let a source exception escape `IslandSources.gather`.
2. Prefer `XLog.once` over spam.
3. No INTERNET permission for idle widgets (TrafficStats only for net speed).
4. Free-form swipe-down default **OFF**.
5. Sticky suppress / exemption must clear items, not leave ghost pills.
6. Hooks: ClassNotFound / NoSuchMethod → log once, skip hook, continue load.
7. S10e: right punch-hole → AUTO cutout follows real top rect; right-anchor → bubbles left (edgeBias / bubbleSide).

---

## Feature board (behaviour only)

See `SMART_ISLAND_PORTS.md` and `FUSION_PROGRESS.md` for the authoritative inventory.

**Implemented (Java, not all owner-verified on S10e):** timer/stopwatch/nav parsers, opt-in notif cooldown, opt-in notification history, Phase 0 DemoSource.

**Owner-confirmed on S10e:** cutout alignment; right-anchor bubbles move left.

**Pending:** idle widget rotation, quick-launch (gesture-safe entry only), richer live-activity types, system-event cooldown, dual-bubble full visual/touch verification.

**Skip / wait:** full weather (network), Compose chrome, Material Capsule full HUD, full S16 gesture UI editor.

---

## License stance

Smart Island is GPLv3-oriented upstream. This project uses **clean-room behaviour ports** into Dynamic Island’s architecture (`IslandItem` + painters + `ModuleSettings`). Personal experimental use only.

---

## On-device checklist (S10e)

- [ ] Soft reboot → module loads, CrashGuard not “off”
- [ ] Demo item appears / clears via broadcast
- [ ] Right-anchor: companion bubbles on left of pill (confirmed once; re-check after large changes)
- [ ] Notification burst: cooldown holds, no System UI ANR
- [ ] Free-form OFF by default; no accidental multi-window
- [ ] History UI list/clear when history setting is on

Last updated: 2026-11-08
