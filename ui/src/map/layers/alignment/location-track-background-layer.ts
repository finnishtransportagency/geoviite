import { LineString } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

let newestLayerId = 0;

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= Limits.ALL_ALIGNMENTS) {
        inFlight = true;
        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'LOCATION_TRACKS')
            .then((locationTracks) => {
                if (layerId === newestLayerId) {
                    const features = createAlignmentBackgroundFeatures(locationTracks);

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                }
            })
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-background-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
