import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import {
    AlignmentDataHolder,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import {
    builtAlignmentBackgroundLineStroke,
    builtAlignmentLineDash,
    createAlignmentFeature,
    getAlignmentZIndex,
} from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { first } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

const selectedExistingOrNotInUseLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 2,
    }),
    zIndex: getAlignmentZIndex('IN_USE', 'NOT_HIGHLIGHTED'),
});

const selectedLocationTrackBuiltStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 2,
        ...builtAlignmentLineDash,
    }),
    zIndex: getAlignmentZIndex('BUILT', 'NOT_HIGHLIGHTED'),
});
const selectedLocationTrackBuiltBackgroundStyle = new Style({
    stroke: builtAlignmentBackgroundLineStroke,
    zIndex: getAlignmentZIndex('BUILT_BACKGROUND', 'NOT_HIGHLIGHTED'),
});

const layerName: MapLayerName = 'location-track-selected-alignment-layer';

export function createLocationTrackSelectedAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    splittingIsActive: boolean, // TODO: This will be removed when layer visibility logic is revised
    changeTimes: ChangeTimes,
    olView: OlView,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const resolution = olView.getResolution() || 0;

    const selectedTrack = first(selection.selectedItems.locationTracks);
    const alignmentPromise: Promise<AlignmentDataHolder[]> =
        selectedTrack && !splittingIsActive
            ? getSelectedLocationTrackMapAlignmentByTiles(
                  changeTimes,
                  mapTiles,
                  layoutContext,
                  selectedTrack,
              )
            : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const selectedTrack = first(locationTracks);
        if (!selectedTrack) {
            return [];
        }

        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;
        const endpointTickStyle =
            selectedTrack.header.state === 'BUILT'
                ? selectedLocationTrackBuiltStyle
                : selectedExistingOrNotInUseLocationTrackStyle;

        return createAlignmentFeature(
            selectedTrack,
            selectedTrack.header.state === 'BUILT'
                ? [selectedLocationTrackBuiltStyle, selectedLocationTrackBuiltBackgroundStyle]
                : [selectedExistingOrNotInUseLocationTrackStyle],
            showEndPointTicks ? endpointTickStyle : undefined,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
