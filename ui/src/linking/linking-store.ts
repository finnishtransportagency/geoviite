import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { PayloadAction } from '@reduxjs/toolkit';
import { fieldComparator, filterNotEmpty, first, last } from 'utils/array-utils';
import {
    AlignmentTypeAndId,
    emptyLinkInterval,
    GeometryLinkingAlignmentLockParameters,
    GeometryPreliminaryLinkingParameters,
    LinkingAlignment,
    LinkingGeometrySwitch,
    LinkingGeometryWithAlignment,
    LinkingGeometryWithEmptyAlignment,
    LinkingState,
    LinkingType,
    LinkInterval,
    LinkPoint,
    SuggestedSwitch,
} from 'linking/linking-model';
import {
    AlignmentId,
    AlignmentPoint,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { GeometryKmPostId, GeometryPlanId, GeometrySwitch } from 'geometry/geometry-model';
import { angleDiffRads, directionBetweenPoints } from 'utils/math-utils';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';
import { draftLayoutContext, LayoutContext, LayoutContextMode } from 'common/common-model';
import { brand } from 'common/brand';

export const linkingReducers = {
    startAlignmentLinking: (
        state: TrackLayoutState,
        { payload }: PayloadAction<GeometryPreliminaryLinkingParameters>,
    ): void => {
        const newLayoutContext = draftLayoutContext(state.layoutContext);
        state.layoutContext = newLayoutContext;
        state.layoutContextMode = inferLayoutContextMode(newLayoutContext);

        state.selection.selectedItems.clusterPoints = [];
        state.linkingState = {
            type: LinkingType.UnknownAlignment,
            state: 'preliminary',
            geometryPlanId: payload.geometryPlanId,
            geometryAlignmentId: payload.geometryAlignmentId,
            geometryAlignmentInterval: emptyLinkInterval,
            layoutAlignmentInterval: emptyLinkInterval,
        };
    },
    lockAlignmentSelection: (
        state: TrackLayoutState,
        { payload }: PayloadAction<GeometryLinkingAlignmentLockParameters>,
    ) => {
        if (
            state.linkingState?.state === 'preliminary' &&
            state.linkingState.type === LinkingType.UnknownAlignment
        ) {
            state.linkingState = {
                ...state.linkingState,
                state: 'setup',
                layoutAlignment: payload.alignment,
                type: payload.type,
                issues: [],
            };
        }
    },
    stopLinking: function (state: TrackLayoutState): void {
        state.linkingState = undefined;
        state.selection.selectedItems.clusterPoints = [];
    },
    setLayoutLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type === LinkingType.LinkingAlignment ||
            state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment
        ) {
            state.linkingState.layoutAlignmentInterval = createUpdatedInterval(
                state.linkingState.layoutAlignmentInterval,
                linkPoint,
                true,
            );
            state.linkingState = validateLinkingState(state.linkingState);
        }
    },
    setGeometryLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
            state.linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment
        ) {
            state.linkingState.geometryAlignmentInterval = createUpdatedInterval(
                state.linkingState.geometryAlignmentInterval,
                linkPoint,
                true,
            );
            state.linkingState = validateLinkingState(state.linkingState);
        }
    },
    setLayoutClusterLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type === LinkingType.LinkingAlignment ||
            state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment
        ) {
            state.linkingState.layoutAlignmentInterval = createUpdatedInterval(
                state.linkingState.layoutAlignmentInterval,
                linkPoint,
                false,
            );
            state.linkingState = validateLinkingState(state.linkingState);
            state.selection.selectedItems.clusterPoints = [];
        }
    },
    setGeometryClusterLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
            state.linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment
        ) {
            state.linkingState.geometryAlignmentInterval = createUpdatedInterval(
                state.linkingState.geometryAlignmentInterval,
                linkPoint,
                false,
            );
            state.linkingState = validateLinkingState(state.linkingState);
            state.selection.selectedItems.clusterPoints = [];
        }
    },
    removeGeometryLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            (state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
                state.linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment) &&
            (state.linkingState.geometryAlignmentInterval.start?.id === linkPoint.id ||
                state.linkingState.geometryAlignmentInterval.end?.id === linkPoint.id)
        ) {
            state.linkingState.geometryAlignmentInterval = createUpdatedIntervalRemovePoint(
                state.linkingState.geometryAlignmentInterval,
                linkPoint,
            );
            state.linkingState = validateLinkingState(state.linkingState);
            state.selection.selectedItems.clusterPoints = [];
        }
    },
    removeLayoutLinkPoint: function (
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            (state.linkingState?.type === LinkingType.LinkingAlignment ||
                state.linkingState?.type === LinkingType.LinkingGeometryWithAlignment) &&
            (state.linkingState.layoutAlignmentInterval.start?.id === linkPoint.id ||
                state.linkingState.layoutAlignmentInterval.end?.id === linkPoint.id)
        ) {
            state.linkingState.layoutAlignmentInterval = createUpdatedIntervalRemovePoint(
                state.linkingState.layoutAlignmentInterval,
                linkPoint,
            );
            state.linkingState = validateLinkingState(state.linkingState);
            state.selection.selectedItems.clusterPoints = [];
        }
    },

    startAlignmentGeometryChange: (
        state: TrackLayoutState,
        { payload: interval }: PayloadAction<LinkInterval>,
    ): void => {
        const alignmentId = interval.start?.alignmentId || interval.end?.alignmentId;
        const alignmentType = interval.start?.alignmentType || interval.end?.alignmentType;

        if (alignmentId && alignmentType) {
            const newLayoutContext = draftLayoutContext(state.layoutContext);
            state.layoutContext = newLayoutContext;
            state.layoutContextMode = inferLayoutContextMode(newLayoutContext);

            state.linkingState = validateLinkingState({
                layoutAlignment: {
                    id: brand<LocationTrackId & ReferenceLineId>(alignmentId),
                    type: alignmentType,
                },
                layoutAlignmentInterval: interval,
                type: LinkingType.LinkingAlignment,
                state: 'setup',
                issues: [],
            });
        }
    },
    startSwitchPlacing: (
        state: TrackLayoutState,
        { payload: layoutSwitch }: PayloadAction<LayoutSwitch>,
    ) => {
        state.linkingState = {
            type: LinkingType.PlacingLayoutSwitch,
            layoutSwitch: layoutSwitch,
            location: undefined,
            state: 'preliminary',
            issues: [],
        };
    },
    startGeometrySwitchLinking: (
        state: TrackLayoutState,
        {
            payload: { suggestedSwitch, geometrySwitch, geometryPlanId },
        }: PayloadAction<{
            suggestedSwitch: SuggestedSwitch;
            geometrySwitch: GeometrySwitch;
            geometryPlanId: GeometryPlanId;
        }>,
    ): void => {
        const newLayoutContext = draftLayoutContext(state.layoutContext);
        state.layoutContext = newLayoutContext;
        state.layoutContextMode = inferLayoutContextMode(newLayoutContext);

        state.linkingState = {
            type: LinkingType.LinkingGeometrySwitch,
            suggestedSwitch: suggestedSwitch,
            geometrySwitchId: geometrySwitch.id,
            suggestedSwitchName: geometrySwitch.name,
            geometryPlanId,
            layoutSwitchId: undefined,
            state: 'preliminary',
            issues: [],
        };

        // Ensure that layout switch is not selected by accident,
        // operator needs to take an action to select a switch
        state.selection.selectedItems.switches = [];
    },
    startLayoutSwitchLinking: (
        state: TrackLayoutState,
        {
            payload: { suggestedSwitch, layoutSwitch },
        }: PayloadAction<{ suggestedSwitch: SuggestedSwitch; layoutSwitch: LayoutSwitch }>,
    ): void => {
        goToDraftContext(state);

        state.linkingState = {
            type: LinkingType.LinkingLayoutSwitch,
            suggestedSwitch: suggestedSwitch,
            layoutSwitchId: layoutSwitch.id,
            suggestedSwitchName: layoutSwitch.name,
            state: 'preliminary',
            issues: [],
        };
    },
    selectOnlyLayoutSwitchForGeometrySwitchLinking: (
        state: TrackLayoutState,
        {
            payload: { layoutSwitchId },
        }: PayloadAction<{ suggestedSwitch?: SuggestedSwitch; layoutSwitchId: LayoutSwitchId }>,
    ) => {
        if (state.linkingState?.type === LinkingType.LinkingGeometrySwitch) {
            state.linkingState = validateLinkingState({
                ...state.linkingState,
                layoutSwitchId,
            });
        }
    },
    selectCandidateSwitchForGeometrySwitchLinking: (
        state: TrackLayoutState,
        {
            payload: { suggestedSwitch, layoutSwitchId },
        }: PayloadAction<{ suggestedSwitch: SuggestedSwitch; layoutSwitchId: LayoutSwitchId }>,
    ) => {
        if (state.linkingState?.type === LinkingType.LinkingGeometrySwitch) {
            state.linkingState = validateLinkingState({
                ...state.linkingState,
                suggestedSwitch,
                layoutSwitchId,
            });
        }
    },
    startKmPostLinking: (
        state: TrackLayoutState,
        { payload: geometryKmPostId }: PayloadAction<GeometryKmPostId>,
    ) => {
        const newLayoutContext = draftLayoutContext(state.layoutContext);
        state.layoutContext = newLayoutContext;
        state.layoutContextMode = inferLayoutContextMode(newLayoutContext);

        state.linkingState = {
            type: LinkingType.LinkingKmPost,
            geometryKmPostId: geometryKmPostId,
            state: 'setup',
            issues: [],
        };
    },
};

