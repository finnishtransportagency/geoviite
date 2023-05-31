import { useEffect, useMemo, useRef, useState } from 'react';
import { VerticalGeometryDiagramAlignmentId } from 'vertical-geometry/vertical-geometry-diagram';
import {
    getLocationTrackHeights,
    getPlanAlignmentHeights,
    TrackKmHeights,
} from 'geometry/geometry-api';
import throttle from '@jcoreio/async-throttle';
import { ChangeTimes } from 'common/common-slice';
import { toDate } from 'utils/date-utils';

type HeightCacheKey = string;
type HeightCacheItem = {
    resolved: TrackKmHeights[];
};

function timeClearedCache<O, T extends { changeTime: Date | null; clear: () => void; obj: O }>(
    obj: T,
): (changeTime: Date) => O {
    return (changeTime) => {
        if (obj.changeTime === null || changeTime > obj.changeTime) {
            obj.changeTime = changeTime;
            obj.clear();
        }
        return obj.obj;
    };
}

const geometryHeightsCache: (changeTime: Date) => Map<HeightCacheKey, HeightCacheItem> = (() => {
    const cache = new Map();
    return timeClearedCache({ obj: cache, clear: () => cache.clear(), changeTime: null });
})();

const locationTrackHeightsCache: (changeTime: Date) => Map<HeightCacheKey, HeightCacheItem> =
    (() => {
        const cache = new Map();
        return timeClearedCache({ obj: cache, clear: () => cache.clear(), changeTime: null });
    })();

