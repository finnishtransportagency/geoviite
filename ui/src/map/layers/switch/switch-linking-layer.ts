import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { Point as OlPoint } from 'ol/geom';
import OlMap from 'ol/Map';
import { EventsKey } from 'ol/events';
import { unByKey } from 'ol/Observable';
import { debounce } from 'ts-debounce';
import { MapLayerName, MapViewport } from 'map/map-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import {
    LinkingState,
    LinkingType,
    PlacingLayoutSwitch,
    SuggestedSwitch,
} from 'linking/linking-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import {
    getLinkingJointRenderer,
    suggestedSwitchHasMatchOnJoint,
} from 'map/layers/utils/switch-layer-utils';
import { Point } from 'model/geometry';
import { LayoutContext } from 'common/common-model';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { getSuggestedSwitchesForLayoutSwitchPlacing } from 'linking/linking-api';
import {
    blockCells,
    blockKey,
    BlockSuggestions,
    cellIndexInBlock,
    cellToBlock,
    cellToPoint,
    GRID_STEP_PX,
    GridBlock,
    neighborhoodBlocks,
    pointToCell,
    SwitchPlacingBlockKey,
} from 'map/layers/switch/switch-placing-preview';
import { expectCoordinate } from 'utils/type-utils';
import { AsyncCache, asyncCache } from 'cache/cache';

const FETCH_DEBOUNCE_MS = 100;

type PreviewCache = {
    // Viewport the cache is valid for; the whole cache is replaced when this changes (zoom or pan),
    // since the pixel-sized sampling step is tied to the viewport resolution.
    viewport: MapViewport;
    switchId: LayoutSwitchId;
    // Distance between adjacent lattice points in map units (GRID_STEP_PX * resolution).
    step: number;
    // Per-block fetch results; getImmutable dedupes concurrent fetches per key, and a failed fetch
    // drops out of the cache, so the area goes back through the debounced fetch path and retries
    // when next hovered.
    blocks: AsyncCache<SwitchPlacingBlockKey, BlockSuggestions>;
};

function refreshPreviewCache(
    state: PlacingPreviewState,
    viewport: MapViewport,
    linkingState: PlacingLayoutSwitch,
): PreviewCache {
    if (
        state.cache === undefined ||
        state.cache.viewport !== viewport ||
        state.cache.switchId !== linkingState.layoutSwitch.id
    ) {
        state.cache = {
            viewport,
            switchId: linkingState.layoutSwitch.id,
            step: GRID_STEP_PX * viewport.resolution,
            blocks: asyncCache(),
        };
    }
    return state.cache;
}

// Layer inputs read by the pointermove listener and async fetch callbacks; re-bound on every layer
// re-creation so they always act on the current props.
type PlacingPreviewInputs = {
    // Cursor location in map coordinates; written by the pointermove listener and carried across
    // layer re-creations.
    hoveredLocation: Point | undefined;
    linkingState: PlacingLayoutSwitch | undefined;
    layoutContext: LayoutContext;
    viewport: MapViewport;
    onSwitchPlacingPreviewChange: (suggestedSwitch: SuggestedSwitch | undefined) => void;
    onLoadingData: (loading: boolean) => void;
};

type PlacingPreviewState = {
    cache: PreviewCache | undefined;
    previouslySetPreviewSwitch: SuggestedSwitch | undefined;
    latest: PlacingPreviewInputs;
    runFetch: { (): void; cancel: () => void };
};

// ts-debounce rejects the pending call promises with the reason passed to cancel(); this marks
// those rejections as ours so nothing else gets swallowed with them.
const FETCH_CANCELLED = Symbol('switch placing preview fetch cancelled');

function createPlacingPreviewState(latest: PlacingPreviewInputs): PlacingPreviewState {
    const debouncedFetch = debounce(() => startPlacingFetches(state), FETCH_DEBOUNCE_MS);
    const state: PlacingPreviewState = {
        cache: undefined,
        previouslySetPreviewSwitch: undefined,
        latest,
        // cancellations would bubble up as unhandled all the way if not swallowed here
        runFetch: Object.assign(
            () =>
                void debouncedFetch().catch((reason) => {
                    if (reason !== FETCH_CANCELLED) {
                        throw reason;
                    }
                }),
            { cancel: () => debouncedFetch.cancel(FETCH_CANCELLED) },
        ),
    };
    return state;
}

function changePlacingPreviewSwitch(
    state: PlacingPreviewState,
    next: SuggestedSwitch | undefined,
): void {
    if (state.previouslySetPreviewSwitch !== next) {
        state.previouslySetPreviewSwitch = next;
        state.latest.onSwitchPlacingPreviewChange(next);
    }
}

function onBlockResolved(
    state: PlacingPreviewState,
    cache: PreviewCache,
    block: GridBlock,
    suggestions: BlockSuggestions,
): void {
    const cursor = state.latest.hoveredLocation;
    if (state.cache !== cache || cursor === undefined) {
        return;
    }
    const cell = pointToCell(cursor, cache.step);
    const cursorBlock = cellToBlock(cell);
    if (cursorBlock.i === block.i && cursorBlock.j === block.j) {
        changePlacingPreviewSwitch(state, suggestions[cellIndexInBlock(cell)]);
        state.latest.onLoadingData(false);
    }
}

