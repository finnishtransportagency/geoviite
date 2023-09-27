import { LineString } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { getLocationTrackMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { Selection } from 'selection/selection-model';

let newestLayerId = 0;

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    selection: Selection,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = true;
    const selectedTrack = selection.selectedItems.locationTracks[0];

    const alignmentPromise =
        resolution <= ALL_ALIGNMENTS || selectedTrack
            ? getLocationTrackMapAlignmentsByTiles(
                  changeTimes,
                  mapTiles,
                  publishType,
                  resolution <= ALL_ALIGNMENTS ? undefined : selectedTrack,
              )
            : Promise.resolve([]);

    alignmentPromise
        .then((locationTracks) => {
            if (layerId === newestLayerId) {
                const features = createAlignmentBackgroundFeatures(locationTracks);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            }
        })
        .catch(() => {
            if (layerId === newestLayerId) clearFeatures(vectorSource);
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'location-track-background-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
