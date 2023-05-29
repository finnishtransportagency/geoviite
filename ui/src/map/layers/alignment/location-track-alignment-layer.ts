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
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentFeatures } from 'map/layers/alignment/alignment-layer-utils';
import { filterNotEmpty, filterUnique } from 'utils/array-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';

let shownLocationTracksCompare = '';
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

    function updateShownLocationTracks(locationTrackIds: LocationTrackId[]) {
        const compare = locationTrackIds.sort().join();

        if (compare !== shownLocationTracksCompare) {
            shownLocationTracksCompare = compare;
            onViewContentChanged({ locationTracks: locationTrackIds });
        }
    }

    if (resolution <= Limits.ALL_ALIGNMENTS) {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'LOCATION_TRACKS')
            .then((locationTracks) => {
                if (layerId !== newestLayerId) return;

                const features = createAlignmentFeatures(
                    locationTracks,
                    selection,
                    linkingState,
                    showEndPointTicks,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);

                updateShownLocationTracks(locationTracks.map(({ header }) => header.id));
            })
            .catch(() => {
                clearFeatures(vectorSource);
                updateShownLocationTracks([]);
            });
    } else {
        clearFeatures(vectorSource);
        updateShownLocationTracks([]);
    }

    return {
        name: 'location-track-alignment-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
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
        },
        onRemove: () => updateShownLocationTracks([]),
    };
}
