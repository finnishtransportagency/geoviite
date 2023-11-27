import { Point as OlPoint } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    AlignmentBadgeColor,
    createBadgeFeatures,
    getBadgeDrawDistance,
    getBadgePoints,
} from 'map/layers/utils/badge-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { sortSplitsByDistance, SplittingState } from 'tool-panel/location-track/split-store';
import { AlignmentStartAndEnd } from 'track-layout/track-layout-model';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';

let newestLayerId = 0;

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
    const splitsSorted = sortSplitsByDistance(splittingState.splits);
    return [
        {
            start: originalStartAndEnd.start?.point.m || 0,
            end: splitsSorted[0]?.distance || originalStartAndEnd.end?.point?.m || 0,
            name: splittingState.initialSplit.name,
        },
        ...splitsSorted.map((split, index) => ({
            start: split.distance,
            end:
                splitsSorted[index + 1]?.distance ||
                originalStartAndEnd.end?.point?.m ||
                split.distance,
            name: split.name,
        })),
    ].filter((split) => split.name);
};

export function createLocationTrackSplitBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    publishType: PublishType,
    splittingState: SplittingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= Limits.SHOW_LOCATION_TRACK_BADGES && splittingState) {
        const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;

        inFlight = true;
        getLocationTrackMapAlignmentsByTiles(
            changeTimes,
            mapTiles,
            publishType,
            splittingState.originLocationTrack.id,
        )
            .then((locationTracks) => {
                getLocationTrackStartAndEnd(
                    splittingState.originLocationTrack.id,
                    publishType,
                    changeTimes.layoutLocationTrack,
                ).then((startAndEnd) => {
                    if (layerId !== newestLayerId || !startAndEnd) return;

                    const alignmentDataHolders = locationTracks.filter(
                        ({ header }) => header.id === splittingState.originLocationTrack.id,
                    );

                    const splitBounds = calculateSplitBounds(splittingState, startAndEnd);
                    const features = alignmentDataHolders.flatMap((alignment) =>
                        createSplitBadgeFeatures(alignment, splitBounds, badgeDrawDistance),
                    );
                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                });
            })
            .catch(() => {
                if (layerId === newestLayerId) clearFeatures(vectorSource);
            })
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-split-badge-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
