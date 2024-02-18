import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { getSelectedLocationTrackMapAlignmentByTiles } from 'track-layout/layout-map-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

let newestLayerId = 0;

const selectedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 2,
    }),
    zIndex: 2,
});

const splittingLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 2,
});

export function createLocationTrackSelectedAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    olView: OlView,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const resolution = olView.getResolution() || 0;

    let inFlight = true;
    const selectedTrack = selection.selectedItems.locationTracks[0];
    const alignmentPromise = selectedTrack
        ? getSelectedLocationTrackMapAlignmentByTiles(
              changeTimes,
              mapTiles,
              publishType,
              selectedTrack,
          )
        : Promise.resolve([]);

    alignmentPromise
        .then((locationTracks) => {
            if (!locationTracks[0]) {
                clearFeatures(vectorSource);
                return;
            }
            if (layerId !== newestLayerId) return;

            const selectedTrack = locationTracks[0];
            const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

            const isSplitting = splittingState?.originLocationTrack.id === selectedTrack.header.id;
            const alignmentFeatures = createAlignmentFeature(
                selectedTrack,
                showEndPointTicks,
                isSplitting ? splittingLocationTrackStyle : selectedLocationTrackStyle,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(alignmentFeatures);
        })
        .catch(() => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
            }
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'location-track-selected-alignment-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
