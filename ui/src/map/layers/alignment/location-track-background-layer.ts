import { LineString } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { Selection } from 'selection/selection-model';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';

const layerName: MapLayerName = 'location-track-background-layer';

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
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

    const selectedTrack = selection.selectedItems.locationTracks[0];

    const alignmentPromise: Promise<AlignmentDataHolder[]> = getMapAlignments(
        changeTimes,
        mapTiles,
        resolution,
        publishType,
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
    publishType: PublishType,
    selectedLocationTrackId: LocationTrackId | undefined,
): Promise<AlignmentDataHolder[]> {
    if (resolution <= ALL_ALIGNMENTS) {
        return getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, publishType);
    } else if (selectedLocationTrackId) {
        return getSelectedLocationTrackMapAlignmentByTiles(
            changeTimes,
            mapTiles,
            publishType,
            selectedLocationTrackId,
        );
    } else {
        return Promise.resolve([]);
    }
}
