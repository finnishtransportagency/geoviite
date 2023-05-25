import { MapTile, OptionalShownItems } from 'map/map-model';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { LineString, Point, Polygon } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { PublishType } from 'common/common-model';
import { LinkingState } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import OlView from 'ol/View';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    clearFeatures,
    getMatchingAlignmentData,
    MatchOptions,
} from 'map/layers/utils/layer-utils';
import { deduplicate, filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { fromExtent } from 'ol/geom/Polygon';
import { createAlignmentFeatures } from 'map/layers/alignment/alignment-layer-utils';

let compareString = '';
let newestLayerId = 0;

export function createReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const shownItemsSearchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
        const matchOptions: MatchOptions = {
            strategy: options.limit == 1 ? 'nearest' : 'limit',
            limit: undefined,
        };

        const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
        const referenceLines = getMatchingAlignmentData(hitArea, features, matchOptions)
            .map(({ header }) => header)
            .filter(filterUniqueById((a) => a.id))
            .slice(0, options.limit);

        const trackNumberIds = deduplicate(
            referenceLines.map((rl) => rl.trackNumberId).filter(filterNotEmpty),
        );

        return {
            referenceLines: referenceLines.map((r) => r.id),
            trackNumbers: trackNumberIds,
        };
    };

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

            if (onViewContentChanged) {
                const compare = referenceLines
                    .map(({ header }) => header.id)
                    .sort()
                    .join();

                if (compare !== compareString) {
                    compareString = compare;
                    const area = fromExtent(olView.calculateExtent());
                    const result = shownItemsSearchFunction(area, {});
                    onViewContentChanged(result);
                }
            }
        })
        .catch(() => clearFeatures(vectorSource));

    return {
        name: 'reference-line-alignment-layer',
        layer: layer,
        searchItems: shownItemsSearchFunction,
        searchShownItems: shownItemsSearchFunction,
    };
}
