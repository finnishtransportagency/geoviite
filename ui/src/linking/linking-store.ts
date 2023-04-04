import { TrackLayoutState } from 'track-layout/track-layout-store';
import { PayloadAction } from '@reduxjs/toolkit';
import { fieldComparator, filterNotEmpty } from 'utils/array-utils';
import {
    emptyLinkInterval,
    GeometryLinkingAlignmentLockParameters,
    GeometryPreliminaryLinkingParameters,
    LinkingAlignment,
    LinkingGeometryWithAlignment,
    LinkingGeometryWithEmptyAlignment,
    LinkingState,
    LinkingSwitch,
    LinkingType,
    LinkInterval,
    LinkPoint,
    SuggestedSwitch,
} from 'linking/linking-model';
import {
    AlignmentId,
    LayoutPoint,
    LayoutSwitch,
    LayoutSwitchId,
    MapAlignment,
    MapAlignmentType,
    MapSegment,
} from 'track-layout/track-layout-model';
import { GeometryKmPostId } from 'geometry/geometry-model';
import { angleDiffRads, directionBetweenPoints } from 'utils/math-utils';
import { BoundingBox, boundingBoxContains } from 'model/geometry';

export const linkingReducers = {
    startAlignmentLinking: (
        state: TrackLayoutState,
        { payload }: PayloadAction<GeometryPreliminaryLinkingParameters>,
    ): void => {
        state.publishType = 'DRAFT';
        state.selection.selectedItems.clusterPoints = [];
        state.linkingState = {
            type: LinkingType.UnknownAlignment,
            state: 'preliminary',
            geometryPlanId: payload.geometryPlanId,
            geometryAlignmentId: payload.geometryAlignmentId,
            geometryAlignmentInterval: emptyLinkInterval,
            layoutAlignmentInterval: emptyLinkInterval,
        };
        state.map.mapLayers.forEach((layer) => {
            if (layer.type === 'manualSwitchLinking') {
                state.linkingIssuesSelectedBeforeLinking = layer.visible;
                layer.visible = false;
            } else if (layer.type === 'switchLinking') {
                state.switchLinkingSelectedBeforeLinking = layer.visible;
                layer.visible = false;
            }
        });
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
                layoutAlignmentType: payload.alignmentType,
                layoutAlignmentId: payload.alignmentId,
                type: payload.type,
                errors: [],
            };
        }
    },
    stopLinking: function(state: TrackLayoutState): void {
        state.linkingState = undefined;
        state.selection.selectedItems.clusterPoints = [];
        state.selection.selectedItems.suggestedSwitches = [];
        state.map.mapLayers.forEach((layer) => {
            if (layer.type === 'manualSwitchLinking') {
                layer.visible = state.linkingIssuesSelectedBeforeLinking;
            } else if (layer.type === 'switchLinking') {
                layer.visible = state.switchLinkingSelectedBeforeLinking;
            }
        });
    },
    setLayoutLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type == LinkingType.LinkingAlignment ||
            state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment
        ) {
            state.linkingState.layoutAlignmentInterval = createUpdatedInterval(
                state.linkingState.layoutAlignmentInterval,
                linkPoint,
                true,
            );
            state.linkingState = validateLinkingState(state.linkingState);
        }
    },
    setGeometryLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment ||
            state.linkingState?.type == LinkingType.LinkingGeometryWithEmptyAlignment
        ) {
            state.linkingState.geometryAlignmentInterval = createUpdatedInterval(
                state.linkingState.geometryAlignmentInterval,
                linkPoint,
                true,
            );
            state.linkingState = validateLinkingState(state.linkingState);
        }
    },
    setLayoutClusterLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type == LinkingType.LinkingAlignment ||
            state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment
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
    setGeometryClusterLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment ||
            state.linkingState?.type == LinkingType.LinkingGeometryWithEmptyAlignment
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
    removeGeometryLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            (state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment ||
                state.linkingState?.type == LinkingType.LinkingGeometryWithEmptyAlignment) &&
            (state.linkingState.geometryAlignmentInterval.start?.id == linkPoint.id ||
                state.linkingState.geometryAlignmentInterval.end?.id == linkPoint.id)
        ) {
            state.linkingState.geometryAlignmentInterval = createUpdatedIntervalRemovePoint(
                state.linkingState.geometryAlignmentInterval,
                linkPoint,
            );
            state.linkingState = validateLinkingState(state.linkingState);
            state.selection.selectedItems.clusterPoints = [];
        }
    },
    removeLayoutLinkPoint: function(
        state: TrackLayoutState,
        { payload: linkPoint }: PayloadAction<LinkPoint>,
    ): void {
        if (
            (state.linkingState?.type == LinkingType.LinkingAlignment ||
                state.linkingState?.type == LinkingType.LinkingGeometryWithAlignment) &&
            (state.linkingState.layoutAlignmentInterval.start?.id == linkPoint.id ||
                state.linkingState.layoutAlignmentInterval.end?.id == linkPoint.id)
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
        { payload: alignment }: PayloadAction<MapAlignment>,
    ): void => {
        state.publishType = 'DRAFT';
        state.linkingState = validateLinkingState({
            layoutAlignmentId: alignment.id,
            layoutAlignmentType: alignment.alignmentType,
            layoutAlignmentInterval: createEndLinkPoints(
                alignment.alignmentType,
                alignment.id,
                alignment.segments,
            ),
            type: LinkingType.LinkingAlignment,
            state: 'setup',
            errors: [],
        });
    },
    startSwitchPlacing: (
        state: TrackLayoutState,
        { payload: layoutSwitch }: PayloadAction<LayoutSwitch>,
    ) => {
        state.linkingState = {
            type: LinkingType.PlacingSwitch,
            layoutSwitch: layoutSwitch,
            location: undefined,
            state: 'preliminary',
            errors: [],
        };
    },
    startSwitchLinking: (
        state: TrackLayoutState,
        { payload }: PayloadAction<SuggestedSwitch>,
    ): void => {
        state.publishType = 'DRAFT';
        state.linkingState = {
            type: LinkingType.LinkingSwitch,
            suggestedSwitch: payload,
            state: 'preliminary',
            errors: [],
        };

        // Make switch linking layer visible. In future this information
        // should be calculated, not stored into the state.
        const switchLinkingLayer = state.map.mapLayers.find((layer) => layer.id == 'switchLinking');
        if (switchLinkingLayer) {
            switchLinkingLayer.visible = true;
        }

        // Ensure that layout switch is not selected by accident,
        // operator needs to take an action to select a switch
        state.selection.selectedItems.switches = [];
    },
    lockSwitchSelection: (
        state: TrackLayoutState,
        { payload: switchId }: PayloadAction<LayoutSwitchId | undefined>,
    ) => {
        if (state.linkingState?.type === LinkingType.LinkingSwitch) {
            state.linkingState = validateLinkingState({
                ...state.linkingState,
                layoutSwitchId: switchId,
            });
        }
    },
    startKmPostLinking: (
        state: TrackLayoutState,
        { payload: geometryKmPostId }: PayloadAction<GeometryKmPostId>,
    ) => {
        state.publishType = 'DRAFT';
        state.linkingState = {
            type: LinkingType.LinkingKmPost,
            geometryKmPostId: geometryKmPostId,
            state: 'setup',
            errors: [],
        };
        state.map.mapLayers.forEach((layer) => {
            if (layer.type === 'manualSwitchLinking') {
                state.linkingIssuesSelectedBeforeLinking = layer.visible;
                layer.visible = false;
            } else if (layer.type === 'switchLinking') {
                state.switchLinkingSelectedBeforeLinking = layer.visible;
                layer.visible = false;
            }
        });
    },
};

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
            start: points.length > 0 ? points[0] : undefined,
            end: points.length > 0 ? points[points.length - 1] : undefined,
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
        case LinkingType.LinkingSwitch:
            return validateLinkingSwitch(state);
        default:
            return state;
    }
}

