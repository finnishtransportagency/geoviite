import React from 'react';
import { VerticalGeometryDiagramDisplayItem } from 'geometry/geometry-model';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';

import { Coordinates, heightToY, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import { filterNotEmpty, first, last } from 'utils/array-utils';
import { approximateHeightAtM, polylinePoints } from 'vertical-geometry/util';
import { radsToDegrees } from 'utils/math-utils';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { TrackMeter } from 'common/common-model';
import { expectDefined } from 'utils/type-utils';
import { formatRounded } from 'utils/string-utils';

export interface PviGeometryProps {
    geometry: VerticalGeometryDiagramDisplayItem[];
    kmHeights: TrackKmHeights[];
    coordinates: Coordinates;
    drawTangentArrows: boolean;
}

const minimumSpacePxForPviPointSideLabels = 14;
const minimumSpacePxForPviPointTopLabel = 16;
const minimumSpaceForTangentArrowLabel = 14;

const pviAssistLineHeightPx = 40;

const diamondHeightPx = 5;
const diamondWidthPx = 2;

function tangentArrow(
    left: boolean,
    pointStation: number,
    tangentStation: number,
    tangentHeight: number,
    tangent: number,
    pviAssistLineHeightPx: number,
    coordinates: Coordinates,
): React.JSX.Element {
    const tangentX = mToX(coordinates, tangentStation);
    const tangentBottomPx = heightToY(coordinates, tangentHeight);
    const tangentTopPx = tangentBottomPx - pviAssistLineHeightPx / 1.5;

    const arrowWidth = 3;
    const arrowHeight = 3;

    const arrowPoints = polylinePoints([
        [tangentX - arrowWidth, tangentTopPx + arrowHeight],
        [tangentX, tangentTopPx],
        [tangentX + arrowWidth, tangentTopPx + arrowHeight],
    ]);

    const hasSpaceForText =
        coordinates.mMeterLengthPxOverM * Math.abs(tangentStation - pointStation) >
        (left
            ? minimumSpaceForTangentArrowLabel
            : minimumSpaceForTangentArrowLabel + minimumSpacePxForPviPointSideLabels);

    return (
        <React.Fragment>
            {hasSpaceForText && (
                <text
                    className={styles['vertical-geometry-diagram__text-stroke-narrow']}
                    transform={`translate (${tangentX + (left ? 10 : -4)},${
                        tangentBottomPx - 2
                    }) rotate(-90) scale(0.7)`}>
                    {formatRounded(tangent, 2)}
                </text>
            )}
            <line
                x1={tangentX}
                x2={tangentX}
                y1={tangentBottomPx}
                y2={tangentTopPx}
                stroke="black"
                fill="none"
            />
            <polyline points={arrowPoints} stroke="black" fill="none" />,
        </React.Fragment>
    );
}

interface EdgeLineProps {
    coordinates: Coordinates;
    verticalGeometryItem: VerticalGeometryDiagramDisplayItem;
}

const LeftMostStartingLine: React.FC<EdgeLineProps> = ({ coordinates, verticalGeometryItem }) => {
    return (
        <line
            x1={mToX(
                coordinates,
                verticalGeometryItem.alignmentStartStation -
                    (verticalGeometryItem.linearSectionBackward.linearSegmentLength ?? 0),
            )}
            x2={mToX(coordinates, verticalGeometryItem.alignmentPointStation)}
            y1={
                heightToY(
                    coordinates,
                    verticalGeometryItem.start.height -
                        (verticalGeometryItem.linearSectionBackward.linearSegmentLength ?? 0) *
                            (verticalGeometryItem.start.angle ?? 0),
                ) - pviAssistLineHeightPx
            }
            y2={heightToY(coordinates, verticalGeometryItem.point.height) - pviAssistLineHeightPx}
            stroke="black"
            fill="none"
        />
    );
};

const RightMostEndingLine: React.FC<EdgeLineProps> = ({
    coordinates,
    verticalGeometryItem: endingVerticalGeometryItem,
}) => {
    return (
        <line
            stroke="black"
            x1={mToX(coordinates, endingVerticalGeometryItem.alignmentPointStation)}
            x2={mToX(
                coordinates,
                endingVerticalGeometryItem.alignmentEndStation +
                    (endingVerticalGeometryItem.linearSectionForward.linearSegmentLength ?? 0),
            )}
            y1={
                heightToY(coordinates, endingVerticalGeometryItem.point.height) -
                pviAssistLineHeightPx
            }
            y2={
                heightToY(
                    coordinates,
                    endingVerticalGeometryItem.end.height +
                        (endingVerticalGeometryItem.linearSectionForward.linearSegmentLength ?? 0) *
                            (endingVerticalGeometryItem.end.angle ?? 0),
                ) - pviAssistLineHeightPx
            }
        />
    );
};

const GeoLineDistanceAndAngleText: React.FC<{
    coordinates: Coordinates;
    geo: VerticalGeometryDiagramDisplayItem;
    nextGeo: VerticalGeometryDiagramDisplayItem;
}> = ({ coordinates, geo, nextGeo }) => {
    const approximateAngleTextLengthPx = 100;
    const angleTextScale = 0.7;
    const approximateAngleTextLengthM =
        (approximateAngleTextLengthPx / coordinates.mMeterLengthPxOverM) * angleTextScale;

    if (nextGeo.alignmentPointStation - geo.alignmentPointStation <= approximateAngleTextLengthM) {
        return <React.Fragment />;
    }

    const midpoint =
        geo.alignmentPointStation +
        (nextGeo.alignmentPointStation - geo.alignmentPointStation) * 0.5;
    const angleTextStartM = midpoint - approximateAngleTextLengthM * 0.5;
    const angleTextStartProportion =
        (angleTextStartM - geo.alignmentPointStation) /
        (nextGeo.alignmentPointStation - geo.alignmentPointStation);
    const angleTextStartHeight =
        (1 - angleTextStartProportion) * geo.point.height +
        angleTextStartProportion * nextGeo.point.height;
    const textStartX = mToX(coordinates, angleTextStartM);
    const textStartY = heightToY(coordinates, angleTextStartHeight) - pviAssistLineHeightPx - 2;
    const angle = radsToDegrees(
        -Math.atan2(
            coordinates.meterHeightPx * (nextGeo.point.height - geo.point.height),
            coordinates.mMeterLengthPxOverM *
                (nextGeo.alignmentPointStation - geo.alignmentPointStation),
        ),
    );

    return (
        <text
            className={styles['vertical-geometry-diagram__text-stroke-wide']}
            transform={`translate(${textStartX},${textStartY}) rotate(${angle}) scale(${angleTextScale})`}>
            {formatRounded(nextGeo.alignmentPointStation - geo.alignmentPointStation, 3)}
            {' : '}
            {geo.end.angle && formatRounded(geo.end.angle, 3)}
        </text>
    );
};

const GeoLine: React.FC<{
    coordinates: Coordinates;
    geo: VerticalGeometryDiagramDisplayItem;
    nextGeo: VerticalGeometryDiagramDisplayItem;
}> = ({ coordinates, geo, nextGeo }) => {
    return (
        <line
            x1={mToX(coordinates, geo.alignmentPointStation)}
            x2={mToX(coordinates, nextGeo.alignmentPointStation)}
            y1={heightToY(coordinates, geo.point.height) - pviAssistLineHeightPx}
            y2={heightToY(coordinates, nextGeo.point.height) - pviAssistLineHeightPx}
            stroke={'black'}
            fill="none"
        />
    );
};

interface TangentArrowProps {
    geo: VerticalGeometryDiagramDisplayItem;
    coordinates: Coordinates;
    kmHeights: TrackKmHeights[];
}

const TangentArrows: React.FC<TangentArrowProps> = ({ geo, coordinates, kmHeights }) => {
    return (
        <React.Fragment>
            <StartingTangentArrow geo={geo} coordinates={coordinates} kmHeights={kmHeights} />
            <EndingTangentArrow geo={geo} coordinates={coordinates} kmHeights={kmHeights} />
        </React.Fragment>
    );
};

const StartingTangentArrow: React.FC<TangentArrowProps> = ({ geo, coordinates, kmHeights }) => {
    if (!geo.start || !geo.point) {
        return <React.Fragment />;
    }

    return tangentArrow(
        true,
        geo.alignmentPointStation,
        geo.alignmentStartStation,
        approximateHeightAtM(geo.alignmentStartStation, kmHeights) ?? geo.start.height,
        geo.tangent,
        pviAssistLineHeightPx,
        coordinates,
    );
};

const EndingTangentArrow: React.FC<TangentArrowProps> = ({ geo, coordinates, kmHeights }) => {
    if (!geo.end || !geo.point) {
        return <React.Fragment />;
    }

    return tangentArrow(
        false,
        geo.alignmentPointStation,
        geo.alignmentEndStation,
        approximateHeightAtM(geo.alignmentEndStation, kmHeights) ?? geo.end.height,
        geo.tangent,
        pviAssistLineHeightPx,
        coordinates,
    );
};

const PointAddressText: React.FC<{
    x: number;
    y: number;
    address: TrackMeter;
}> = ({ x, y, address }) => (
    <text
        className={styles['vertical-geometry-diagram__text-stroke-wide']}
        transform={`translate(${x},${y})  rotate(-90) scale(0.7)`}>
        KM {formatTrackMeterWithoutMeters(address)}
    </text>
);

const PointHeightText: React.FC<{ x: number; y: number; height: number }> = ({ height, x, y }) => (
    <text
        className={styles['vertical-geometry-diagram__text-stroke-wide']}
        transform={`translate(${x},${y}) rotate(-90) scale(0.7)`}>
        kt={formatRounded(height, 2)}
    </text>
);

const PointRadiusText: React.FC<{ x: number; y: number; radius: number }> = ({ x, y, radius }) => (
    <text
        className={styles['vertical-geometry-diagram__text-stroke-wide']}
        transform={`translate(${x},${y}) rotate(-90) scale(0.6)`}>
        S={radius}
    </text>
);

function getMinimumSpaceAroundPoint(
    index: number,
    geometry: VerticalGeometryDiagramDisplayItem[],
    coordinates: Coordinates,
): number {
    const geo = expectDefined(geometry[index]);
    const maybePreviousGeo = index === 0 ? undefined : geometry[index - 1];
    const maybeNextGeo = index === geometry.length - 1 ? undefined : geometry[index + 1];

    return (
        Math.min(
            ...[
                maybePreviousGeo
                    ? geo.alignmentPointStation - maybePreviousGeo.alignmentPointStation
                    : undefined,

                maybeNextGeo
                    ? maybeNextGeo.alignmentPointStation - geo.alignmentPointStation
                    : undefined,
            ].filter(filterNotEmpty),
        ) * coordinates.mMeterLengthPxOverM
    );
}

const PviArrow: React.FC<{ x: number; bottomY: number; topY: number }> = ({ x, bottomY, topY }) => (
    <>
        <line x1={x} x2={x} y1={bottomY} y2={topY} stroke="black" fill="none" />
        <polyline
            points={polylinePoints([
                [x, topY],
                [x - diamondWidthPx, topY - diamondHeightPx],
                [x, topY - 2 * diamondHeightPx],
                [x + diamondWidthPx, topY - diamondHeightPx],
                [x, topY],
            ])}
            fill="black"
            stroke="black"
        />
    </>
);

const PviPoint: React.FC<{
    geometry: VerticalGeometryDiagramDisplayItem[];
    kmHeights: TrackKmHeights[];
    coordinates: Coordinates;
    drawTangentArrows: boolean;
    index: number;
}> = ({ geometry, kmHeights, coordinates, drawTangentArrows, index }) => {
    const pviPoint = expectDefined(geometry[index]);

    const x = mToX(coordinates, pviPoint.alignmentPointStation);
    // bottomY is on the height line (unless approximateHeightAt fails for whatever reason), topY is at the PVI
    // line
    const bottomHeight =
        approximateHeightAtM(pviPoint.alignmentPointStation, kmHeights) ?? pviPoint.point.height;
    const bottomY = heightToY(coordinates, bottomHeight);
    const topY = heightToY(coordinates, pviPoint.point.height) - pviAssistLineHeightPx;

    const minimumSpaceAroundPointPx = getMinimumSpaceAroundPoint(index, geometry, coordinates);

    const nextGeo = geometry[index + 1];
    return (
        <>
            {nextGeo && (
                <>
                    <GeoLineDistanceAndAngleText
                        coordinates={coordinates}
                        geo={pviPoint}
                        nextGeo={nextGeo}
                    />
                    <GeoLine coordinates={coordinates} geo={pviPoint} nextGeo={nextGeo} />,
                </>
            )}
            {drawTangentArrows && (
                <TangentArrows geo={pviPoint} coordinates={coordinates} kmHeights={kmHeights} />
            )}
            {minimumSpaceAroundPointPx > minimumSpacePxForPviPointSideLabels && (
                <>
                    {pviPoint.point.address && (
                        <PointAddressText x={x - 8} y={topY - 4} address={pviPoint.point.address} />
                    )}
                    <PointHeightText x={x + 10} y={bottomY - 4} height={pviPoint.point.height} />
                </>
            )}
            <PviArrow x={x} bottomY={bottomY} topY={topY} />
            {minimumSpaceAroundPointPx > minimumSpacePxForPviPointTopLabel && (
                <PointRadiusText
                    x={x + diamondWidthPx}
                    y={topY - diamondHeightPx - 10}
                    radius={pviPoint.radius}
                />
            )}
        </>
    );
};

export const PviGeometry: React.FC<PviGeometryProps> = ({
    geometry,
    kmHeights,
    coordinates,
    drawTangentArrows,
}) => {
    const firstItem = first(geometry);
    const lastItem = last(geometry);
    if (!firstItem) {
        return <React.Fragment />;
    }

    const leftmostPviInViewR = geometry.findIndex(
        (s) => s.alignmentEndStation >= coordinates.startM,
    );
    const leftPviI = leftmostPviInViewR < 1 ? 0 : leftmostPviInViewR - 1;
    const pastRightmostPviInViewR = geometry.findIndex(
        (s) => s.alignmentStartStation >= coordinates.endM,
    );
    const rightPviI =
        pastRightmostPviInViewR === -1 ? geometry.length - 1 : pastRightmostPviInViewR;

    return (
        <>
            {leftPviI === 0 && (
                <LeftMostStartingLine coordinates={coordinates} verticalGeometryItem={firstItem} />
            )}
            {rightPviI === geometry.length - 1 && lastItem && (
                <RightMostEndingLine coordinates={coordinates} verticalGeometryItem={lastItem} />
            )}
            {Array.from([...Array(rightPviI - leftPviI + 1)].keys()).map((index) => (
                <PviPoint
                    key={expectDefined(geometry[index + leftPviI]).id}
                    geometry={geometry}
                    kmHeights={kmHeights}
                    coordinates={coordinates}
                    drawTangentArrows={drawTangentArrows}
                    index={index + leftPviI}
                />
            ))}
        </>
    );
};
