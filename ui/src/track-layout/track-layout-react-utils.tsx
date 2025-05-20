import {
    AlignmentStartAndEnd,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackInfoboxExtras,
    LocationTrackNaming,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { LoaderStatus, useLoader, useLoaderWithStatus, useOptionalLoader } from 'utils/react-utils';
import {
    CoordinateSystem,
    LayoutAssetChangeInfo,
    LayoutBranch,
    LayoutContext,
    Srid,
    SwitchStructure,
    SwitchStructureId,
    TimeStamp,
} from 'common/common-model';
import { getCoordinateSystem, getSwitchStructure } from 'common/common-api';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { getGeometryPlanHeader } from 'geometry/geometry-api';
import {
    getReferenceLine,
    getReferenceLineChangeTimes,
    getReferenceLines,
    getReferenceLineStartAndEnd,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import {
    getLocationTrack,
    getLocationTrackChangeTimes,
    getLocationTrackInfoboxExtras,
    getLocationTrackNames,
    getLocationTracks,
    getLocationTracksByName,
    getLocationTrackStartAndEnd,
} from 'track-layout/layout-location-track-api';
import { getSwitch, getSwitchChangeTimes, getSwitches } from 'track-layout/layout-switch-api';
import {
    getTrackNumberById,
    getTrackNumberChangeTimes,
    getTrackNumbers,
} from 'track-layout/layout-track-number-api';
import { getKmPost, getKmPostChangeInfo, getKmPosts } from 'track-layout/layout-km-post-api';
import { PVDocumentHeader, PVDocumentId } from 'infra-model/projektivelho/pv-model';
import { getPVDocument } from 'infra-model/infra-model-api';
import {
    getChangeTimes,
    updateAllChangeTimes,
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updateSwitchChangeTime,
} from 'common/change-time-api';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { deduplicate, first } from 'utils/array-utils';
import { validateLocationTrackName } from 'tool-panel/location-track/dialog/location-track-validation';
import { getMaxTimestamp, getMaxTimestampFromArray } from 'utils/date-utils';
import { ChangeTimes } from 'common/common-slice';
import { getLayoutDesignByBranch, LayoutDesign } from 'track-layout/layout-design-api';

export function useTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useOptionalLoader(
        () =>
            trackNumberId
                ? getTrackNumberReferenceLine(trackNumberId, layoutContext, changeTime)
                : undefined,
        [trackNumberId, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useReferenceLine(
    id: ReferenceLineId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useOptionalLoader(
        () => (id ? getReferenceLine(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useReferenceLines(
    ids: ReferenceLineId[],
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutReferenceLine[] {
    return (
        useLoader(
            () => (ids ? getReferenceLines(ids, layoutContext) : undefined),
            [JSON.stringify(ids), layoutContext.publicationState, layoutContext.branch, changeTime],
        ) || []
    );
}

export function useLocationTrack(
    id: LocationTrackId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutLocationTrack | undefined {
    return useOptionalLoader(
        () => (id ? getLocationTrack(id, layoutContext) : undefined),
        [id, layoutContext.publicationState, layoutContext.branch, changeTime],
    );
}

export function useLocationTracks(
    ids: LocationTrackId[],
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutLocationTrack[] {
    return (
        useLoader(
            () => (ids ? getLocationTracks(ids, layoutContext) : undefined),
            [JSON.stringify(ids), layoutContext.branch, layoutContext.publicationState, changeTime],
        ) || []
    );
}

export function useSwitch(
    id: LayoutSwitchId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutSwitch | undefined {
    return useOptionalLoader(
        () => (id ? getSwitch(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useSwitches(
    ids: LayoutSwitchId[] | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutSwitch[] {
    return (
        useLoader(
            () => (ids ? getSwitches(ids, layoutContext) : undefined),
            [JSON.stringify(ids), layoutContext.branch, layoutContext.publicationState, changeTime],
        ) || []
    );
}

export function useSwitchStructure(id: SwitchStructureId | undefined): SwitchStructure | undefined {
    return useLoader(() => (id ? getSwitchStructure(id) : undefined), [id]);
}

export function useTrackNumber(
    id: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutTrackNumber | undefined {
    return useLoader(
        () => (id ? getTrackNumberById(id, layoutContext, changeTime) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useTrackNumberWithStatus(
    id: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): [LayoutTrackNumber | undefined, LoaderStatus] {
    return useLoaderWithStatus(
        () => (id ? getTrackNumberById(id, layoutContext, changeTime) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useTrackNumbers(
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutTrackNumber[] | undefined {
    return useLoader(
        () => getTrackNumbers(layoutContext, changeTime),
        [layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useTrackNumbersIncludingDeleted(
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutTrackNumber[] | undefined {
    return useLoader(
        () => getTrackNumbers(layoutContext, changeTime, true),
        [layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useReferenceLineStartAndEnd(
    id: ReferenceLineId | undefined,
    layoutContext: LayoutContext,
    changeTime: TimeStamp | undefined = undefined,
): AlignmentStartAndEnd | undefined {
    return useLoader(
        () => (id ? getReferenceLineStartAndEnd(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useLocationTrackStartAndEnd(
    id: LocationTrackId | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): [AlignmentStartAndEnd | undefined, LoaderStatus] {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutLocationTrack,
        changeTimes.layoutTrackNumber,
        changeTimes.layoutReferenceLine,
        changeTimes.layoutKmPost,
    );
    return useLoaderWithStatus(
        () => (id ? getLocationTrackStartAndEnd(id, layoutContext, changeTime) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useLocationTrackInfoboxExtras(
    id: LocationTrackId | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): [LocationTrackInfoboxExtras | undefined, LoaderStatus] {
    return useLoaderWithStatus(
        () =>
            id === undefined
                ? undefined
                : getLocationTrackInfoboxExtras(id, layoutContext, changeTimes),
        [id, layoutContext.branch, layoutContext.publicationState, changeTimes],
    );
}

export function useConflictingTracks(
    trackNumberId: LayoutTrackNumberId | undefined,
    trackNames: LocationTrackNaming[],
    trackIds: LocationTrackId[],
    layoutContext: LayoutContext,
): LayoutLocationTrack[] | undefined {
    const properAlignmentNames = trackNames.filter(
        (name) => validateLocationTrackName(name).length === 0,
    );
    const namesString = JSON.stringify(properAlignmentNames);
    const trackIdsString = JSON.stringify(trackIds);
    return useLoader(
        () =>
            trackNumberId === undefined || properAlignmentNames.length === 0
                ? undefined
                : getLocationTracksByName(
                      trackNumberId,
                      properAlignmentNames,
                      layoutContext,
                      false,
                  ).then((tracks) => tracks.filter((t) => !trackIds.includes(t.id))),
        [
            trackNumberId,
            namesString,
            trackIdsString,
            layoutContext.branch,
            layoutContext.publicationState,
        ],
    );
}

export const useLocationTrackNames = (ids: LocationTrackId[], layoutContext: LayoutContext) => {
    const changeTimes = getChangeTimes();
    const maxChangeTime = getMaxTimestampFromArray([
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutTrackNumber,
    ]);
    return useLoader(
        () => getLocationTrackNames(ids, layoutContext, maxChangeTime),
        [JSON.stringify(ids), maxChangeTime],
    );
};

export const useLocationTrackName = (
    id: LocationTrackId | undefined,
    layoutContext: LayoutContext,
) => {
    const changeTimes = getChangeTimes();
    const maxChangeTime = getMaxTimestampFromArray([
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutTrackNumber,
    ]);

    return useLoader(
        () =>
            id !== undefined
                ? getLocationTrackNames([id], layoutContext, maxChangeTime).then((names) =>
                      first(names),
                  )
                : Promise.resolve(undefined),
        [id, maxChangeTime],
    );
};

export function usePlanHeader(id: GeometryPlanId | undefined): GeometryPlanHeader | undefined {
    return useLoader(() => (id ? getGeometryPlanHeader(id) : undefined), [id]);
}

export function useTrackNumberChangeTimes(
    id: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
): LayoutAssetChangeInfo | undefined {
    return useOptionalLoader(
        () => (id ? getTrackNumberChangeTimes(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState],
    );
}

export function useReferenceLineChangeTimes(
    id: ReferenceLineId | undefined,
    layoutContext: LayoutContext,
): LayoutAssetChangeInfo | undefined {
    return useOptionalLoader(
        () => (id ? getReferenceLineChangeTimes(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState],
    );
}

export function useLocationTrackChangeTimes(
    id: LocationTrackId | undefined,
    layoutContext: LayoutContext,
): LayoutAssetChangeInfo | undefined {
    return useOptionalLoader(
        () => (id ? getLocationTrackChangeTimes(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState],
    );
}

export function useSwitchChangeTimes(
    id: LayoutSwitchId | undefined,
    layoutContext: LayoutContext,
): LayoutAssetChangeInfo | undefined {
    return useOptionalLoader(
        () => (id ? getSwitchChangeTimes(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState],
    );
}

export function useKmPostChangeTimes(
    id: LayoutKmPostId | undefined,
    layoutContext: LayoutContext,
): LayoutAssetChangeInfo | undefined {
    return useOptionalLoader(
        () => (id ? getKmPostChangeInfo(id, layoutContext) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState],
    );
}

export function useCoordinateSystem(srid: Srid | undefined): CoordinateSystem | undefined {
    return useLoader(() => (srid === undefined ? undefined : getCoordinateSystem(srid)), [srid]);
}

export function useCoordinateSystems(srids: Srid[]): CoordinateSystem[] | undefined {
    return useLoader(
        () => Promise.all(srids.map((srid) => getCoordinateSystem(srid))),
        [srids.join('_')],
    );
}

export function useKmPost(
    id: LayoutKmPostId | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutKmPost | undefined {
    return useOptionalLoader(
        () => (id ? getKmPost(id, layoutContext, changeTime) : undefined),
        [id, layoutContext.branch, layoutContext.publicationState, changeTime],
    );
}

export function useKmPosts(
    ids: LayoutKmPostId[] | undefined,
    layoutContext: LayoutContext,
    changeTime?: TimeStamp,
): LayoutKmPost[] {
    return (
        useLoader(
            () => (ids ? getKmPosts(ids, layoutContext, changeTime) : undefined),
            [JSON.stringify(ids), layoutContext.branch, layoutContext.publicationState, changeTime],
        ) || []
    );
}

export function usePvDocumentHeader(
    id: PVDocumentId | undefined,
    changeTime?: TimeStamp,
): PVDocumentHeader | undefined {
    return useLoader(
        () => (id ? getPVDocument(changeTime, id).then((v) => v || undefined) : undefined),
        [id, changeTime],
    );
}

export function refreshTrackNumberSelection(
    layoutContext: LayoutContext,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutTrackNumberId) => void {
    return (id) => {
        Promise.all([updateAllChangeTimes()]).then(([changeTimes]) => {
            getTrackNumberById(id, layoutContext, changeTimes.layoutTrackNumber).then((tn) => {
                if (tn) onSelect({ trackNumbers: [id] });
                else onUnselect({ trackNumbers: [id] });
            });
        });
    };
}

export function refreshLocationTrackSelection(
    layoutContext: LayoutContext,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LocationTrackId) => void {
    return (id) => {
        updateLocationTrackChangeTime().then((ts) => {
            getLocationTrack(id, layoutContext, ts).then((lt) => {
                if (lt) onSelect({ locationTracks: [id] });
                else onUnselect({ locationTracks: [id] });
            });
        });
    };
}

export function refreshSwitchSelection(
    layoutContext: LayoutContext,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutSwitchId) => void {
    return (id) => {
        updateSwitchChangeTime().then((ts) => {
            getSwitch(id, layoutContext, ts).then((s) => {
                if (s) onSelect({ switches: [id] });
                else onUnselect({ switches: [id] });
            });
        });
    };
}

export function refereshKmPostSelection(
    layoutContext: LayoutContext,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutKmPostId) => void {
    return (id) => {
        updateKmPostChangeTime().then((ts) => {
            getKmPost(id, layoutContext, ts).then((kmp) => {
                if (kmp) onSelect({ kmPosts: [id] });
                else onUnselect({ kmPosts: [id] });
            });
        });
    };
}

export const getSaveDisabledReasons = (reasons: string[], saveInProgress: boolean): string[] =>
    saveInProgress
        ? ['save-in-progress']
        : deduplicate(
              reasons.map((reason) => {
                  if (reason.includes('mandatory-field')) return 'mandatory-fields-missing';
                  if (reason === 'km-post-regexp') return 'invalid-km-post-number';
                  else return reason;
              }),
          );

export const useLayoutDesign = (
    changeTime: TimeStamp,
    layoutBranch: LayoutBranch,
): LayoutDesign | undefined =>
    useLoader(
        () =>
            layoutBranch === 'MAIN' ? undefined : getLayoutDesignByBranch(changeTime, layoutBranch),
        [layoutBranch],
    );