function isSameLinkPoint(p1: LinkPoint, p2: LinkPoint): boolean {
    return p1.x == p2.x && p1.y == p2.y;
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
        state.layoutAlignmentId &&
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
        errors: errors,
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
            direction == 'LayoutToGeometry'
                ? directionBetweenPoints(layoutPoint, geometryPoint)
                : directionBetweenPoints(geometryPoint, layoutPoint);
        return (
            isSharpAngle(connectDirection, layoutDirection) ||
            isSharpAngle(connectDirection, geometryDirection)
        );
    }
}

function isSharpAngle(
    directionRads1: number | null | undefined,
    directionRads2: number | null | undefined,
): boolean {
    if (directionRads1 == null || directionRads2 == null) return false;
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
        state.layoutAlignmentId &&
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
        state.layoutAlignmentId &&
        state.layoutAlignmentInterval.start &&
        state.layoutAlignmentInterval.end &&
        intervalHasLength(state.layoutAlignmentInterval);
    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
    };
}

function validateLinkingSwitch(state: LinkingSwitch): LinkingSwitch {
    const canLink = state.suggestedSwitch && state.layoutSwitchId;
    return {
        ...state,
        state: canLink ? 'allSet' : 'setup',
    };
}

export function createEndLinkPoints(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segments: MapSegment[],
): LinkInterval {
    if (segments.length == 0) {
        return {
            start: undefined,
            end: undefined,
        };
    }
    const firstSegment = segments[0];
    const lastSegment = segments[segments.length - 1];
    return {
        start: createLinkPoint(
            alignmentType,
            alignmentId,
            firstSegment,
            0,
            firstSegment.points[0],
            directionBetweenPoints(firstSegment.points[0], firstSegment.points[1]),
            true,
        ),
        end: createLinkPoint(
            alignmentType,
            alignmentId,
            lastSegment,
            // use full segment point-count as the points might be simplified
            lastSegment.pointCount - 1,
            lastSegment.points[lastSegment.points.length - 1],
            directionBetweenPoints(
                lastSegment.points[lastSegment.points.length - 2],
                lastSegment.points[lastSegment.points.length - 1],
            ),
            true,
        ),
    };
}

