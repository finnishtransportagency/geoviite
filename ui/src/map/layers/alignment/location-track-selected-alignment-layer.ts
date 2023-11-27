import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createEndPointTicks,
    getAlignmentHeaderStates,
    highlightedLocationTrackStyle,
    highlightedReferenceLineStyle,
    setAlignmentFeatureProperty,
} from 'map/layers/utils/alignment-layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import Feature from 'ol/Feature';
import mapStyles from 'map/map.module.scss';
import { Stroke, Style } from 'ol/style';

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

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 1,
});

export function createSelectedAlignmentFeature(
    alignment: AlignmentDataHolder,
    selection: Selection,
    linkingState: LinkingState | undefined,
    showEndTicks: boolean,
    splittingState: SplittingState | undefined,
): Feature<LineString | OlPoint>[] {
    const { selected, isLinking, highlighted } = getAlignmentHeaderStates(
        alignment,
        selection,
        linkingState,
    );

    const features: Feature<LineString | OlPoint>[] = [];
    const alignmentFeature = new Feature({
        geometry: new LineString(alignment.points.map(pointToCoords)),
    });
    features.push(alignmentFeature);

    const isReferenceLine = alignment.header.alignmentType === 'REFERENCE_LINE';

    if (splittingState?.originLocationTrack.id === alignment.header.id) {
        alignmentFeature.setStyle(splittingLocationTrackStyle);
    } else if (selected || isLinking) {
        alignmentFeature.setStyle(
            isReferenceLine ? selectedReferenceLineStyle : selectedLocationTrackStyle,
        );
    } else if (highlighted) {
        alignmentFeature.setStyle(
            isReferenceLine ? highlightedReferenceLineStyle : highlightedLocationTrackStyle,
        );
    }

    if (showEndTicks) {
        features.push(...createEndPointTicks(alignment, selected || isLinking || highlighted));
    }

    setAlignmentFeatureProperty(alignmentFeature, alignment);

    return features;
}

export function createLocationTrackSelectedAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
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
    const alignmentPromise =
        resolution <= ALL_ALIGNMENTS || selectedTrack
            ? getLocationTrackMapAlignmentsByTiles(
                  changeTimes,
                  mapTiles,
                  publishType,
                  selectedTrack,
              )
            : Promise.resolve([]);

    alignmentPromise
        .then((locationTracks) => {
            if (layerId !== newestLayerId) return;

            const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

            const alignmentFeatures = createSelectedAlignmentFeature(
                locationTracks.filter((lt) => lt.header.id === selectedTrack)[0],
                selection,
                linkingState,
                showEndPointTicks,
                splittingState,
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
