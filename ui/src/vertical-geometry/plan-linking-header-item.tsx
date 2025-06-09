import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { OnSelectOptions } from 'selection/selection-model';
import ElevationMeasurementMethod from 'geoviite-design-lib/elevation-measurement-method/elevation-measurement-method';

export interface PlanLinkingItemHeaderProps {
    coordinates: Coordinates;
    planLinkingSummaryItem: PlanLinkingSummaryItem;
    onSelect: (options: OnSelectOptions) => void;
}

export const PlanLinkingHeaderItem: React.FC<PlanLinkingItemHeaderProps> = ({
    coordinates,
    planLinkingSummaryItem,
    onSelect,
}) => {
    const textLineOneYPx = 8;
    const textLineTwoYPx = 18;

    const textDropAreaPx = 3;

    const {
        startM,
        endM,
        filename,
        planId,
        alignmentHeader,
        verticalCoordinateSystem,
        elevationMeasurementMethod,
    } = planLinkingSummaryItem;

    if (endM < coordinates.startM || startM > coordinates.endM) {
        return <React.Fragment />;
    }

    const textStartX = 2 + Math.max(3, mToX(coordinates, startM));
    const endX = mToX(coordinates, endM);
    const width = endX - textStartX;

    return width >= 0 && alignmentHeader ? (
        <React.Fragment>
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
                <text
                    className={styles['vertical-geometry-diagram__text-stroke-header']}
                    transform={`translate(0 ${textLineOneYPx}) scale(0.7)`}>
                    <tspan>{filename}</tspan>
                    <tspan x="0" dy={textLineTwoYPx}>
                        {verticalCoordinateSystem && verticalCoordinateSystem + ', '}
                        <ElevationMeasurementMethod
                            method={elevationMeasurementMethod}
                            lowerCase={verticalCoordinateSystem !== undefined}
                            includeTermContextForUnknownMethod={true}
                        />
                    </tspan>
                </text>
            </svg>
        </React.Fragment>
    ) : (
        <React.Fragment />
    );
};
