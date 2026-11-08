# Smart Island → coobvo — Project Context (start → finish)

**Personal / experimental only.** Not for competition, commercial distribution, or profit.
Repo may stay public so other AIs can read it; that is not product publicity.

**Device:** SM-G970F (beyond0lte) · ExtremeROM Nexus 2.6.1 · One UI 7.0 · Android 15  
**Runtime:** LSPosed module **inside System UI** (highest privilege the module needs). Settings app is the same APK, separate process for UI only.  
**Policy:** Pure Java behaviour ports. No Compose / no SI overlay stack in System UI. Soft-fail every collector. Opaque island only (no blur/frost).

Companion docs: `FUSION_CONTEXT.md` (architecture), `FUSION_PROGRESS.md` (honest status), `SMART_ISLAND_PORTS.md` (inventory).

---

## What “done” means (finish line)

A single LSPosed module on this S10e that:

1. **Keeps Dynamic Island’s strengths** — springs, Canvas painters, System UI hooks, CrashGuard, soft-fail sources.
2. **Absorbs Smart Island’s missing behaviours** as Java sources + settings — not as a second app or Compose overlay.
3. **Fits the S10e right punch-hole** — pill on/near the hole; companion bubbles on the inward side when right-anchored (owner-confirmed).
4. **Does not crash or loop System UI** — every new source is isolated; hooks skip unknown OEM methods.
5. **Stays personal** — clean-room behaviour ports; no commercial distribution claim.

Verification is **owner eyes on the phone** after install/enable in LSPosed and a soft reboot of System UI — not adb scripts, not CI green alone.

---

## Start → finish roadmap

### A. Foundation (mostly in place)
| Item | Intent |
|------|--------|
| DI base on main | Upstream island architecture (sources → controller → Scene/Director → Canvas) |
| CrashGuard + guarded collect | One bad source or hook must not kill System UI |
| S10e placement | AUTO cutout = real top hole; right anchor → bubbles left |
| Opaque look | No blur / frosted / backdrop sampling |
| Debug identity | `dev.android.dynamicisland.coobvo` + stable debug keystore |

### B. Smart Island behaviour ports (core of the merge)
Port **behaviour**, not SI architecture. Prefer System UI data DI already sees (OA, calls, media, privacy, torch) over notification-listener clones.

| Priority | Behaviour | Finish criteria (owner on S10e) |
|----------|-----------|----------------------------------|
| Done (source) | Timer / stopwatch / nav parsers | Real timer or Maps session shows correct card |
| Done (source) | Opt-in notif cooldown | Burst of ordinary notifs throttles island cards only |
| Done (source) | Opt-in notification history | List/clear in settings of cards the island actually showed |
| Next | Idle rotation (clock, battery, alarm, DND, unread, net speed via TrafficStats) | Idle pill cycles useful widgets without network permission |
| Next | Gesture-safe quick-launch entry | Opens pinned apps without stealing existing swipe/long-press |
| Later | Richer live-activity / transfer classification | Clear card kind + progress/ETA where text allows |
| Later | System-event cooldown (separate from notif cooldown) | Defined lifecycle first |
| Skip | Full weather / Compose chrome / SI Accessibility overlay / Shizuku | Out of scope |

### C. Dual bubbles + polish
| Item | Finish criteria |
|------|-----------------|
| Two companion bubbles | Both visible together, sensible gap, independent touch (side already owner-confirmed) |
| Battery end-to-end | All intended states/actions, not only shared gauge geometry |
| M3 Expressive tokens | One coherent opaque system across pill, bubbles, cards, motion |

### D. Prism / LyricSync (parallel track; not SI)
Prism lyrics via Morphe bridge + Canvas strip; LyricSync only for non-Prism if still needed. See `PRISM_LYRICS_MORPHE_BRIDGE.md`.

### E. Stop conditions
- System UI crash-loop → fix or CrashGuard off before more features.
- Feature needs Compose, SI notification-listener service, or INTERNET for “idle” → redesign or skip.
- Owner says skip (e.g. weather, full gesture editor) → document and leave.

---

## Hardening rules (always)

1. Never let a source exception escape `IslandSources.gather`.
2. Prefer `XLog.once` over spam.
3. No INTERNET for idle widgets (TrafficStats only for net speed).
4. Free-form swipe-down default **OFF**.
5. Hooks: ClassNotFound / NoSuchMethod → log once, skip, continue load.
6. S10e: right punch → AUTO real top cutout; right-anchor → bubbles left.
7. Do not replace `IslandView.java` wholesale.

---

## Why not adb “Test Lab” as a requirement

The module already runs as LSPosed inside System UI. Real proof is:

- Enable module → soft reboot System UI → use the phone (timer, maps, notifications, idle).
- Diagnostics / Tools copy report when something fails.

Optional developer hooks (e.g. `DemoSource` broadcast) may exist in tree but are **not** part of the finish line and are not required for SI ports. CI proves compile/package only.

---

## License stance

Smart Island is GPLv3-oriented upstream. coobvo uses **clean-room behaviour ports** into Dynamic Island’s architecture (`IslandItem` + painters + `ModuleSettings`). Personal experimental use only.

---

## Owner-confirmed so far (S10e)

- Cutout alignment works.
- Right-anchored pill → companion bubbles on the left.

Do not treat dual-bubble touch/spacing, history UI, idle widgets, or Prism lyrics as confirmed until you say so on device.

Last updated: 2026-11-08
