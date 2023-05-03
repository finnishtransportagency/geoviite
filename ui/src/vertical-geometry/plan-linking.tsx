import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { OnSelectOptions } from 'selection/selection-model';

export interface PlanLinkingProps {
    coordinates: Coordinates;
    planLinkingSummary: PlanLinkingSummaryItem[];
    onSelect: (options: OnSelectOptions) => void;
}

export const PlanLinking: React.FC<PlanLinkingProps> = ({
    coordinates,
    planLinkingSummary,
    onSelect,
}) => {
    const textBottomYPx = 10;
    const textDropAreaPx = 3;
    return (
        <>
            {planLinkingSummary.map(({ startM, endM, filename, planId, alignmentHeader }, i) => {
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
                            onClick={() =>
                                planId &&
                                alignmentHeader &&
                                onSelect({
                                    geometryAlignments: [
                                        {
                                            geometryItem: alignmentHeader,
                                            planId: planId,
                                        },
                                    ],
                                })
                            }
                            className={styles['vertical-geometry-diagram__plan-link']}
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
