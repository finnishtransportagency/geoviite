import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    AlignmentDataHolder,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { first } from 'utils/array-utils';

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

const layerName: MapLayerName = 'location-track-selected-alignment-layer';

export function createLocationTrackSelectedAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    olView: OlView,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const resolution = olView.getResolution() || 0;

    const selectedTrack = first(selection.selectedItems.locationTracks);
    const alignmentPromise: Promise<AlignmentDataHolder[]> = selectedTrack
        ? getSelectedLocationTrackMapAlignmentByTiles(
              changeTimes,
              mapTiles,
              publishType,
              selectedTrack,
          )
        : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const selectedTrack = first(locationTracks);
        if (!selectedTrack) {
            return [];
        }

        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;
        const isSplitting = splittingState?.originLocationTrack.id === selectedTrack.header.id;
        return createAlignmentFeature(
            selectedTrack,
            showEndPointTicks,
            isSplitting ? splittingLocationTrackStyle : selectedLocationTrackStyle,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
