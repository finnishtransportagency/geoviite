import React from 'react';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import {
    approximateHeightAtM,
    Coordinates,
    heightToY,
    mToX,
    polylinePoints,
    TrackKmHeights,
} from 'vertical-geometry/vertical-geometry-diagram';

function tangentArrow(
    left: boolean,
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

    return (
        <React.Fragment key={pviKey}>
            <line
                x1={tangentX}
                x2={tangentX}
                y1={tangentBottomPx}
                y2={tangentTopPx}
                stroke="black"
                fill="none"
            />
            <polyline points={arrowPoints} stroke="black" fill="none" />,
            <text
                transform={`translate (${tangentX + (left ? 8 : -4)},${
                    tangentBottomPx - 2
                }) rotate(-90) scale(0.7)`}>
                {tangent.toLocaleString(undefined, { maximumFractionDigits: 2 })}
            </text>
        </React.Fragment>
    );
}

export interface PviGeometryProps {
    geometry: VerticalGeometryItem[];
    kmHeights: TrackKmHeights[];
    coordinates: Coordinates;
}

const minimumPixelWidthToDrawTangents = 0.05;

export const PviGeometry: React.FC<PviGeometryProps> = ({ geometry, kmHeights, coordinates }) => {
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
            const radToDeg = 180 / Math.PI;
            const aspectRatio = coordinates.meterHeightPx / coordinates.mMeterLengthPxOverM;
            const angle =
                geo.end.angle == null ? 0 : -Math.atan(geo.end.angle) * radToDeg * aspectRatio;
            pvis.push(
                <text
                    key={pviKey++}
                    transform={`translate(${textStartX},${textStartY}) rotate(${angle}) scale(${angleTextScale})`}>
                    {(nextGeo.point.station - geo.point.station).toLocaleString(undefined, {
                        maximumFractionDigits: 3,
                    })}
                    {' : '}
                    {geo.end.angle?.toLocaleString(undefined, { maximumFractionDigits: 3 })}
                </text>,
            );
        }
    }
    for (let i = leftPviI; i <= rightPviI; i++) {
        const geo = geometry[i];
        const x = mToX(coordinates, geo.point.station);
        // bottomY is on the height line (unless approximateHeightAt fails for whatever reason), topY is at the PVI
        // line
        const bottomHeight = approximateHeightAtM(geo.point.station, kmHeights) ?? geo.point.height;
        const bottomY = heightToY(coordinates, bottomHeight);
        const topY = heightToY(coordinates, geo.point.height) - pviAssistLineHeightPx;

        pvis.push(
            <line key={pviKey++} x1={x} x2={x} y1={bottomY} y2={topY} stroke="black" fill="none" />,
        );
        const diamondHeight = 5;
        const diamondWidth = 2;
        const diamondPoints = polylinePoints([
            [x, topY],
            [x - diamondWidth, topY - diamondHeight],
            [x, topY - 2 * diamondHeight],
            [x + diamondWidth, topY - diamondHeight],
            [x, topY],
        ]);

        pvis.push(<polyline key={pviKey++} points={diamondPoints} fill="black" stroke="black" />);
        if (geo.point.address != null) {
            pvis.push(
                <text
                    key={pviKey++}
                    transform={`translate(${x - 8},${topY - 4}) rotate(-90) scale(0.7)`}>
                    KM {formatTrackMeterWithoutMeters(geo.point.address)}
                </text>,
            );
        }
        pvis.push(
            <text
                key={pviKey++}
                transform={`translate(${x + 10},${bottomY - 4}) rotate(-90) scale(0.7)`}>
                kt={geo.point.height.toLocaleString(undefined, { maximumFractionDigits: 2 })}
            </text>,
        );
        pvis.push(
            <text
                key={pviKey++}
                transform={`translate(${x + diamondWidth},${
                    topY - diamondHeight - 10
                }) rotate(-90) scale(0.6)`}>
                S={geo.radius}
            </text>,
        );
        if (
            geo.tangent !== null &&
            coordinates.mMeterLengthPxOverM > minimumPixelWidthToDrawTangents
        ) {
            pvis.push(
                tangentArrow(
                    true,
                    geo.start.station,
                    geo.start.height,
                    geo.tangent,
                    pviAssistLineHeightPx,
                    pviKey++,
                    coordinates,
                ),
            );
            pvis.push(
                tangentArrow(
                    false,
                    geo.end.station,
                    geo.end.height,
                    geo.tangent,
                    pviAssistLineHeightPx,
                    pviKey++,
                    coordinates,
                ),
            );
        }
    }
    return <>{pvis}</>;
};
