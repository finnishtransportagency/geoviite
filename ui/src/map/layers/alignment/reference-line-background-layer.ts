import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';

let newestLayerId = 0;

export function createReferenceLineBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'REFERENCE_LINES')
        .then((referenceLines) => {
            if (layerId === newestLayerId) {
                const features = createAlignmentBackgroundFeatures(referenceLines);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            }
        })
        .catch(() => clearFeatures(vectorSource));

    return {
        name: 'reference-line-background-layer',
        layer: layer,
    };
}
