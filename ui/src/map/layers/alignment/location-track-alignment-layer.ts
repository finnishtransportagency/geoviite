import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentFeatures,
    findMatchingAlignments,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { getLocationTrackMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

let shownLocationTracksCompare = '';
let newestLayerId = 0;

const highlightedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 2,
});

const locationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 0,
});

export function createLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    isSplitting: boolean,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const resolution = olView.getResolution() || 0;

    function updateShownLocationTracks(locationTrackIds: LocationTrackId[]) {
        const compare = locationTrackIds.sort().join();

        if (compare !== shownLocationTracksCompare) {
            shownLocationTracksCompare = compare;
            onViewContentChanged({ locationTracks: locationTrackIds });
        }
    }

    let inFlight = true;
    const alignmentPromise =
        resolution <= ALL_ALIGNMENTS
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            : Promise.resolve([]);

    alignmentPromise
        .then((locationTracks) => {
            if (layerId !== newestLayerId) return;

            const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

            const features = createAlignmentFeatures(
                locationTracks,
                selection,
                showEndPointTicks,
                locationTrackStyle,
                highlightedLocationTrackStyle,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);

            updateShownLocationTracks(locationTracks.map(({ header }) => header.id));
        })
        .catch(() => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
                updateShownLocationTracks([]);
            }
        })
        .finally(() => {
            inFlight = false;
        });

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
