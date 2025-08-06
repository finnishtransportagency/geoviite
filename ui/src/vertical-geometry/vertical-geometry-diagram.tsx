import * as React from 'react';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { HeightTooltip } from 'vertical-geometry/height-tooltip';
import { HeightLabels, HeightLines } from 'vertical-geometry/height-lines';
import { LabeledTicks } from 'vertical-geometry/labeled-ticks';
import { HeightGraph } from 'vertical-geometry/height-graph';
import { PviGeometry } from 'vertical-geometry/pvi-geometry';
import { Translate } from 'vertical-geometry/translate';
import { TrackAddressRuler } from 'vertical-geometry/track-address-ruler';
import { PointIndicator } from 'vertical-geometry/point-indicator';
import { createClassName } from 'vayla-design-lib/utils';
import { getSnappedPoint } from 'vertical-geometry/snapped-point';
import { Coordinates, xToM } from 'vertical-geometry/coordinates';
import { getBottomAndTopTicks, sumPaddings, zeroSafeDivision } from 'vertical-geometry/util';
import { PlanLinkingSummaryItem, TrackKmHeights } from 'geometry/geometry-api';
import { VerticalGeometryDiagramDisplayItem } from 'geometry/geometry-model';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
} from 'vertical-geometry/track-meter-index';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { BoundingBox } from 'model/geometry';
import { OnSelectOptions } from 'selection/selection-model';
import { PlanLinkingHeaders } from 'vertical-geometry/plan-linking-header';
import { DisplayedPositionGuide } from 'vertical-geometry/displayed-position-guide';

const chartBottomPadding = 60;
const topHeightPaddingPx = 180;
const bottomHeightPaddingPx = 0;
const minimumPixelWidthToDrawTangentArrows = 0.05;

type VerticalGeometryDiagramProps = {
    kmHeights: TrackKmHeights[];
    geometry: VerticalGeometryDiagramDisplayItem[];
    visibleStartM: number;
    visibleEndM: number;
    startM: number;
    endM: number;
    showArea: (area: BoundingBox) => void;
    linkingSummary: PlanLinkingSummaryItem[] | undefined;
    onSelect: (options: OnSelectOptions) => void;
    onMove: (startM: number, endM: number) => void;
    horizontalTick: number;
    width: number;
    height: number;
};

