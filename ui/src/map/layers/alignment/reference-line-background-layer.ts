import { LineString } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { getReferenceLineMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';

let newestLayerId = 0;

export function createReferenceLineBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    isSplitting: boolean,
    publishType: PublishType,
    changeTimes: ChangeTimes,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    let inFlight = true;
    getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
        .then((referenceLines) => {
            if (layerId === newestLayerId) {
                const features = createAlignmentBackgroundFeatures(referenceLines);

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
        name: 'reference-line-background-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
