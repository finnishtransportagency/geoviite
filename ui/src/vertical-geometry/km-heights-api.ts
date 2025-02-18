import { useEffect, useMemo, useRef, useState } from 'react';
import {
    getLocationTrackHeights,
    getPlanAlignmentHeights,
    TrackKmHeights,
} from 'geometry/geometry-api';
import throttle from '@jcoreio/async-throttle';
import { ChangeTimes } from 'common/common-slice';
import { getMaxTimestamp, toDate } from 'utils/date-utils';
import { VerticalGeometryDiagramAlignmentId } from 'vertical-geometry/store';
import { TimeStamp } from 'common/common-model';
import { expectDefined } from 'utils/type-utils';
import { first } from 'utils/array-utils';

type HeightCacheKey = string;
type HeightCacheItem = {
    resolved: TrackKmHeights[];
};

function timeClearedCache<O, T extends { changeTime: Date | undefined; clear: () => void; obj: O }>(
    obj: T,
): (changeTime: Date) => O {
    return (changeTime) => {
        if (obj.changeTime === undefined || changeTime > obj.changeTime) {
            obj.changeTime = changeTime;
            obj.clear();
        }
        return obj.obj;
    };
}

const geometryHeightsCache: (changeTime: Date) => Map<HeightCacheKey, HeightCacheItem> = (() => {
    const cache = new Map();
    return timeClearedCache({ obj: cache, clear: () => cache.clear(), changeTime: undefined });
})();

const locationTrackHeightsCache: (changeTime: Date) => Map<HeightCacheKey, HeightCacheItem> =
    (() => {
        const cache = new Map();
        return timeClearedCache({ obj: cache, clear: () => cache.clear(), changeTime: undefined });
    })();

function heightCacheKey(
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheKey {
    return 'locationTrackId' in alignmentId
        ? `${alignmentId.locationTrackId}_${alignmentId.layoutContext.publicationState}_${alignmentId.layoutContext.branch}_${tickLength}`
        : `${alignmentId.planId}_${alignmentId.alignmentId}_${tickLength}`;
}

function getCache(changeTime: TimeStamp, alignmentId: VerticalGeometryDiagramAlignmentId) {
    return 'planId' in alignmentId
        ? geometryHeightsCache(toDate(changeTime))
        : locationTrackHeightsCache(toDate(changeTime));
}

function getCacheItem(
    changeTime: TimeStamp,
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheItem | undefined {
    const cache = getCache(changeTime, alignmentId);
    const key = heightCacheKey(alignmentId, tickLength);
    return cache.get(key);
}

function getOrCreateCacheItem(
    changeTime: TimeStamp,
    alignmentId: VerticalGeometryDiagramAlignmentId,
    tickLength: number,
): HeightCacheItem {
    const cache = getCache(changeTime, alignmentId);
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
        const leftM = getFirstM(expectDefined(left[leftI]));
        const rightM = getFirstM(expectDefined(right[rightI]));
        if (leftM < rightM) {
            rv.push(expectDefined(left[leftI++]));
        } else if (rightM < leftM) {
            rv.push(expectDefined(right[rightI++]));
        } else {
            rv.push(expectDefined(left[leftI++]));
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
): Promise<[TrackKmHeights[], number]> {
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
                  alignmentId.layoutContext,
                  startM,
                  endM,
                  tickLength,
              )
    ).then((r) => [r, tickLength]);
}

export function getMissingCoveringRange(
    ranges: [number, number][],
    queryStart: number,
    queryEnd: number,
): undefined | [number, number] {
    const leftStart = ranges.findIndex(([_, rangeEnd]) => rangeEnd >= queryStart);
    if (leftStart !== -1) {
        ranges.forEach((range) => {
            if (range[0] <= queryStart && range[1] >= queryStart) {
                queryStart = range[1];
            } else {
                return;
            }
        });
    }
    const rightStart = ranges.findIndex(([_, rangeEnd]) => rangeEnd >= queryEnd);
    if (rightStart !== -1) {
        [...ranges].reverse().forEach((range) => {
            if (range[0] <= queryEnd && range[1] >= queryEnd) {
                queryEnd = range[0];
            } else {
                return;
            }
        });
    }
    return queryStart >= queryEnd ? undefined : [queryStart, queryEnd];
}

function getQueryableRange(
    resolved: TrackKmHeights[],
    startM: number,
    endM: number,
): [number, number] | undefined {
    return getMissingCoveringRange(
        resolved.map((r) => [expectDefined(first(r.trackMeterHeights)).m, r.endM]),
        startM,
        endM,
    );
}

export function useAlignmentHeights(
    alignmentId: VerticalGeometryDiagramAlignmentId | undefined,
    changeTimes: ChangeTimes,
    startM: number | undefined,
    endM: number | undefined,
    tickLength: number,
): TrackKmHeights[] | undefined {
    const [loadedHeights, setLoadedHeights] = useState<TrackKmHeights[]>();

    const changeTime =
        alignmentId && 'planId' in alignmentId
            ? changeTimes.geometryPlan
            : getMaxTimestamp(
                  changeTimes.geometryPlan,
                  changeTimes.layoutLocationTrack,
                  changeTimes.layoutReferenceLine,
                  changeTimes.layoutKmPost,
              );
    const renderedRange = useRef({ alignmentId, startM, endM, tickLength, changeTime });
    renderedRange.current = { alignmentId, startM, endM, tickLength, changeTime };

    const updateLoadedHeights = (sM: number, eM: number) => {
        const cacheItem = alignmentId && getCacheItem(changeTime, alignmentId, tickLength);
        if (cacheItem) {
            setLoadedHeights(
                cacheItem.resolved.filter(
                    (item) =>
                        expectDefined(first(item.trackMeterHeights)).m <= eM && item.endM >= sM,
                ),
            );
        }
    };

    const throttledLoadAlignmentHeights = useMemo(
        () => throttle(loadAlignmentHeights, 0),
        [alignmentId],
    );

    useEffect(() => {
        if (startM === undefined || endM === undefined) {
            setLoadedHeights(undefined);
            return;
        }

        const cacheItem = alignmentId && getOrCreateCacheItem(changeTime, alignmentId, tickLength);
        const queryRange = cacheItem && getQueryableRange(cacheItem.resolved, startM, endM);
        if (queryRange === undefined) {
            updateLoadedHeights(startM, endM);
        } else if (alignmentId && queryRange) {
            throttledLoadAlignmentHeights(
                alignmentId,
                queryRange[0],
                queryRange[1],
                tickLength,
            ).then(([loadedKms, loadedTickLength]) => {
                // the throttled fetcher can return results for earlier queries
                const loadedCacheItem = getOrCreateCacheItem(
                    changeTime,
                    alignmentId,
                    loadedTickLength,
                );

                loadedCacheItem.resolved = weaveKms(
                    (kms) => expectDefined(first(kms.trackMeterHeights)).m,
                    loadedCacheItem.resolved,
                    loadedKms,
                );

                if (
                    renderedRange.current.alignmentId === alignmentId &&
                    renderedRange.current.startM === startM &&
                    renderedRange.current.endM === endM &&
                    renderedRange.current.tickLength === tickLength &&
                    renderedRange.current.changeTime === changeTime
                ) {
                    updateLoadedHeights(startM, endM);
                }
            });
        }
    }, [alignmentId, startM, endM, tickLength, changeTime]);

    return loadedHeights;
}
