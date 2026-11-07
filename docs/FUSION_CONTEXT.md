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
2026-11-07: issue-triage removed. Write access OK. Ready for code changes.

## References (local uploads)
- SmartIsland-dev.zip
- prism-main.zip
- dynamicisland-main.zip / fusion-seed
- SystemUI.apk