const VerticalGeometryDiagramM: React.FC<VerticalGeometryDiagramProps> = ({
    kmHeights,
    geometry,
    visibleStartM,
    visibleEndM,
    showArea,
    linkingSummary,
    onSelect,
    onMove,
    horizontalTick,
    startM,
    endM,
    width,
    height,
}) => {
    const [panning, setPanning] = React.useState<number>();
    const [mousePositionInElement, setMousePositionInElement] = React.useState<[number, number]>();
    const [diagramHeight, setDiagramHeight] = React.useState<number>(height);
    const [diagramWidth, setDiagramWidth] = React.useState<number>(width);

    const ref = React.useRef<HTMLDivElement>(null);
    const svgRef = React.useRef<SVGSVGElement>(null);

    const elementPosition = ref.current?.getBoundingClientRect();

    React.useEffect(() => {
        const elementStyle = ref.current && getComputedStyle(ref.current);

        const horizontalPadding = elementStyle
            ? sumPaddings(elementStyle.paddingLeft, elementStyle.paddingRight)
            : 0;

        const verticalPadding = elementStyle
            ? sumPaddings(elementStyle.paddingTop, elementStyle.paddingBottom)
            : 0;

        setDiagramHeight(Math.max(height - verticalPadding, 0));
        setDiagramWidth(Math.max(width - horizontalPadding, 0));
    }, [width, height]);

    const diagramClasses = createClassName(
        styles['vertical-geometry-diagram'],
        panning && styles['vertical-geometry-diagram--panning'],
    );

    const [bottomHeightTick, topHeightTick] = getBottomAndTopTicks(
        kmHeights,
        geometry,
        visibleStartM,
        visibleEndM,
    );

    const coordinates: Coordinates = {
        bottomHeightPaddingPx,
        topHeightTick,
        bottomHeightTick,
        chartHeightPx: diagramHeight - chartBottomPadding,
        meterHeightPx: zeroSafeDivision(
            diagramHeight - topHeightPaddingPx + bottomHeightPaddingPx,
            topHeightTick - bottomHeightTick,
        ),
        mMeterLengthPxOverM: zeroSafeDivision(diagramWidth, visibleEndM - visibleStartM),
        diagramWidthPx: diagramWidth,
        endM: visibleEndM,
        fullDiagramHeightPx: diagramHeight,
        startM: visibleStartM,
        horizontalTickLengthMeters: horizontalTick,
    };

    const drawTangentArrows =
        coordinates.mMeterLengthPxOverM > minimumPixelWidthToDrawTangentArrows;

    const pointerCapturePointerId = React.useRef<undefined | PointerEvent['pointerId']>(undefined);

    const onMouseMove: React.EventHandler<React.MouseEvent<unknown>> = (
        e: React.MouseEvent<SVGSVGElement>,
    ) => {
        const elementBounds = svgRef.current?.getBoundingClientRect();
        if (!elementBounds) {
            return;
        }

        setMousePositionInElement([e.clientX - elementBounds.x, e.clientY - elementBounds.y]);

        if (panning) {
            if (pointerCapturePointerId.current !== undefined) {
                ref?.current?.setPointerCapture(pointerCapturePointerId.current);
                pointerCapturePointerId.current = undefined;
            }
            const requestedPanDistance = (panning - e.clientX) / coordinates.mMeterLengthPxOverM;
            const panDistance = Math.min(
                endM - visibleEndM,
                Math.max(startM - visibleStartM, requestedPanDistance),
            );

            onMove(visibleStartM + panDistance, visibleEndM + panDistance);
            setPanning(e.clientX);
        }
    };

    const onWheel: (e: WheelEvent) => void = (e) => {
        e.preventDefault();
        const elementLeft = ref.current?.getBoundingClientRect()?.x;
        if (elementLeft === undefined) {
            return;
        }

        const focusM = (e.clientX - elementLeft) / coordinates.mMeterLengthPxOverM + visibleStartM;
        // downward should zoom out (push startM/endM further out), upward should zoom in
        // upward = negative delta
        const factor = Math.pow(1.05, e.deltaY * 0.01);
        const leftDistanceM = visibleStartM - focusM;
        const rightDistanceM = visibleEndM - focusM;
        const newStartM = Math.max(startM, visibleStartM - leftDistanceM + factor * leftDistanceM);
        const newEndM = Math.min(endM, visibleEndM - rightDistanceM + factor * rightDistanceM);

        onMove(newStartM, newEndM);
    };

    React.useEffect(() => {
        ref?.current?.addEventListener('wheel', onWheel, { passive: false });

        return () => {
            ref?.current?.removeEventListener('wheel', onWheel);
        };
    });

    const onDoubleClick: React.EventHandler<React.MouseEvent<unknown>> = (e) => {
        const elementLeft = ref.current?.getBoundingClientRect()?.x;
        if (elementLeft === undefined) {
            return;
        }

        const m = xToM(coordinates, e.clientX - elementLeft);
        const index = findTrackMeterIndexContainingM(m, kmHeights);
        if (!index) {
            return;
        }

        const [left, right] = getTrackMeterPairAroundIndex(index, kmHeights);
        const proportion = (m - left.m) / (right.m - left.m);
        const point = {
            x: (1 - proportion) * left.point.x + proportion * right.point.x,
            y: (1 - proportion) * left.point.y + proportion * right.point.y,
        };

        showArea(calculateBoundingBoxToShowAroundLocation(point));
    };

    const snap = getSnappedPoint(
        mousePositionInElement,
        kmHeights,
        geometry,
        coordinates,
        drawTangentArrows,
    );

    return (
        <div
            className={diagramClasses}
            onPointerDown={(e) => {
                pointerCapturePointerId.current = e.pointerId;
                e.preventDefault();
                setPanning(e.clientX);
            }}
            onPointerUp={(e) => {
                ref?.current?.releasePointerCapture(e.pointerId);
                setPanning(undefined);
            }}
            onPointerMove={onMouseMove}
            onPointerLeave={() => {
                setPanning(undefined);
                setMousePositionInElement(undefined);
            }}
            onDoubleClick={onDoubleClick}
            ref={ref}>
            {snap && elementPosition && (
                <HeightTooltip
                    point={snap}
                    parentElementRect={elementPosition}
                    coordinates={coordinates}
                />
            )}
            <svg height="100%" width="100%" qa-id="vertical-geometry-diagram-proper" ref={svgRef}>
                <>
                    <HeightLines coordinates={coordinates} />
                    <LabeledTicks
                        trackKmHeights={kmHeights}
                        coordinates={coordinates}
                        planLinkingSummary={linkingSummary}
                    />
                    <HeightGraph coordinates={coordinates} kmHeights={kmHeights} />
                    <PviGeometry
                        geometry={geometry}
                        kmHeights={kmHeights}
                        coordinates={coordinates}
                        drawTangentArrows={drawTangentArrows}
                    />
                    <HeightLabels coordinates={coordinates} />
                    <PlanLinkingHeaders
                        coordinates={coordinates}
                        planLinkingSummary={linkingSummary}
                        planLinkingOnSelect={onSelect}
                    />
                    <DisplayedPositionGuide coordinates={coordinates} maxMeters={endM} />
                    <Translate x={0} y={240}>
                        <TrackAddressRuler
                            kmHeights={kmHeights}
                            heightPx={40}
                            coordinates={coordinates}
                        />
                    </Translate>
                    {snap && <PointIndicator point={snap} />}
                </>
            </svg>
        </div>
    );
};

export const VerticalGeometryDiagram = React.memo(VerticalGeometryDiagramM);
