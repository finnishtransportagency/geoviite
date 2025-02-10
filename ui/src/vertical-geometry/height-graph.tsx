import { Coordinates, heightToY, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import React from 'react';
import { polylinePoints } from 'vertical-geometry/util';

interface HeightGraphProps {
    coordinates: Coordinates;
    kmHeights: TrackKmHeights[];
}

export const HeightGraph: React.FC<HeightGraphProps> = ({ coordinates, kmHeights }) => {
    const lines: React.JSX.Element[] = [];
    let linePoints: [number, number][] = [];
    let lineIndex = 0;
    const finishLineIfStarted = () => {
        if (linePoints.length > 0) {
            lines.push(
                <polyline
                    key={lineIndex++}
                    points={polylinePoints(linePoints)}
                    stroke="black"
                    fill="none"
                    strokeWidth={2}
                />,
            );
            linePoints = [];
        }
    };
    for (const { trackMeterHeights } of kmHeights) {
        for (const { height, m } of trackMeterHeights) {
            if (height === undefined) {
                finishLineIfStarted();
            } else {
                linePoints.push([mToX(coordinates, m), heightToY(coordinates, height)]);
            }
        }
    }
    finishLineIfStarted();
    return <>{lines}</>;
};