function heightCacheKey(
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheKey {
    return 'locationTrackId' in alignmentId
        ? `${alignmentId.locationTrackId}_${alignmentId.publishType}_${tickLength}`
        : `${alignmentId.planId}_${alignmentId.alignmentId}_${tickLength}`;
}

function getCache(changeTimes: ChangeTimes, alignmentId: VerticalGeometryDiagramAlignmentId) {
    return 'planId' in alignmentId
        ? geometryHeightsCache(toDate(changeTimes.geometryPlan))
        : locationTrackHeightsCache(toDate(changeTimes.layoutLocationTrack));
}

function getCacheItem(
    changeTimes: ChangeTimes,
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheItem | undefined {
    const cache = getCache(changeTimes, alignmentId);
    const key = heightCacheKey(alignmentId, tickLength);
    return cache.get(key);
}

function getOrCreateCacheItem(
    changeTimes: ChangeTimes,
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheItem {
    const cache = getCache(changeTimes, alignmentId);
    const key = heightCacheKey(alignmentId, tickLength);
    const item = cache.get(key);
    if (item !== undefined) {
        return item;
    } else {
        const newItem = {
            resolved: [],
        };
        cache.set(key, newItem);
        return newItem;
    }
}

export function weaveKms<T>(getFirstM: (km: T) => number, left: T[], right: T[]): T[] {
    const rv: T[] = [];
    let leftI = 0,
        rightI = 0;

    while (leftI < left.length && rightI < right.length) {
        const leftM = getFirstM(left[leftI]);
        const rightM = getFirstM(right[rightI]);
        if (leftM < rightM) {
            rv.push(left[leftI++]);
        } else if (rightM < leftM) {
            rv.push(right[rightI++]);
        } else {
            rv.push(left[leftI++]);
            rightI++;
        }
    }
    if (leftI >= left.length) {
        rv.push(...right.slice(rightI));
    } else if (rightI >= right.length) {
        rv.push(...left.slice(leftI));
    }
    return rv;
}

async function loadAlignmentHeights(
    alignmentId: VerticalGeometryDiagramAlignmentId,
    startM: number,
    endM: number,
    tickLength: number,
): Promise<[TrackKmHeights[], [VerticalGeometryDiagramAlignmentId, number]]> {
    return (
        'planId' in alignmentId
            ? getPlanAlignmentHeights(
                  alignmentId.planId,
                  alignmentId.alignmentId,
                  startM,
                  endM,
                  tickLength,
              )
            : getLocationTrackHeights(
                  alignmentId.locationTrackId,
                  alignmentId.publishType,
                  startM,
                  endM,
                  tickLength,
              )
    ).then((r) => [r, [alignmentId, tickLength]]);
}

export function getMissingCoveringRange(
    ranges: [number, number][],
    queryStart: number,
    queryEnd: number,
): null | [number, number] {
    const leftStart = ranges.findIndex(([_, rangeEnd]) => rangeEnd >= queryStart);
    if (leftStart !== -1) {
        for (let i = 0; i < ranges.length; i++) {
            if (ranges[i][0] <= queryStart && ranges[i][1] >= queryStart) {
                queryStart = ranges[i][1];
            } else {
                break;
            }
        }
    }
    const rightStart = ranges.findIndex(([_, rangeEnd]) => rangeEnd >= queryEnd);
    if (rightStart !== -1) {
        for (let i = rightStart; i >= 0; i--) {
            if (ranges[i][0] <= queryEnd && ranges[i][1] >= queryEnd) {
                queryEnd = ranges[i][0];
            } else {
                break;
            }
        }
    }
    return queryStart >= queryEnd ? null : [queryStart, queryEnd];
}

function getQueryableRange(
    resolved: TrackKmHeights[],
    startM: number,
    endM: number,
): [number, number] | null {
    return getMissingCoveringRange(
        resolved.map((r) => [r.trackMeterHeights[0].m, r.endM]),
        startM,
        endM,
    );
}

export function useAlignmentHeights(
    alignmentId: VerticalGeometryDiagramAlignmentId,
    changeTimes: ChangeTimes,
    startM: number,
    endM: number,
    tickLength: number,
): { heights: TrackKmHeights[]; alignmentId: VerticalGeometryDiagramAlignmentId } | undefined {
    const [loadedHeights, setLoadedHeights] = useState<{
        heights: TrackKmHeights[];
        alignmentId: VerticalGeometryDiagramAlignmentId;
    }>();

    const renderedRange = useRef({ alignmentId, startM, endM, tickLength });
    renderedRange.current = { alignmentId, startM, endM, tickLength };

    const updateLoadedHeights = () => {
        const cacheItem = getCacheItem(changeTimes, alignmentId, tickLength);
        if (cacheItem !== undefined) {
            setLoadedHeights({
                heights: cacheItem.resolved.filter(
                    (item) => item.trackMeterHeights[0].m <= endM && item.endM >= startM,
                ),
                alignmentId,
            });
        }
    };

    const throttledLoadAlignmentHeights = useMemo(
        () => throttle(loadAlignmentHeights, 0),
        [alignmentId],
    );

    useEffect(() => {
        const cacheItem = getOrCreateCacheItem(changeTimes, alignmentId, tickLength);
        const queryRange = getQueryableRange(cacheItem.resolved, startM, endM);
        if (queryRange === null) {
            updateLoadedHeights();
        } else {
            throttledLoadAlignmentHeights(
                alignmentId,
                queryRange[0],
                queryRange[1],
                tickLength,
            ).then(([loadedKms, [loadedAlignmentId, loadedTickLength]]) => {
                // the throttled fetcher can return results for earlier queries
                const loadedCacheItem = getOrCreateCacheItem(
                    changeTimes,
                    loadedAlignmentId,
                    loadedTickLength,
                );
                loadedCacheItem.resolved = weaveKms(
                    (kms) => kms.trackMeterHeights[0].m,
                    loadedCacheItem.resolved,
                    loadedKms,
                );
                if (
                    renderedRange.current.alignmentId === alignmentId &&
                    renderedRange.current.startM === startM &&
                    renderedRange.current.endM === endM &&
                    renderedRange.current.tickLength === tickLength
                ) {
                    updateLoadedHeights();
                }
            });
        }
    }, [alignmentId, startM, endM, tickLength]);

    return loadedHeights;
}
