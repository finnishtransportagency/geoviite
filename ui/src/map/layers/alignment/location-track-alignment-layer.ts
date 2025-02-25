import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import {
    builtAlignmentBackgroundLineStroke,
    builtAlignmentLineDash,
    createAlignmentFeature,
    findMatchingAlignments,
    getAlignmentZIndex,
    isHighlighted,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId, LocationTrackState } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import {
    getLocationTrackMapAlignmentsByTiles,
    LayoutAlignmentDataHolder,
    LocationTrackAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import { LayoutContext } from 'common/common-model';
import { brand } from 'common/brand';
import mapStyles from 'map/map.module.scss';
import { Stroke, Style } from 'ol/style';
import Feature from 'ol/Feature';

let shownLocationTracksCompare = '';

const highlightedExistingOrNotInUseLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: getAlignmentZIndex('IN_USE', 'HIGHLIGHTED'),
});

const highlightedBuiltLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
        ...builtAlignmentLineDash,
    }),
    zIndex: getAlignmentZIndex('BUILT', 'HIGHLIGHTED'),
});

const highlightedBuiltLocationTrackBackgroundStyle = new Style({
    stroke: builtAlignmentBackgroundLineStroke,
    zIndex: getAlignmentZIndex('BUILT_BACKGROUND', 'HIGHLIGHTED'),
});

const locationTrackExistingStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: getAlignmentZIndex('IN_USE', 'NOT_HIGHLIGHTED'),
});

const locationTrackNotInUseStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLineNotInUse,
        width: 1,
    }),
    zIndex: getAlignmentZIndex('NOT_IN_USE', 'NOT_HIGHLIGHTED'),
});

const locationTrackBuiltStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLineBuilt,
        width: 1,
        ...builtAlignmentLineDash,
    }),
    zIndex: getAlignmentZIndex('BUILT', 'NOT_HIGHLIGHTED'),
});
const locationTrackBuiltBackgroundStyle = new Style({
    stroke: builtAlignmentBackgroundLineStroke,
    zIndex: getAlignmentZIndex('BUILT_BACKGROUND', 'NOT_HIGHLIGHTED'),
});

export function getLocationTrackStyles(state: LocationTrackState): Style[] {
    switch (state) {
        case 'NOT_IN_USE':
            return [locationTrackNotInUseStyle];
        case 'BUILT':
            return [locationTrackBuiltStyle, locationTrackBuiltBackgroundStyle];
        default:
            return [locationTrackExistingStyle];
    }
}

export function getLocationTrackHighlightStyles(state: LocationTrackState): Style[] {
    switch (state) {
        case 'BUILT':
            return [
                highlightedBuiltLocationTrackStyle,
                highlightedBuiltLocationTrackBackgroundStyle,
            ];
        default:
            return [highlightedExistingOrNotInUseLocationTrackStyle];
    }
}

export function getLocationTrackEndTickStyle(state: LocationTrackState): Style {
    switch (state) {
        case 'NOT_IN_USE':
            return locationTrackNotInUseStyle;
        case 'BUILT':
            return locationTrackBuiltStyle;
        default:
            return locationTrackExistingStyle;
    }
}

export function getLocationTrackHighlightEndTickStyle(state: LocationTrackState): Style {
    switch (state) {
        case 'BUILT':
            return highlightedBuiltLocationTrackStyle;
        default:
            return highlightedExistingOrNotInUseLocationTrackStyle;
    }
}

export function createLocationTrackFeatures(
    alignments: LayoutAlignmentDataHolder[],
    selection: Selection,
    showEndTicks: boolean,
): Feature<LineString | OlPoint>[] {
    return alignments.flatMap((alignment) => {
        const highlighted = isHighlighted(selection, alignment.header);
        const styles = highlighted
            ? getLocationTrackHighlightStyles(alignment.header.state)
            : getLocationTrackStyles(alignment.header.state);
        const endTickStyle = highlighted
            ? getLocationTrackHighlightEndTickStyle(alignment.header.state)
            : getLocationTrackEndTickStyle(alignment.header.state);

        return createAlignmentFeature(alignment, styles, showEndTicks ? endTickStyle : undefined);
    });
}

const layerName: MapLayerName = 'location-track-alignment-layer';

export function createLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    selection: Selection,
    isSplitting: boolean,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const resolution = olView.getResolution() || 0;

    function updateShownLocationTracks(locationTrackIds: LocationTrackId[]) {
        const compare = locationTrackIds.sort().join();

        if (compare !== shownLocationTracksCompare) {
            shownLocationTracksCompare = compare;
            onViewContentChanged({ locationTracks: locationTrackIds });
        }
    }

    const alignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const createFeatures = (locationTracks: LocationTrackAlignmentDataHolder[]) => {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;
        return createLocationTrackFeatures(locationTracks, selection, showEndPointTicks);
    };

    const onLoadingChange = (
        loading: boolean,
        locationTracks: LocationTrackAlignmentDataHolder[] | undefined,
    ) => {
        if (!loading) {
            updateShownLocationTracks(locationTracks?.map(({ header }) => header.id) ?? []);
        }
        onLoadingData(loading);
    };

    loadLayerData(source, isLatest, onLoadingChange, alignmentPromise, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            locationTracks: findMatchingAlignments(hitArea, source, options).map(({ header }) =>
                brand(header.id),
            ),
        }),
        onRemove: () => updateShownLocationTracks([]),
    };
}