export function createLinkPoints(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segments: MapSegment[],
    bounds?: BoundingBox,
    filter: ((linkPoint: LinkPoint, isSegmentEndPoint: boolean) => boolean) | undefined = undefined,
): LinkPoint[] {
    return segments.flatMap((segment, sIdx) => {
        const isFirstSegment = sIdx === 0;
        const isLastSegment = sIdx === segments.length - 1;

        return segment.points
            .flatMap((point, pIdx) => {
                const isFirstPoint = pIdx === 0;
                const isLastPoint = pIdx === segment.points.length - 1;
                const isSegmentEndPoint = isFirstPoint || isLastPoint;

                if (!isLastSegment && isLastPoint) return null;
                if (bounds != undefined && !boundingBoxContains(bounds, point)) return null;

                const direction = isLastPoint
                    ? directionBetweenPoints(segment.points[pIdx - 1], point)
                    : directionBetweenPoints(point, segment.points[pIdx + 1]);

                const linkPoint = createLinkPoint(
                    alignmentType,
                    alignmentId,
                    segment,
                    pIdx,
                    point,
                    direction,
                    (isFirstSegment && isFirstPoint) || (isLastSegment && isLastPoint),
                );

                if (filter && !filter(linkPoint, isSegmentEndPoint)) return null;
                return linkPoint;
            })
            .filter(filterNotEmpty);
    });
}

function createLinkPoint(
    alignmentType: MapAlignmentType,
    alignmentId: AlignmentId,
    segment: MapSegment,
    pointIndex: number,
    point: LayoutPoint,
    direction: number | undefined,
    isEndPoint: boolean,
): LinkPoint {
    return {
        id: `${segment.id}_${pointIndex}`,
        alignmentType: alignmentType,
        alignmentId: alignmentId,
        segmentId: segment.id,
        x: point.x,
        y: point.y,
        m: point.m,
        isSegmentEndPoint: pointIndex === 0 || pointIndex == segment.points.length - 1,
        isEndPoint: isEndPoint,
        direction: direction,
    };
}
