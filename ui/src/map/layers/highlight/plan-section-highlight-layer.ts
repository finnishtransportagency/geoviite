import { MapTile } from 'map/map-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import { blueHighlightStyle } from 'map/layers/highlight/highlight-layer-utils';
import { HoveredOverItem } from 'tool-panel/alignment-plan-section-infobox-content';

function createFeatures(
    alignments: AlignmentDataHolder[],
    hoveredOverItem: HoveredOverItem | undefined,
): Feature<LineString>[] {
    return alignments
        .filter(
            (alignment) =>
                hoveredOverItem !== undefined &&
                (hoveredOverItem.type === 'REFERENCE_LINE'
                    ? alignment.header.trackNumberId === hoveredOverItem.id
                    : alignment.header.id === hoveredOverItem.id),
        )
        .flatMap(({ points }) => {
            points = points.filter(
                (point) =>
                    hoveredOverItem?.startM !== undefined &&
                    hoveredOverItem?.endM !== undefined &&
                    point.m >= hoveredOverItem?.startM &&
                    point.m <= hoveredOverItem?.endM,
            );
            const lineString = new LineString(points.map(pointToCoords));
            const feature = new Feature({ geometry: lineString });

            feature.setStyle(blueHighlightStyle);

            return feature;
        });
}

let newestLayerId = 0;

export function createPlanSectionHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    hoveredOverItem: HoveredOverItem | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= HIGHLIGHTS_SHOW) {
        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'ALL')
            .then((alignments) => {
                if (layerId !== newestLayerId) return;

                const features = createFeatures(alignments, hoveredOverItem);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'plan-section-highlight-layer',
        layer: layer,
    };
}
