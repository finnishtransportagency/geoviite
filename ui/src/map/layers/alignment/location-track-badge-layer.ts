import { Point as OlPoint } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentBadgeFeatures,
    getBadgeDrawDistance,
} from 'map/layers/utils/badge-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import { getLocationTrackMapAlignmentsByTiles } from 'track-layout/layout-map-api';

let newestLayerId = 0;

export function createLocationTrackBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= Limits.SHOW_LOCATION_TRACK_BADGES) {
        const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;

        inFlight = true;
        getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            .then((locationTracks) => {
                if (layerId !== newestLayerId) return;

                const features = createAlignmentBadgeFeatures(
                    locationTracks,
                    selection,
                    linkingState,
                    badgeDrawDistance,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-badge-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
