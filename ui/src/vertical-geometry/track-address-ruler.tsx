import React from 'react';
import { Coordinates, mToX, TrackKmHeights } from 'vertical-geometry/vertical-geometry-diagram';

export interface TrackAddressRulerProps {
    kmHeights: TrackKmHeights[];
    heightPx: number;
    coordinates: Coordinates;
}

export const TrackAddressRuler: React.FC<TrackAddressRulerProps> = ({
    kmHeights,
    heightPx,
    coordinates,
}) => {
    if (kmHeights.length === 0) {
        return <React.Fragment />;
    }
    const kmPostMarkers = kmHeights.map(({ trackMeterHeights }, kmIndex) => (
        <circle
            key={kmIndex}
            cx={mToX(coordinates, trackMeterHeights[0].m)}
            cy={heightPx}
            r={5}
            stroke={'black'}
        />
    ));

    // avoid rendering too far off the screen for performance
    const startM = coordinates.startM - 5 / coordinates.mMeterLengthPxOverM;
    const endM = coordinates.endM + 5 / coordinates.mMeterLengthPxOverM;

    const ticks = kmHeights.map((kmHeight, i) => {
        return kmHeight.trackMeterHeights
            .filter(({ m }) => m > startM && m < endM)
            .map(({ m, height }, mi) => {
                const tickX = mToX(coordinates, m);
                return (
                    <g transform={`translate(${tickX} ${heightPx})`} key={`tick_${i}_${mi}`}>
                        <line y2={-5} stroke={'black'} />
                        {height && (
                            <text transform={`translate(3 -6) scale(0.7) rotate(-90)`}>
                                {height.toLocaleString('fi', { maximumFractionDigits: 2 })}
                            </text>
                        )}
                    </g>
                );
            });
    });

    return (
        <>
            {ticks}
            {kmPostMarkers}
            <line
                x1={0}
                x2={coordinates.diagramWidthPx}
                y1={heightPx}
                y2={heightPx}
                stroke="black"
                fill="none"
                shapeRendering="crispEdges"
            />
        </>
    );
};
