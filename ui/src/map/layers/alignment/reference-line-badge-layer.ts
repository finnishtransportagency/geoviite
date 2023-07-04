import { Point as OlPoint } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
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

let newestLayerId = 0;

export function createReferenceLineBadgeLayer(
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

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'REFERENCE_LINES')
        .then((referenceLines) => {
            if (layerId !== newestLayerId) return;

            const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;
            const features = createAlignmentBadgeFeatures(
                referenceLines,
                selection,
                linkingState,
                badgeDrawDistance,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
        })
        .catch(() => clearFeatures(vectorSource));

    return {
        name: 'reference-line-badge-layer',
        layer: layer,
    };
}