function goToDraftContext(state: TrackLayoutState): void {
    const newLayoutContext = draftLayoutContext(state.layoutContext);
    state.layoutContext = newLayoutContext;
    state.layoutContextMode = inferLayoutContextMode(newLayoutContext);
}

export const inferLayoutContextMode = (layoutContext: LayoutContext): LayoutContextMode =>
    layoutContext.branch === 'MAIN'
        ? layoutContext.publicationState === 'OFFICIAL'
            ? 'MAIN_OFFICIAL'
            : 'MAIN_DRAFT'
        : 'DESIGN';

export function createUpdatedIntervalRemovePoint(
    interval: LinkInterval,
    removePoint: LinkPoint,
): LinkInterval {
    const start =
        interval.start?.alignmentId === removePoint.alignmentId ? interval.start : undefined;
    const end = interval.end?.alignmentId === removePoint.alignmentId ? interval.end : undefined;

    if (start?.id === removePoint.id && end?.id === removePoint.id)
        return { start: undefined, end: undefined };
    else if (start?.id === removePoint.id) return { start: end, end: end };
    else if (end?.id === removePoint.id) return { start: start, end: start };
    else return { start: undefined, end: undefined };
}

export function createUpdatedInterval(
    interval: LinkInterval,
    newPoint: LinkPoint,
    toggleOn: boolean,
): LinkInterval {
    const start = interval.start?.alignmentId === newPoint.alignmentId ? interval.start : undefined;
    const end = interval.end?.alignmentId === newPoint.alignmentId ? interval.end : undefined;

    if (toggleOn && start?.id === newPoint.id && end?.id === newPoint.id)
        return { start: undefined, end: undefined };
    else if (toggleOn && start?.id === newPoint.id) return { start: end, end: end };
    else if (toggleOn && end?.id === newPoint.id) return { start: start, end: start };
    else {
        const startDiff = start ? Math.abs(start.m - newPoint.m) : -1;
        const endDiff = end ? Math.abs(end.m - newPoint.m) : -1;
        const points: LinkPoint[] = [startDiff >= endDiff ? interval.start : interval.end, newPoint]
            .filter(filterNotEmpty)
            .sort(fieldComparator((p) => p.m));
        return {
            start: points.length > 0 ? first(points) : undefined,
            end: points.length > 0 ? last(points) : undefined,
        };
    }
}

