# Fusion Context

## Base
Dynamic Island (this repo, Java/Xposed). Always upstream here.

## Goals
- Port missing Smart Island features (Kotlin refs in local zips)
- Add compatible Prism Xposed hooks
- Remove all frosted/blur/glass/backdrop (FrostedSurface, ShadeMaterialBackdrop, BlurMaskFilter, AGSL frosted.agsl, stopBackdropSampling paths)
- 2 independent small bubble islands + 1 compact main
- S10e right cutout: when compact pill anchored right, place both small bubbles left of it
- Lottie + Material 3 Expressive (opaque surfaces)
- APK only via existing workflows (ci/release)

## Device
S10e, One UI 7 / Android 15, SystemUI.apk reference local only

## Status
2026-11-07:
- issue-triage removed
- Write access OK
- Context file added
- Architecture already has one secondary bubble (Director / Scene / BubbleContent)
- Plan: extend to secondary1 + secondary2 (list.get(1/2)), duplicate bubble layers, update split spring + layout so both sit opposite the cutout side of the pill
- Placement: when pillAnchor == RIGHT (S10e), force bubble side = LEFT; inverse when LEFT
- Blur removal: replace FrostedSurface with opaque Material 3 surface, delete AGSL gen, remove backdrop sampling

## Next
1. Extend Scene + Director for two bubbles
2. Layout offset for left-of-pill on right-anchor
3. Strip frosted material
4. Port selected Smart Island / Prism features after UI base stable
