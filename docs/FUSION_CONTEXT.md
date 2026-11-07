# Fusion Context

## Base
Dynamic Island (this repo, Java/Xposed). Always upstream here.

## Goals
- Port missing Smart Island features
- Add compatible Prism Xposed hooks
- Remove all frosted/blur/glass/backdrop
- 2 independent small bubble islands + 1 compact main
- S10e right cutout: compact on right → both small bubbles left
- Lottie + Material 3 Expressive (opaque)
- APK only via ci/release workflows

## Done so far
- issue-triage workflow removed
- Metrics.bubbleSide (+1 right / -1 left) added
- Shapes.bubble now side-aware and indexed (0 = first bubble, 1 = second further out)
- Geometry foundation for dual bubbles + S10e left placement ready

## Still needed
1. Wire bubbleSide from IslandPlacement based on ModuleSettings.pillAnchor() (RIGHT → -1)
2. Extend Scene + Director for secondary2 + second bubble layer
3. Draw / touch the second bubble
4. Strip FrostedSurface + backdrop sampling
5. Port selected Smart Island / Prism features
