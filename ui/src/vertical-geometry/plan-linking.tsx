import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';

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
                const textStartX = 2 + Math.max(3, mToX(coordinates, startM));
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
                            shapeRendering="crispEdges"
                        />
                        <line
                            x1={endX}
                            x2={endX}
                            y1={0}
                            y2={coordinates.fullDiagramHeightPx}
                            stroke="black"
                            fill="none"
                            shapeRendering="crispEdges"
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
