import { Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import {
    AlignmentBadgeColor,
    createBadgeFeatures,
    getBadgeDrawDistance,
    getBadgePoints,
} from 'map/layers/utils/badge-layer-utils';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import {
    AlignmentDataHolder,
    getSelectedLocationTrackMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { AlignmentStartAndEnd } from 'track-layout/track-layout-model';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';
import { first } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { getMaxTimestamp } from 'utils/date-utils';

type SplitBoundsAndName = {
    start: number;
    end: number;
    name: string;
};

const createSplitBadgeFeatures = (
    alignment: AlignmentDataHolder,
    splits: SplitBoundsAndName[],
    badgeDrawDistance: number,
) =>
    splits.flatMap((split) => {
        const badgePoints = getBadgePoints(
            alignment.points.filter((point) => point.m >= split.start && point.m <= split.end),
            badgeDrawDistance,
        );

        return createBadgeFeatures(split.name, badgePoints, AlignmentBadgeColor.LIGHT, true);
    });

const calculateSplitBounds = (
    splittingState: SplittingState,
    originalStartAndEnd: AlignmentStartAndEnd,
): SplitBoundsAndName[] => {
    const splitsSorted = splittingState.splits;
    return [
        {
            start: originalStartAndEnd.start?.point.m || 0,
            end: first(splitsSorted)?.distance || originalStartAndEnd.end?.point?.m || 0,
            name: 'WOLOLOO', //splittingState.firstSplit.name,
        },
        ...splitsSorted.map((split, index) => ({
            start: split.distance,
            end:
                splitsSorted[index + 1]?.distance ||
                originalStartAndEnd.end?.point?.m ||
                split.distance,
            name: 'WOLOLOO', //split.name,
        })),
    ].filter((split) => split.name);
};

type LocationTrackSplitBadgeData = {
    locationTracks: AlignmentDataHolder[];
    startAndEnd: AlignmentStartAndEnd | undefined;
};

async function getLocationTrackSplitBadgeData(
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    mapTiles: MapTile[],
): Promise<LocationTrackSplitBadgeData> {
    if (resolution <= Limits.SHOW_LOCATION_TRACK_BADGES && splittingState) {
        const startEndChangeTime = getMaxTimestamp(
            changeTimes.layoutLocationTrack,
            changeTimes.layoutTrackNumber,
            changeTimes.layoutReferenceLine,
            changeTimes.layoutKmPost,
        );
        const [locationTracks, startAndEnd] = await Promise.all([
            getSelectedLocationTrackMapAlignmentByTiles(
                changeTimes,
                mapTiles,
                layoutContext,
                splittingState.originLocationTrack.id,
            ),
            getLocationTrackStartAndEnd(
                splittingState.originLocationTrack.id,
                layoutContext,
                startEndChangeTime,
            ),
        ]);
        return { locationTracks, startAndEnd };
    } else {
        return { locationTracks: [], startAndEnd: undefined };
    }
}

const layerName: MapLayerName = 'location-track-split-badge-layer';

export function createLocationTrackSplitBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise = getLocationTrackSplitBadgeData(
        layoutContext,
        splittingState,
        changeTimes,
        resolution,
        mapTiles,
    );

    const createFeatures = (data: LocationTrackSplitBadgeData) => {
        if (splittingState && data.startAndEnd) {
            const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;

            const alignmentDataHolders = data.locationTracks.filter(
                ({ header }) => header.id === splittingState.originLocationTrack.id,
            );

            const splitBounds = calculateSplitBounds(splittingState, data.startAndEnd);
            return alignmentDataHolders.flatMap((alignment) =>
                createSplitBadgeFeatures(alignment, splitBounds, badgeDrawDistance),
            );
        } else {
            return [];
        }
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
