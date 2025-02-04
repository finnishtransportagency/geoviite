import { LineString } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import Feature from 'ol/Feature';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'location-track-background-layer';

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<LineString>> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    isSplitting: boolean,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const alignmentPromise: Promise<AlignmentDataHolder[]> = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const createFeatures = (locationTracks: AlignmentDataHolder[]) =>
        createAlignmentBackgroundFeatures(locationTracks);

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
