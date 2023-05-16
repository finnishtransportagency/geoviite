import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/alignment/background-layer-utils';
import { Selection } from 'selection/selection-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';

let newestLayerId = 0;

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= Limits.ALL_ALIGNMENTS) {
        const selectedAlignment = selection.selectedItems?.locationTracks[0];
        getMapAlignmentsByTiles(
            changeTimes,
            mapTiles,
            publishType,
            'LOCATION_TRACKS',
            selectedAlignment,
        )
            .then((locationTracks) => {
                if (layerId != newestLayerId) return;

                const features = createAlignmentBackgroundFeatures(locationTracks);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-background-layer',
        layer: layer,
    };
}
