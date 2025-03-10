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
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import {
    getLocationTrackMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import { LayoutContext } from 'common/common-model';

const layerName: MapLayerName = 'location-track-badge-layer';

export function createLocationTrackBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<LocationTrackAlignmentDataHolder[]> =
        resolution <= Limits.SHOW_LOCATION_TRACK_BADGES
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext)
            : Promise.resolve([]);

    const createFeatures = (locationTracks: LocationTrackAlignmentDataHolder[]) => {
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