function validateLinkingState(state: LinkingState): LinkingState {
    switch (state.type) {
        case LinkingType.LinkingGeometryWithEmptyAlignment:
            return validateLinkingGeometryWithEmptyAlignment(state);
        case LinkingType.LinkingGeometryWithAlignment:
            return validateLinkingGeometryWithAlignment(state);
        case LinkingType.LinkingAlignment:
            return validateLinkingAlignment(state);
        case LinkingType.LinkingGeometrySwitch:
            return validateLinkingGeometrySwitch(state);
        case LinkingType.PlacingLayoutSwitch:
        case LinkingType.LinkingKmPost:
        case LinkingType.UnknownAlignment:
        case LinkingType.LinkingLayoutSwitch:
            return state;
        default:
            return exhaustiveMatchingGuard(state);
    }
}

function isSameLinkPoint(p1: LinkPoint, p2: LinkPoint): boolean {
    return p1.x === p2.x && p1.y === p2.y;
}

function intervalHasLength(interval: LinkInterval): boolean {
    return !!(interval.start && interval.end && !isSameLinkPoint(interval.start, interval.end));
}

function validateLinkingGeometryWithAlignment(
    state: LinkingGeometryWithAlignment,
): LinkingGeometryWithAlignment {
    const geomStart = state.geometryAlignmentInterval.start;
    const geomEnd = state.geometryAlignmentInterval.end;
    const layoutStart = state.layoutAlignmentInterval.start;
    const layoutEnd = state.layoutAlignmentInterval.end;

    const allSelected =
        state.geometryAlignmentId &&
        state.layoutAlignment.id &&
        layoutStart &&
        layoutEnd &&
        geomStart &&
        geomEnd &&
        //Allow single point selection when selected points are end points
        ((isSinglePointSelection(layoutStart, layoutEnd) &&
            intervalHasLength(state.geometryAlignmentInterval)) ||
            (intervalHasLength(state.layoutAlignmentInterval) &&
                intervalHasLength(state.geometryAlignmentInterval)));

    const errors =
        allSelected && areConnectorsTooSteep(geomStart, geomEnd, layoutStart, layoutEnd)
            ? ['error.linking.segments-sharp-angle']
            : [];
    const canLink = errors.length === 0 && allSelected;

    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
        issues: errors,
    };
}

