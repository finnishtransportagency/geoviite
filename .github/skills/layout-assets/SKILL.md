---
name: handling-layout-assets
description: Guide for handling layout asset domain objects -- LayoutSwitch, LocationTrack, LayoutTrackNumber, ReferenceLine, OperationalPoint, LayoutKmPost
---

LayoutAsset types are: LayoutSwitch, LocationTrack, LayoutTrackNumber, ReferenceLine, OperationalPoint, LayoutKmPost

When handling LayoutAsset types, always remember the active LayoutContext

- LayoutAsset are changed via a 2-step process, described by LayoutState (enum):
    - All LayoutAsset changes are done as DRAFT
    - Draft changes are made OFFICIAL LayoutAssets through the publication process, including validation
- Alternate states of layout can be described with LayoutBranch:
    - Most typically MAIN, describing the actual implemented track layout
    - Can also be DESIGN (multiple, identified with id), describing an alternate future state (a plan)
- LayoutContext is a view into the whole layout and asking for layout data without it makes no sense, consisting of the
  LayoutBranch and LayoutState
- History state is usually only fetched for OFFICIAL assets with a timestamp
- Current state is fetched with a full LayoutContext, picking the latest object versions
