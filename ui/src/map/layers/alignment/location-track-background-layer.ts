import { LineString } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { Selection } from 'selection/selection-model';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { first } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'location-track-background-layer';

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    selection: Selection,
    isSplitting: boolean,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const selectedTrack = first(selection.selectedItems.locationTracks);

    const alignmentPromise: Promise<AlignmentDataHolder[]> = getMapAlignments(
        changeTimes,
        mapTiles,
        resolution,
        layoutContext,
        selectedTrack,
    );

    const createFeatures = (locationTracks: AlignmentDataHolder[]) =>
        createAlignmentBackgroundFeatures(locationTracks);

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}

function getMapAlignments(
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    resolution: number,
    layoutContext: LayoutContext,
    selectedLocationTrackId: LocationTrackId | undefined,
): Promise<AlignmentDataHolder[]> {
    if (resolution <= ALL_ALIGNMENTS) {
        return getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext);
    } else if (selectedLocationTrackId) {
        return getSelectedLocationTrackMapAlignmentByTiles(
            changeTimes,
            mapTiles,
            layoutContext,
            selectedLocationTrackId,
        );
    } else {
        return Promise.resolve([]);
    }
}
