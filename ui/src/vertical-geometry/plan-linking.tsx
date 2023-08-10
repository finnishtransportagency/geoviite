import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { OnSelectOptions } from 'selection/selection-model';
import { filterNotEmpty } from 'utils/array-utils';

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
    const textLineOneYPx = 8;
    const textLineTwoYPx = 18;

    const textDropAreaPx = 3;
    return (
        <>
            {planLinkingSummary
                .map((summary, i) => {
                    const {
                        startM,
                        endM,
                        filename,
                        planId,
                        alignmentHeader,
                        verticalCoordinateSystem,
                    } = summary;
                    if (endM < coordinates.startM || startM > coordinates.endM) {
                        return <React.Fragment key={i} />;
                    }
                    const startX = mToX(coordinates, startM);
                    const textStartX = 2 + Math.max(3, mToX(coordinates, startM));
                    const endX = mToX(coordinates, endM);
                    const width = endX - textStartX;
                    return width >= 0 ? (
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
                                        geometryAlignmentIds: [
                                            {
                                                geometryId: alignmentHeader.id,
                                                planId: planId,
                                            },
                                        ],
                                    })
                                }
                                className={styles['vertical-geometry-diagram__plan-link']}
                                x={textStartX}
                                y={0}
                                width={width}
                                height={textLineTwoYPx + textDropAreaPx}>
                                <text transform={`translate(0 ${textLineOneYPx}) scale(0.7)`}>
                                    {filename}
                                </text>
                                <text transform={`translate(0 ${textLineTwoYPx}) scale(0.7)`}>
                                    {verticalCoordinateSystem}
                                </text>
                            </svg>
                        </React.Fragment>
                    ) : undefined;
                })
                .filter(filterNotEmpty)}
        </>
    );
};
