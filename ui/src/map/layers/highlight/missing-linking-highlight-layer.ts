import mapStyles from 'map/map.module.scss';
import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import { MapTile } from 'map/map-model';
import {
    getAlignmentSectionsWithoutLinkingByTiles,
    getMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { getMaxTimestamp } from 'utils/date-utils';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { createHighlightFeatures } from 'map/layers/highlight/highlight-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';

const highlightBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedHighlight,
        width: 12,
    }),
});

let newestLayerId = 0;

export function createMissingLinkingHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= HIGHLIGHTS_SHOW) {
        const alignmentPromise = getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'ALL');

        const linkingStatusPromise = getAlignmentSectionsWithoutLinkingByTiles(
            getMaxTimestamp(changeTimes.layoutLocationTrack, changeTimes.layoutReferenceLine),
            publishType,
            'ALL',
            mapTiles,
        );

        Promise.all([alignmentPromise, linkingStatusPromise])
            .then(([alignments, sections]) => {
                if (layerId != newestLayerId) return;

                const features = createHighlightFeatures(
                    alignments,
                    sections,
                    highlightBackgroundStyle,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'missing-linking-highlight-layer',
        layer: layer,
    };
}