function areConnectorsTooSteep(
    geomStart: LinkPoint,
    geomEnd: LinkPoint,
    layoutStart: LinkPoint,
    layoutEnd: LinkPoint,
): boolean {
    if (!isSinglePointSelection(layoutStart, layoutEnd)) {
        const startSteep =
            layoutStart && !layoutStart.isEndPoint && geomStart
                ? isConnectorTooSteep(layoutStart, geomStart, 'LayoutToGeometry')
                : false;
        const endSteep =
            layoutEnd && !layoutEnd.isEndPoint && geomEnd
                ? isConnectorTooSteep(layoutEnd, geomEnd, 'GeometryToLayout')
                : false;
        return startSteep || endSteep;
    } else if (layoutStart.m === 0 && layoutStart && geomEnd) {
        return isConnectorTooSteep(layoutStart, geomEnd, 'GeometryToLayout');
    } else if (layoutStart.m !== 0 && layoutEnd && geomStart) {
        return isConnectorTooSteep(layoutEnd, geomStart, 'LayoutToGeometry');
    } else {
        return false;
    }
}

function isConnectorTooSteep(
    layoutPoint: LinkPoint,
    geometryPoint: LinkPoint,
    direction: 'LayoutToGeometry' | 'GeometryToLayout',
): boolean {
    if (layoutPoint.x === geometryPoint.x && layoutPoint.y === geometryPoint.y) {
        return isSharpAngle(layoutPoint.direction, geometryPoint.direction);
    } else {
        const layoutDirection = layoutPoint.direction;
        const geometryDirection = geometryPoint.direction;
        const connectDirection =
            direction === 'LayoutToGeometry'
                ? directionBetweenPoints(layoutPoint, geometryPoint)
                : directionBetweenPoints(geometryPoint, layoutPoint);
        return (
            isSharpAngle(connectDirection, layoutDirection) ||
            isSharpAngle(connectDirection, geometryDirection)
        );
    }
}

