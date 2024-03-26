import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentFeatures,
    findMatchingAlignments,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { compareNamed, LayoutContext } from 'common/common-model';
import { first } from 'utils/array-utils';

let shownLocationTracksCompare = '';

const highlightedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 2,
});

const locationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 0,
});

const layerName: MapLayerName = 'location-track-alignment-layer';

export function createLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
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

    const alignmentPromise =
        resolution <= ALL_ALIGNMENTS
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext)
            : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

        return createAlignmentFeatures(
            locationTracks,
            selection,
            showEndPointTicks,
            locationTrackStyle,
            highlightedLocationTrackStyle,
        );
    };

    const onLoadingChange = (
        loading: boolean,
        locationTracks: AlignmentDataHolder[] | undefined,
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
        searchItems: (hitArea: Rectangle, _options: SearchItemsOptions): LayerItemSearchResult => {
            const alignments = findMatchingAlignments(hitArea, source, {});
            const sortedByName = [...alignments].sort((a, b) => compareNamed(a.header, b.header));
            const selected = first(selection.selectedItems.locationTracks);
            const selectedIndex = selected
                ? sortedByName.findIndex((a) => a.header.id === selected)
                : -1;
            const next =
                selectedIndex < sortedByName.length - 1
                    ? sortedByName[selectedIndex + 1]
                    : first(sortedByName);

            return { locationTracks: next ? [next.header.id] : [] };
        },
        onRemove: () => updateShownLocationTracks([]),
    };
}
