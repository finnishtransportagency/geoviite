import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentFeatures,
    findMatchingAlignments,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

let shownLocationTracksCompare = '';
let newestLayerId = 0;

export function createLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
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

    let inFlight = false;
    if (resolution <= Limits.ALL_ALIGNMENTS) {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

        inFlight = true;
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
            })
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
        updateShownLocationTracks([]);
    }

    return {
        name: 'location-track-alignment-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            return {
                locationTracks: findMatchingAlignments(hitArea, vectorSource, options).map(
                    ({ header }) => header.id,
                ),
            };
        },
        onRemove: () => updateShownLocationTracks([]),
        requestInFlight: () => inFlight,
    };
}
