import { LineString, Point as OlPoint } from 'ol/geom';
import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { MapLayerName, MapTile } from 'map/map-model';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    AlignmentDataHolder,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { SplitTarget, SplitTargetId, SplittingState } from 'tool-panel/location-track/split-store';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import { filterNotEmpty, first, last } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

const splittingLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 3,
    }),
    zIndex: 2,
});

const splittingLocationTrackFocusedStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 6,
    }),
    zIndex: 2,
});

const layerName: MapLayerName = 'location-track-split-alignment-layer';

function splitToParts(
    alignment: AlignmentDataHolder,
    splits: SplitTarget[],
    focusedSplits: SplitTargetId[],
): Feature<LineString | OlPoint>[] {
    const endOfAlignment = last(alignment.points).m;
    return splits.flatMap((split, index, allSplits) => {
        const start = split.distance;
        const end = index + 1 < allSplits.length ? allSplits[index + 1].distance : endOfAlignment;
        const pointsForSplit = alignment.points.filter(
            (point) => point.m >= start && point.m <= end,
        );
        const alignmentPart = {
            ...alignment,
            points: pointsForSplit,
        };

        const splitIsFocused = focusedSplits.some((splitInFocus) => splitInFocus == split.id);

        return createAlignmentFeature(
            alignmentPart,
            false,
            splitIsFocused ? splittingLocationTrackFocusedStyle : splittingLocationTrackStyle,
        );
    });
}

export function createLocationTrackSplitAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const alignmentPromise: Promise<AlignmentDataHolder[]> =
        splittingState != undefined
            ? getSelectedLocationTrackMapAlignmentByTiles(
                  changeTimes,
                  mapTiles,
                  layoutContext,
                  splittingState.originLocationTrack.id,
              )
            : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const splitTrack = first(locationTracks);
        if (!splittingState || !splitTrack) {
            return [];
        }

        if (Math.random() >= 0) {
            createAlignmentFeature(splitTrack, false, splittingLocationTrackFocusedStyle);
        }

        return splitToParts(
            splitTrack,
            [splittingState.firstSplit, ...splittingState.splits],
            [splittingState.focusedSplit, splittingState.highlightedSplit].filter(filterNotEmpty),
        );
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
