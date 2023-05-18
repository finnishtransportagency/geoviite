import { Point } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentBadgeFeatures,
    getBadgeDrawDistance,
} from 'map/layers/alignment/badge-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';

let newestLayerId = 0;

export function createLocationTrackBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Point>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= Limits.SHOW_LOCATION_TRACK_BADGES) {
        const selectedAlignment = selection.selectedItems?.locationTracks[0];
        const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;

        getMapAlignmentsByTiles(
            changeTimes,
            mapTiles,
            publishType,
            'LOCATION_TRACKS',
            selectedAlignment,
        )
            .then((alignments) => {
                if (layerId != newestLayerId) return;

                const features = createAlignmentBadgeFeatures(
                    alignments,
                    selection,
                    linkingState,
                    badgeDrawDistance,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-badge-layer',
        layer: layer,
    };
}
