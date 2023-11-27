import { MapTile, OptionalShownItems } from 'map/map-model';
import { LineString, Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { getReferenceLineMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import {
    createAlignmentFeatures,
    findMatchingAlignments,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { ReferenceLineId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

let shownReferenceLinesCompare: string;
let newestLayerId = 0;

export function createReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    isSplitting: boolean,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    function updateShownReferenceLines(referenceLineIds: ReferenceLineId[]) {
        const compare = referenceLineIds.sort().join();

        if (compare !== shownReferenceLinesCompare) {
            shownReferenceLinesCompare = compare;
            onViewContentChanged({ referenceLines: referenceLineIds });
        }
    }

    let inFlight = true;
    getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
        .then((referenceLines) => {
            if (layerId !== newestLayerId) return;

            const features = createAlignmentFeatures(referenceLines, selection, false);

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
            updateShownReferenceLines(referenceLines.map(({ header }) => header.id));
        })
        .catch(() => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
                updateShownReferenceLines([]);
            }
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'reference-line-alignment-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions) => {
            const referenceLines = findMatchingAlignments(hitArea, vectorSource, options);

            const trackNumberIds = deduplicate(
                referenceLines.map((rl) => rl.header.trackNumberId).filter(filterNotEmpty),
            );

            return {
                referenceLines: referenceLines.map((r) => r.header.id),
                trackNumbers: trackNumberIds,
            };
        },
        onRemove: () => updateShownReferenceLines([]),
        requestInFlight: () => inFlight,
    };
}