function requestBlock(state: PlacingPreviewState, cache: PreviewCache, block: GridBlock): void {
    cache.blocks
        .getImmutable(blockKey(block), () =>
            getSuggestedSwitchesForLayoutSwitchPlacing(
                state.latest.layoutContext.branch,
                blockCells(block).map((cell) => cellToPoint(cell, cache.step)),
                cache.switchId,
            ),
        )
        .then(
            (suggestions) => onBlockResolved(state, cache, block, suggestions),
            // The cache dropped the failed promise on its own; nothing to do beyond not letting
            // the rejection surface as unhandled.
            () => {},
        );
}

function startPlacingFetches(state: PlacingPreviewState): void {
    const { hoveredLocation } = state.latest;
    if (state.cache === undefined || hoveredLocation === undefined) {
        return;
    }
    const cache = state.cache;
    neighborhoodBlocks(pointToCell(hoveredLocation, cache.step)).forEach((block) =>
        requestBlock(state, cache, block),
    );
}

function stopPreview(state: PlacingPreviewState): void {
    state.cache = undefined;
    state.runFetch.cancel();
    changePlacingPreviewSwitch(state, undefined);
    state.latest.onLoadingData(false);
}

function updatePlacingPreview(state: PlacingPreviewState): void {
    const { linkingState, hoveredLocation, viewport } = state.latest;
    if (linkingState === undefined || hoveredLocation === undefined) {
        // In-flight fetches learn the cache was dropped via the cache identity check.
        stopPreview(state);
        return;
    }
    const cache = refreshPreviewCache(state, viewport, linkingState);

    state.latest.onLoadingData(true);

    const cursorCell = pointToCell(hoveredLocation, cache.step);
    const cursorBlock = cellToBlock(cursorCell);
    // Membership in the cache routes a pointermove on an already-started block to re-awaiting
    // the cached fetch, and one on an unstarted block to the debounced fetch path (so that
    // sweeping across unfetched area doesn't fire a backend request per block crossed).
    if (cache.blocks.has(blockKey(cursorBlock))) {
        requestBlock(state, cache, cursorBlock);
    }
    if (neighborhoodBlocks(cursorCell).some((block) => !cache.blocks.has(blockKey(block)))) {
        state.runFetch();
    }
}

function createSwitchFeatures(suggestedSwitch: SuggestedSwitch): Feature<OlPoint>[] {
    const features: Feature<OlPoint>[] = [];

    suggestedSwitch.joints.forEach((joint) => {
        const f = new Feature({
            geometry: new OlPoint(pointToCoords(joint.location)),
        });

        f.setStyle(
            new Style({
                renderer: getLinkingJointRenderer(
                    joint,
                    suggestedSwitchHasMatchOnJoint(suggestedSwitch, joint.number),
                ),
            }),
        );

        setSuggestedSwitchFeatureProperty(f, suggestedSwitch);
        features.push(f);
    });

    return features;
}

const layerName: MapLayerName = 'switch-linking-layer';

export type SwitchLinkingLayer = MapLayer & {
    layer: GeoviiteMapLayer<OlPoint>;
    pointerMoveListenerKey: EventsKey;
    placingPreview: PlacingPreviewState;
};

export function createSwitchLinkingLayer(
    existingLayer: SwitchLinkingLayer | undefined,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
    viewport: MapViewport,
    olMap: OlMap,
    onSwitchPlacingPreviewChange: (suggestedSwitch: SuggestedSwitch | undefined) => void,
    onLoadingData: (loading: boolean) => void,
): SwitchLinkingLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingLayer?.layer);

    const switchPlacingPreviewInputs: PlacingPreviewInputs = {
        hoveredLocation: existingLayer?.placingPreview.latest.hoveredLocation,
        linkingState:
            linkingState?.type === LinkingType.PlacingLayoutSwitch ? linkingState : undefined,
        layoutContext,
        viewport,
        onSwitchPlacingPreviewChange,
        onLoadingData,
    };
    const placingPreview =
        existingLayer?.placingPreview ?? createPlacingPreviewState(switchPlacingPreviewInputs);
    placingPreview.latest = switchPlacingPreviewInputs;

    // The listener only touches the persistent placingPreview object, so the one registered on
    // first creation stays valid for the layer's whole lifetime.
    const pointerMoveListenerKey =
        existingLayer?.pointerMoveListenerKey ??
        olMap.on('pointermove', ({ coordinate }) => {
            if (placingPreview.latest.linkingState === undefined) {
                return;
            }
            const [x, y] = expectCoordinate(coordinate);
            placingPreview.latest.hoveredLocation = { x, y };
            updatePlacingPreview(placingPreview);
        });

    updatePlacingPreview(placingPreview);

    const suggestedSwitch =
        linkingState?.type === LinkingType.LinkingGeometrySwitch ||
        linkingState?.type === LinkingType.LinkingLayoutSwitch ||
        linkingState?.type === LinkingType.PlacingLayoutSwitch
            ? linkingState.suggestedSwitch
            : undefined;

    const createFeatures = (suggestion: SuggestedSwitch | undefined) =>
        suggestion === undefined ? [] : createSwitchFeatures(suggestion);

    loadLayerData(
        source,
        isLatest,
        onLoadingData,
        Promise.resolve(suggestedSwitch),
        createFeatures,
    );

    return {
        name: layerName,
        layer,
        pointerMoveListenerKey,
        placingPreview,
        onRemove: () => {
            unByKey(pointerMoveListenerKey);
            stopPreview(placingPreview);
        },
    };
}

const SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY = 'suggested-switch-data';

function setSuggestedSwitchFeatureProperty(feature: Feature<OlPoint>, data: SuggestedSwitch) {
    feature.set(SUGGESTED_SWITCH_FEATURE_DATA_PROPERTY, data);
}
