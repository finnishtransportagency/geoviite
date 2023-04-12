import {
    Coordinates,
    mToX,
    PlanLinkingSummaryItem,
} from 'vertical-geometry/vertical-geometry-diagram';
import React from 'react';

export interface PlanLinkingProps {
    coordinates: Coordinates;
    planLinkingSummary: PlanLinkingSummaryItem[];
}

export const PlanLinking: React.FC<PlanLinkingProps> = ({ coordinates, planLinkingSummary }) => {
    const textBottomYPx = 10;
    const textDropAreaPx = 3;
    return (
        <>
            {planLinkingSummary.map(({ startM, endM, filename }, i) => {
                if (endM < coordinates.startM || startM > coordinates.endM) {
                    return <React.Fragment key={i} />;
                }
                const startX = mToX(coordinates, startM);
                const textStartX = Math.max(5, mToX(coordinates, startM));
                const endX = mToX(coordinates, endM);
                return (
                    <React.Fragment key={i}>
                        <line
                            x1={startX}
                            x2={startX}
                            y1={0}
                            y2={coordinates.fullDiagramHeightPx}
                            stroke="black"
                            fill="none"
                        />
                        <line
                            x1={endX}
                            x2={endX}
                            y1={0}
                            y2={coordinates.fullDiagramHeightPx}
                            stroke="black"
                            fill="none"
                        />
                        <svg
                            x={textStartX}
                            y={0}
                            width={endX - textStartX}
                            height={textBottomYPx + textDropAreaPx}>
                            <text transform={`translate(0 ${textBottomYPx}) scale(0.7)`}>
                                {filename}
                            </text>
                        </svg>
                    </React.Fragment>
                );
            })}
        </>
    );
};
