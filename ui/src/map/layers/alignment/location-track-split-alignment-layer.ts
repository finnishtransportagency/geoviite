import { LineString, Point as OlPoint } from 'ol/geom';
import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { MapLayerName, MapTile } from 'map/map-model';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import {
    AlignmentDataHolder,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { SplitTarget, SplitTargetId, SplittingState } from 'tool-panel/location-track/split-store';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import { filterNotEmpty, first, last } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { expectDefined } from 'utils/type-utils';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { interpolate } from 'utils/math-utils';

const splittingLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 3,
    }),
    zIndex: 2,
});
const splittingLocationTrackDisabledStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLineDisabled,
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
const splittingLocationTrackDisabledFocusedStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLineDisabled,
        width: 6,
    }),
    zIndex: 2,
});

const layerName: MapLayerName = 'location-track-split-alignment-layer';

const alignmentStyle = (enabled: boolean, focused: boolean) => {
    if (focused) {
        return enabled
            ? splittingLocationTrackFocusedStyle
            : splittingLocationTrackDisabledFocusedStyle;
    } else {
        return enabled ? splittingLocationTrackStyle : splittingLocationTrackDisabledStyle;
    }
};

function splitToParts(
    alignment: AlignmentDataHolder,
    splits: SplitTarget[],
    focusedSplits: SplitTargetId[],
    splittingEnabled: boolean,
): Feature<LineString | OlPoint>[] {
    const endOfAlignment = expectDefined(last(alignment.points)).m;
    return splits.flatMap((split, index, allSplits) => {
        const start = split.distance;
        const end =
            index + 1 < allSplits.length
                ? expectDefined(allSplits[index + 1]).distance
                : endOfAlignment;
        const startIndex = alignment.points.findIndex((point) => point.m >= start);
        const firstIndexPastEnd = findFirstIndexAfterM(alignment.points, end);
        const pointsForSplit = [
            ...interpolateWithPrecedingPointOnAlignment(alignment.points, startIndex, start),
            ...alignment.points.slice(startIndex, firstIndexPastEnd),
            ...interpolateWithPrecedingPointOnAlignment(alignment.points, firstIndexPastEnd, end),
        ];
        const alignmentPart = {
            ...alignment,
            points: pointsForSplit,
        };

        const splitIsFocused = focusedSplits.some((splitInFocus) => splitInFocus == split.id);

        return createAlignmentFeature(
            alignmentPart,
            false,
            alignmentStyle(splittingEnabled, splitIsFocused),
        );
    });
}

function findFirstIndexAfterM(points: AlignmentPoint[], m: number): number {
    const index = points.findIndex((point) => point.m > m);
    return index === -1 ? points.length : index;
}

function interpolateWithPrecedingPointOnAlignment(
    points: AlignmentPoint[],
    index: number,
    alignmentM: number,
): AlignmentPoint[] {
    return index <= 0 || index >= points.length
        ? []
        : [
              linearlyInterpolateAlignmentPoint(
                  expectDefined(points[index - 1]),
                  expectDefined(points[index]),
                  alignmentM,
              ),
          ];
}

function linearlyInterpolateAlignmentPoint(
    start: AlignmentPoint,
    end: AlignmentPoint,
    alignmentM: number,
): AlignmentPoint {
    const portion = (alignmentM - start.m) / (end.m - start.m);
    return {
        x: interpolate(start.x, end.x, portion),
        y: interpolate(start.y, end.y, portion),
        m: alignmentM,
    };
}

export function createLocationTrackSplitAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<LineString | OlPoint>> | undefined,
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);
    const splittingEnabled = splittingState ? !splittingState.disabled : false;

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

        createAlignmentFeature(splitTrack, false, alignmentStyle(splittingEnabled, true));

        return splitToParts(
            splitTrack,
            [splittingState.firstSplit, ...splittingState.splits],
            [splittingState.focusedSplit, splittingState.highlightedSplit].filter(filterNotEmpty),
            splittingEnabled,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
