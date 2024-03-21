import { Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentBadgeFeatures,
    getBadgeDrawDistance,
} from 'map/layers/utils/badge-layer-utils';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'location-track-badge-layer';

export function createLocationTrackBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentDataHolder[]> =
        resolution <= Limits.SHOW_LOCATION_TRACK_BADGES
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext)
            : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;
        return createAlignmentBadgeFeatures(
            locationTracks,
            selection,
            linkingState,
            badgeDrawDistance,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
