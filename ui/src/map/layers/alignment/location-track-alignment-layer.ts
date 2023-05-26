import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import OlView from 'ol/View';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import {
    clearFeatures,
    getMatchingAlignmentData,
    MatchOptions,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { fromExtent } from 'ol/geom/Polygon';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentFeatures } from 'map/layers/alignment/alignment-layer-utils';
import { filterNotEmpty, filterUnique } from 'utils/array-utils';

let compareString = '';
let newestLayerId = 0;

export function createLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const resolution = olView.getResolution() || 0;

    const shownItemsSearchFunction = (
        hitArea: Polygon,
        options: SearchItemsOptions,
    ): LayerItemSearchResult => {
        const matchOptions: MatchOptions = {
            strategy: options.limit == 1 ? 'nearest' : 'limit',
            limit: undefined,
        };

        const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
        const locationTracks = getMatchingAlignmentData(hitArea, features, matchOptions)
            .map(({ header }) => header.id)
            .filter(filterUnique)
            .filter(filterNotEmpty)
            .slice(0, options.limit);

        return { locationTracks };
    };

    if (resolution <= Limits.ALL_ALIGNMENTS) {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'LOCATION_TRACKS')
            .then((alignments) => {
                if (layerId !== newestLayerId) return;

                const features = createAlignmentFeatures(
                    alignments,
                    selection,
                    linkingState,
                    showEndPointTicks,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);

                const compare = alignments
                    .map(({ header }) => header.id)
                    .sort()
                    .join();

                if (compare !== compareString) {
                    compareString = compare;
                    const area = fromExtent(olView.calculateExtent());
                    const result = shownItemsSearchFunction(area, {});
                    onViewContentChanged(result);
                }
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-alignment-layer',
        layer: layer,
        searchItems: shownItemsSearchFunction,
        searchShownItems: shownItemsSearchFunction,
    };
}
