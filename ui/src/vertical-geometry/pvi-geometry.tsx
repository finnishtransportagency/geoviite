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

const minimumSpacePxForPviPointSideLabels = 14;
const minimumSpacePxForPviPointTopLabel = 16;
const minimumSpaceForTangentArrowLabel = 14;

const pviAssistLineHeightPx = 40;

function tangentArrow(
    left: boolean,
    pointStation: number,
    tangentStation: number,
    tangentHeight: number,
    tangent: number,
    pviAssistLineHeightPx: number,
    coordinates: Coordinates,
): JSX.Element {
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
                    {tangent.toLocaleString(undefined, { maximumFractionDigits: 2 })}
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
    if (!verticalGeometryItem.start || !verticalGeometryItem.point) {
        return <React.Fragment />;
    }

    return (
        <line
            x1={mToX(
                coordinates,
                verticalGeometryItem.start.station -
                    (verticalGeometryItem.linearSectionBackward.linearSegmentLength ?? 0),
            )}
            x2={mToX(coordinates, verticalGeometryItem.point.station)}
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
    if (!endingVerticalGeometryItem.end || !endingVerticalGeometryItem.point) {
        return <React.Fragment />;
    }

    return (
        <line
            stroke="black"
            x1={mToX(coordinates, endingVerticalGeometryItem.point.station)}
            x2={mToX(
                coordinates,
                endingVerticalGeometryItem.end.station +
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

    if (!geo.point || !geo.end || !nextGeo.point) {
        return <React.Fragment />;
    }

    if (nextGeo.point.station - geo.point.station <= approximateAngleTextLengthM) {
        return <React.Fragment />;
    }

    const midpoint = geo.point.station + (nextGeo.point.station - geo.point.station) * 0.5;
    const angleTextStartM = midpoint - approximateAngleTextLengthM * 0.5;
    const angleTextStartProportion =
        (angleTextStartM - geo.point.station) / (nextGeo.point.station - geo.point.station);
    const angleTextStartHeight =
        (1 - angleTextStartProportion) * geo.point.height +
        angleTextStartProportion * nextGeo.point.height;
    const textStartX = mToX(coordinates, angleTextStartM);
    const textStartY = heightToY(coordinates, angleTextStartHeight) - pviAssistLineHeightPx - 2;
    const angle = radsToDegrees(
        -Math.atan2(
            coordinates.meterHeightPx * (nextGeo.point.height - geo.point.height),
            coordinates.mMeterLengthPxOverM * (nextGeo.point.station - geo.point.station),
        ),
    );

    return (
        <text
            className={styles['vertical-geometry-diagram__text-stroke-wide']}
            transform={`translate(${textStartX},${textStartY}) rotate(${angle}) scale(${angleTextScale})`}>
            {(nextGeo.point.station - geo.point.station).toLocaleString(undefined, {
                maximumFractionDigits: 3,
            })}
            {' : '}
            {geo.end.angle?.toLocaleString(undefined, { maximumFractionDigits: 3 })}
        </text>
    );
};

const GeoLine: React.FC<{
    coordinates: Coordinates;
    geo: VerticalGeometryDiagramDisplayItem;
    nextGeo: VerticalGeometryDiagramDisplayItem;
}> = ({ coordinates, geo, nextGeo }) => {
    if (!geo.point || !geo.end || !nextGeo.point) {
        return <React.Fragment />;
    }

    return (
        <line
            x1={mToX(coordinates, geo.point.station)}
            x2={mToX(coordinates, nextGeo.point.station)}
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
        geo.point.station,
        geo.start.station,
        approximateHeightAtM(geo.start.station, kmHeights) ?? geo.start.height,
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
        geo.point.station,
        geo.end.station,
        approximateHeightAtM(geo.end.station, kmHeights) ?? geo.end.height,
        geo.tangent,
        pviAssistLineHeightPx,
        coordinates,
    );
};

const PointAddressText: React.FC<{
    x: number;
    y: number;
    address: TrackMeter;
}> = ({ x, y, address }) => {
    return (
        <text
            className={styles['vertical-geometry-diagram__text-stroke-wide']}
            transform={`translate(${x},${y}) rotate(-90) scale(0.7)`}>
            KM {formatTrackMeterWithoutMeters(address)}
        </text>
    );
};

const PointHeightText: React.FC<{
    x: number;
    y: number;
    height: number;
}> = ({ x, y, height }) => {
    return (
        <text
            className={styles['vertical-geometry-diagram__text-stroke-wide']}
            transform={`translate(${x},${y}) rotate(-90) scale(0.7)`}>
            kt={height.toLocaleString(undefined, { maximumFractionDigits: 2 })}
        </text>
    );
};

const PointRadiusText: React.FC<{
    x: number;
    y: number;
    radius: number;
}> = ({ x, y, radius }) => {
    return (
        <text
            className={styles['vertical-geometry-diagram__text-stroke-wide']}
            transform={`translate(${x},${y}) rotate(-90) scale(0.6)`}>
            S={radius}
        </text>
    );
};

export interface PviGeometryProps {
    geometry: VerticalGeometryDiagramDisplayItem[];
    kmHeights: TrackKmHeights[];
    coordinates: Coordinates;
    drawTangentArrows: boolean;
}

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
    const pvis: JSX.Element[] = [];
    let pviKey = 0;

    const leftmostPviInViewR = geometry.findIndex(
        (s) => s.point && s.point.station >= coordinates.startM,
    );
    const leftPviI = leftmostPviInViewR < 1 ? 0 : leftmostPviInViewR - 1;
    const pastRightmostPviInViewR = geometry.findIndex(
        (s) => s.point && s.point.station >= coordinates.endM,
    );
    const rightPviI =
        pastRightmostPviInViewR === -1 ? geometry.length - 1 : pastRightmostPviInViewR;

    if (leftPviI === 0 && firstItem.start && firstItem.point) {
        pvis.push(
            <LeftMostStartingLine
                key={pviKey++}
                coordinates={coordinates}
                verticalGeometryItem={firstItem}
            />,
        );
    }

    if (rightPviI === geometry.length - 1 && lastItem) {
        pvis.push(
            <RightMostEndingLine
                key={pviKey++}
                coordinates={coordinates}
                verticalGeometryItem={lastItem}
            />,
        );
    }

    geometry.every((geo, index) => {
        if (index === geometry.length - 1) return false;
        const nextGeo = expectDefined(geometry[index + 1]);

        if (!geo.point || !geo.end || !nextGeo.point) {
            return true;
        }

        pvis.push(
            <GeoLineDistanceAndAngleText
                key={pviKey++}
                coordinates={coordinates}
                geo={geo}
                nextGeo={nextGeo}
            />,
        );
        pvis.push(<GeoLine key={pviKey++} coordinates={coordinates} geo={geo} nextGeo={nextGeo} />);
        return true;
    });

    for (let i = leftPviI; i <= rightPviI; i++) {
        const geo = geometry[i];
        if (!geo?.point) {
            continue;
        }

        const x = mToX(coordinates, geo.point.station);
        // bottomY is on the height line (unless approximateHeightAt fails for whatever reason), topY is at the PVI
        // line
        const bottomHeight = approximateHeightAtM(geo.point.station, kmHeights) ?? geo.point.height;
        const bottomY = heightToY(coordinates, bottomHeight);
        const topY = heightToY(coordinates, geo.point.height) - pviAssistLineHeightPx;

        const diamondHeight = 5;
        const diamondWidth = 2;

        const maybePreviousGeo = i === 0 ? undefined : geometry[i - 1];
        const maybeNextGeo = i === geometry.length - 1 ? undefined : geometry[i + 1];

        const minimumSpaceAroundPointPx =
            Math.min(
                ...[
                    maybePreviousGeo && maybePreviousGeo.point
                        ? geo.point.station - maybePreviousGeo.point.station
                        : undefined,

                    maybeNextGeo && maybeNextGeo.point
                        ? maybeNextGeo.point.station - geo.point.station
                        : undefined,
                ].filter(filterNotEmpty),
            ) * coordinates.mMeterLengthPxOverM;

        if (drawTangentArrows) {
            pvis.push(
                <TangentArrows
                    key={pviKey++}
                    geo={geo}
                    coordinates={coordinates}
                    kmHeights={kmHeights}
                />,
            );
        }

        if (minimumSpaceAroundPointPx > minimumSpacePxForPviPointSideLabels) {
            if (geo.point.address) {
                pvis.push(
                    <PointAddressText
                        key={pviKey++}
                        x={x - 8}
                        y={topY - 4}
                        address={geo.point.address}
                    />,
                );
            }

            pvis.push(
                <PointHeightText
                    key={pviKey++}
                    x={x + 10}
                    y={bottomY - 4}
                    height={geo.point.height}
                />,
            );
        }

        pvis.push(
            <line key={pviKey++} x1={x} x2={x} y1={bottomY} y2={topY} stroke="black" fill="none" />,
        );

        const diamondPoints = polylinePoints([
            [x, topY],
            [x - diamondWidth, topY - diamondHeight],
            [x, topY - 2 * diamondHeight],
            [x + diamondWidth, topY - diamondHeight],
            [x, topY],
        ]);

        pvis.push(<polyline key={pviKey++} points={diamondPoints} fill="black" stroke="black" />);

        if (minimumSpaceAroundPointPx > minimumSpacePxForPviPointTopLabel) {
            pvis.push(
                <PointRadiusText
                    key={pviKey++}
                    x={x + diamondWidth}
                    y={topY - diamondHeight - 10}
                    radius={geo.radius}
                />,
            );
        }
    }

    return <>{pvis}</>;
};
