import { MapTile } from 'map/map-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import {
    AlignmentDataHolder,
    AlignmentHeader,
    getSelectedLocationTrackMapAlignmentByTiles,
    getSelectedReferenceLineMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import { blueHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { getPartialPolyLine } from 'utils/math-utils';
import { ReferenceLineId } from 'track-layout/track-layout-model';

const isReferenceLine = (header: AlignmentHeader, referenceLineId: ReferenceLineId) =>
    header.trackNumberId === referenceLineId && header.alignmentType === 'REFERENCE_LINE';

function createFeatures(
    alignments: AlignmentDataHolder[],
    hoveredOverItem: HighlightedAlignment | undefined,
): Feature<LineString>[] {
    return alignments
        .filter(
            (alignment) =>
                hoveredOverItem !== undefined &&
                (hoveredOverItem.type === 'REFERENCE_LINE'
                    ? isReferenceLine(alignment.header, hoveredOverItem.id)
                    : alignment.header.id === hoveredOverItem.id),
        )
        .flatMap(({ points }) => {
            const polyline =
                hoveredOverItem &&
                hoveredOverItem.startM !== undefined &&
                hoveredOverItem.endM !== undefined
                    ? getPartialPolyLine(points, hoveredOverItem?.startM, hoveredOverItem?.endM)
                    : [];
            const lineString = new LineString(polyline);
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
    hoveredOverItem: HighlightedAlignment | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= HIGHLIGHTS_SHOW && hoveredOverItem) {
        inFlight = true;
        hoveredOverItem.type === 'REFERENCE_LINE'
            ? getSelectedReferenceLineMapAlignmentByTiles(
                  changeTimes,
                  mapTiles,
                  publishType,
                  hoveredOverItem.id,
              )
            : getSelectedLocationTrackMapAlignmentByTiles(
                  changeTimes,
                  mapTiles,
                  publishType,
                  hoveredOverItem.id,
              )
                  .then((alignments) => {
                      if (layerId === newestLayerId) {
                          const features = createFeatures(alignments, hoveredOverItem);

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
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'plan-section-highlight-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
