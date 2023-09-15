import React from 'react';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';

import { Coordinates, heightToY, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import { filterNotEmpty } from 'utils/array-utils';
import { approximateHeightAtM, polylinePoints } from 'vertical-geometry/util';
import { radsToDegrees } from 'utils/math-utils';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';

const minimumSpacePxForPviPointSideLabels = 14;
const minimumSpacePxForPviPointTopLabel = 16;
const minimumSpaceForTangentArrowLabel = 14;

function tangentArrow(
    left: boolean,
    pointStation: number,
    tangentStation: number,
    tangentHeight: number,
    tangent: number,
    pviAssistLineHeightPx: number,
    pviKey: number,
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
        <React.Fragment key={pviKey}>
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

export interface PviGeometryProps {
    geometry: VerticalGeometryItem[];
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
    if (geometry.length == 0) {
        return <React.Fragment />;
    }
    const pvis: JSX.Element[] = [];
    const pviAssistLineHeightPx = 40;
    let pviKey = 0;
    const leftmostPviInViewR = geometry.findIndex((s) => s.point.station >= coordinates.startM);
    const leftPviI = leftmostPviInViewR < 1 ? 0 : leftmostPviInViewR - 1;
    const pastRightmostPviInViewR = geometry.findIndex((s) => s.point.station >= coordinates.endM);
    const rightPviI = pastRightmostPviInViewR == -1 ? geometry.length - 1 : pastRightmostPviInViewR;
    if (leftPviI === 0) {
        const start = geometry[0];
        pvis.push(
            <line
                key={pviKey++}
                x1={mToX(
                    coordinates,
                    start.start.station - (start.linearSectionBackward.linearSegmentLength ?? 0),
                )}
                x2={mToX(coordinates, start.point.station)}
                y1={
                    heightToY(
                        coordinates,
                        start.start.height -
                            (start.linearSectionBackward.linearSegmentLength ?? 0) *
                                (start.start.angle ?? 0),
                    ) - pviAssistLineHeightPx
                }
                y2={heightToY(coordinates, start.point.height) - pviAssistLineHeightPx}
                stroke="black"
                fill="none"
            />,
        );
    }
    if (rightPviI == geometry.length - 1) {
        const end = geometry[geometry.length - 1];
        pvis.push(
            <line
                key={pviKey++}
                x1={mToX(coordinates, end.point.station)}
                x2={mToX(
                    coordinates,
                    end.end.station + (end.linearSectionForward.linearSegmentLength ?? 0),
                )}
                y1={heightToY(coordinates, end.point.height) - pviAssistLineHeightPx}
                y2={
                    heightToY(
                        coordinates,
                        end.end.height +
                            (end.linearSectionForward.linearSegmentLength ?? 0) *
                                (end.end.angle ?? 0),
                    ) - pviAssistLineHeightPx
                }
            />,
        );
    }
    for (let i = leftPviI; i <= Math.min(rightPviI, geometry.length - 2); i++) {
        const geo = geometry[i];
        const nextGeo = geometry[i + 1];

        const approximateAngleTextLengthPx = 100;
        const angleTextScale = 0.7;
        const approximateAngleTextLengthM =
            (approximateAngleTextLengthPx / coordinates.mMeterLengthPxOverM) * angleTextScale;
        if (nextGeo.point.station - geo.point.station > approximateAngleTextLengthM) {
            const midpoint = geo.point.station + (nextGeo.point.station - geo.point.station) * 0.5;
            const angleTextStartM = midpoint - approximateAngleTextLengthM * 0.5;
            const angleTextStartProportion =
                (angleTextStartM - geo.point.station) / (nextGeo.point.station - geo.point.station);
            const angleTextStartHeight =
                (1 - angleTextStartProportion) * geo.point.height +
                angleTextStartProportion * nextGeo.point.height;
            const textStartX = mToX(coordinates, angleTextStartM);
            const textStartY =
                heightToY(coordinates, angleTextStartHeight) - pviAssistLineHeightPx - 2;
            const angle = radsToDegrees(
                -Math.atan2(
                    coordinates.meterHeightPx * (nextGeo.point.height - geo.point.height),
                    coordinates.mMeterLengthPxOverM * (nextGeo.point.station - geo.point.station),
                ),
            );
            pvis.push(
                <text
                    key={pviKey++}
                    className={styles['vertical-geometry-diagram__text-stroke-wide']}
                    transform={`translate(${textStartX},${textStartY}) rotate(${angle}) scale(${angleTextScale})`}>
                    {(nextGeo.point.station - geo.point.station).toLocaleString(undefined, {
                        maximumFractionDigits: 3,
                    })}
                    {' : '}
                    {geo.end.angle?.toLocaleString(undefined, { maximumFractionDigits: 3 })}
                </text>,
            );
        }

        pvis.push(
            <line
                key={pviKey++}
                x1={mToX(coordinates, geo.point.station)}
                x2={mToX(coordinates, nextGeo.point.station)}
                y1={heightToY(coordinates, geo.point.height) - pviAssistLineHeightPx}
                y2={heightToY(coordinates, nextGeo.point.height) - pviAssistLineHeightPx}
                stroke="black"
                fill="none"
            />,
        );
    }
    for (let i = leftPviI; i <= rightPviI; i++) {
        const geo = geometry[i];
        const x = mToX(coordinates, geo.point.station);
        // bottomY is on the height line (unless approximateHeightAt fails for whatever reason), topY is at the PVI
        // line
        const bottomHeight = approximateHeightAtM(geo.point.station, kmHeights) ?? geo.point.height;
        const bottomY = heightToY(coordinates, bottomHeight);
        const topY = heightToY(coordinates, geo.point.height) - pviAssistLineHeightPx;

        const diamondHeight = 5;
        const diamondWidth = 2;

        const minimumSpaceAroundPointPx =
            Math.min(
                ...[
                    i === 0 ? undefined : geo.point.station - geometry[i - 1].point.station,
                    i === geometry.length - 1
                        ? undefined
                        : geometry[i + 1].point.station - geo.point.station,
                ].filter(filterNotEmpty),
            ) * coordinates.mMeterLengthPxOverM;

        if (drawTangentArrows) {
            pvis.push(
                tangentArrow(
                    true,
                    geo.point.station,
                    geo.start.station,
                    approximateHeightAtM(geo.start.station, kmHeights) ?? geo.start.height,
                    geo.tangent,
                    pviAssistLineHeightPx,
                    pviKey++,
                    coordinates,
                ),
            );
            pvis.push(
                tangentArrow(
                    false,
                    geo.point.station,
                    geo.end.station,
                    approximateHeightAtM(geo.end.station, kmHeights) ?? geo.end.height,
                    geo.tangent,
                    pviAssistLineHeightPx,
                    pviKey++,
                    coordinates,
                ),
            );
        }

        if (minimumSpaceAroundPointPx > minimumSpacePxForPviPointSideLabels) {
            if (geo.point.address) {
                pvis.push(
                    <text
                        className={styles['vertical-geometry-diagram__text-stroke-wide']}
                        key={pviKey++}
                        transform={`translate(${x - 8},${topY - 4}) rotate(-90) scale(0.7)`}>
                        KM {formatTrackMeterWithoutMeters(geo.point.address)}
                    </text>,
                );
            }
            pvis.push(
                <text
                    className={styles['vertical-geometry-diagram__text-stroke-wide']}
                    key={pviKey++}
                    transform={`translate(${x + 10},${bottomY - 4}) rotate(-90) scale(0.7)`}>
                    kt={geo.point.height.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                </text>,
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
                <text
                    key={pviKey++}
                    className={styles['vertical-geometry-diagram__text-stroke-wide']}
                    transform={`translate(${x + diamondWidth},${
                        topY - diamondHeight - 10
                    }) rotate(-90) scale(0.6)`}>
                    S={geo.radius}
                </text>,
            );
        }
    }
    return <>{pvis}</>;
};
