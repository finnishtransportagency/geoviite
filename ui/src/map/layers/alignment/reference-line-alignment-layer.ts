import { MapTile, OptionalShownItems } from 'map/map-model';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { LineString, Point, Polygon } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { PublishType } from 'common/common-model';
import { LinkingState } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import {
    createAlignmentFeatures,
    getMatchingAlignments,
} from 'map/layers/utils/alignment-layer-utils';
import { ReferenceLineId } from 'track-layout/track-layout-model';

let shownReferenceLinesCompare: string;
let newestLayerId = 0;

export function createReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateShownReferenceLines(referenceLineIds: ReferenceLineId[]) {
        const compare = referenceLineIds.sort().join();

        if (compare !== shownReferenceLinesCompare) {
            shownReferenceLinesCompare = compare;
            onViewContentChanged({ referenceLines: referenceLineIds });
        }
    }

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'REFERENCE_LINES')
        .then((referenceLines) => {
            if (layerId !== newestLayerId) return;

            const features = createAlignmentFeatures(
                referenceLines,
                selection,
                linkingState,
                false,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
            updateShownReferenceLines(referenceLines.map(({ header }) => header.id));
        })
        .catch(() => {
            clearFeatures(vectorSource);
            updateShownReferenceLines([]);
        });

    return {
        name: 'reference-line-alignment-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions) => {
            const referenceLines = getMatchingAlignments(hitArea, vectorSource, options);

            const trackNumberIds = deduplicate(
                referenceLines.map((rl) => rl.header.trackNumberId).filter(filterNotEmpty),
            );

            return {
                referenceLines: referenceLines.map((r) => r.header.id),
                trackNumbers: trackNumberIds,
            };
        },
        onRemove: () => updateShownReferenceLines([]),
    };
}
