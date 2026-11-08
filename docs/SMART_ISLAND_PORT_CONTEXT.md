# Smart Island → coobvo — hierarchy & finish line

**Personal / experimental only.** LSPosed inside System UI · S10e · One UI 7.

---

## Hierarchy (read this first)

**Dynamic Island is the base foundation.** If DI already handles something *pretty well*, do **not** re-implement it from Smart Island.

| Layer | Owns | Rule |
|-------|------|------|
| **1. DI core** | IslandView, Director, Springs, painters, TouchController, placement, CrashGuard, hooks | Never replace. Never wholesale-edit IslandView. |
| **2. DI sources** | Call, Media, OA, Peek, Progress, Rule, Event, Charging, Headset, Hotspot, Unlock, Torch, Privacy, Promoted | Prefer extending these over new parallel sources. |
| **3. SI gap fills** | Only behaviour DI does **not** handle pretty well | Small Java classes on DI paths. Opt-in defaults. Soft-fail. |
| **4. Skip** | SI Compose overlay, NotificationListenerService stack, Shizuku, weather full, gesture UI editor | Out of scope. |

Smart Island is a **behaviour reference**, not a second product to merge feature-for-feature.

---

## What DI already handles pretty well (do not duplicate)

| Area | DI already has | SI port? |
|------|----------------|----------|
| Progress / downloads / transfers | `ProgressSource` + progress prefs | **No** — extend ProgressSource only if a real gap appears on device |
| Staged live cards (delivery, taxi, …) | `RuleSource` + RuleDictionary | **No** — extend rules, don’t clone SI parsers |
| Short system events (charge, BT, ringer) | `EventSource` (timed popups) | **No system-event cooldown port** unless owner proves spam |
| Heads-up → island | `PeekSource` + HeadsUpTakeover | Only cooldown/history *gates*, not a second peek stack |
| Media / calls / OA / torch / privacy | Dedicated sources | Keep DI; SI only refined OA timer/nav text where useful |
| Gestures | TouchController (long-press hide, swipe up/down, horizontal) | **Never** repurpose for launcher |
| Empty pill | Always-visible setting | Idle widgets only *fill* that empty state |
| Springs / dual-bubble geometry / cutout | Director, Shapes, IslandPlacement | Polish DI; don’t import SI motion |

---

## SI gap fills kept (additive, opt-in where possible)

| Port | Why it is a gap | Gate |
|------|-----------------|------|
| Timer / stopwatch / nav parsers | Sharper OA card text on Samsung data | Via existing OaCardBuilder |
| Ordinary notif burst cooldown | DI had no per-app island quiet period | Opt-in, off by default |
| Notification history | Local archive of cards the *island* showed | Opt-in, off by default |
| Idle rotation | Always-visible was empty; no clock/battery/DND/net cycle | Only when always-visible + empty |
| Quick-launch | DI has no pinned-app card | Opt-in prefs; **card actions only** — no gesture steal |

**DemoSource** = optional developer hook, not a product feature. Not required for finish.

---

## Explicitly not next

1. **Richer transfer / live-activity clone of SI** — ProgressSource + RuleSource already cover this *pretty well*.
2. **System-event cooldown** — EventSource already auto-expires; skip unless S10e shows real spam.
3. **More idle chrome / LED clocks / weather** — skip or low priority.
4. **SI gesture map / free-form as primary** — free-form stays default **OFF**; DI gestures stay primary.
5. **Any second notification-listener or overlay window** — forbidden.

---

## Finish line

One LSPosed module: DI stability + animation + S10e placement, plus only the SI gap fills above. Owner confirms on device. CI = compile only.

## Hardening (always)

1. No source exception escapes `gather`.
2. `XLog.once` over spam.
3. No INTERNET for idle (TrafficStats only).
4. Free-form default OFF.
5. Unknown hooks: skip and continue.
6. Do not replace IslandView wholesale.

Last updated: 2026-11-08