function isSharpAngle(
    directionRads1: number | undefined,
    directionRads2: number | undefined,
): boolean {
    if (directionRads1 === undefined || directionRads2 === undefined) return false;
    else return angleDiffRads(directionRads1, directionRads2) > Math.PI / 2;
}

const isSinglePointSelection = (
    layoutStart: LinkPoint | undefined,
    layoutEnd: LinkPoint | undefined,
) => !!(layoutStart?.id === layoutEnd?.id && layoutStart?.isEndPoint);

function validateLinkingGeometryWithEmptyAlignment(
    state: LinkingGeometryWithEmptyAlignment,
): LinkingGeometryWithEmptyAlignment {
    const canLink =
        state.geometryAlignmentId &&
        state.layoutAlignment.id &&
        state.geometryAlignmentInterval.start &&
        state.geometryAlignmentInterval.end &&
        intervalHasLength(state.geometryAlignmentInterval);
    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
    };
}

function validateLinkingAlignment(state: LinkingAlignment): LinkingAlignment {
    const canLink =
        state.layoutAlignment.id &&
        state.layoutAlignmentInterval.start &&
        state.layoutAlignmentInterval.end &&
        intervalHasLength(state.layoutAlignmentInterval);
    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
    };
}

function validateLinkingGeometrySwitch(state: LinkingGeometrySwitch): LinkingGeometrySwitch {
    const canLink = state.suggestedSwitch && state.layoutSwitchId;
    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
    };
}

export function createLinkPoints(
    alignment: AlignmentTypeAndId,
    alignmentLength: number,
    segmentEndMs: number[],
    points: AlignmentPoint[],
): LinkPoint[] {
    const type = alignment.type;
    const id = alignment.id;
    return points.flatMap((point, pIdx) => {
        const linkPoints: LinkPoint[] = [];

        // Create the linkpoint from layout point
        const direction =
            pIdx === 0
                ? directionBetweenPoints(point, expectDefined(points[pIdx + 1]))
                : directionBetweenPoints(expectDefined(points[pIdx - 1]), point);
        const segmentEnd = segmentEndMs.includes(point.m);
        const alignmentEnd = point.m === 0 || point.m === alignmentLength;
        linkPoints.push(
            alignmentPointToLinkPoint(type, id, point, direction, segmentEnd, alignmentEnd),
        );

        return linkPoints;
    });
}

export function alignmentPointToLinkPoint(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    point: AlignmentPoint,
    direction: number | undefined,
    isSegmentEndPoint: boolean,
    isEndPoint: boolean,
): LinkPoint {
    return createLinkPoint(
        alignmentType,
        alignmentId,
        point.x,
        point.y,
        point.m,
        direction,
        isSegmentEndPoint,
        isEndPoint,
    );
}

function createLinkPoint(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    x: number,
    y: number,
    m: number,
    direction: number | undefined,
    isSegmentEndPoint: boolean,
    isEndPoint: boolean,
): LinkPoint {
    return {
        id: `${alignmentId}_${m}`,
        alignmentType: alignmentType,
        alignmentId: alignmentId,
        x: x,
        y: y,
        m: m,
        isSegmentEndPoint: isSegmentEndPoint,
        isEndPoint: isEndPoint,
        direction: direction,
        address: undefined,
    };
}
