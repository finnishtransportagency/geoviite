import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import {
    elevationMeasurementMethodText
} from 'geoviite-design-lib/elevation-measurement-method/elevation-measurement-method';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';

export interface PlanLinkingItemHeaderProps {
    coordinates: Coordinates;
    planLinkingSummaryItem: PlanLinkingSummaryItem;
    onSelectGeometryAlignment: (geometryId: GeometryAlignmentId, planId: GeometryPlanId) => void;
}

export const PlanLinkingHeaderItem: React.FC<PlanLinkingItemHeaderProps> = ({
    coordinates,
    planLinkingSummaryItem,
    onSelectGeometryAlignment,
}) => {
    const { t } = useTranslation();

    const textLineOneYPx = 8;
    const textLineTwoYPx = 24;

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
                    onSelectGeometryAlignment(alignmentHeader.id, planId)
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
                        {verticalCoordinateSystem === undefined ? (
                            <tspan fontWeight={'bold'} fill={'red'}>
                                {t('vertical-geometry-diagram.no-vertical-coordinate-system')}
                            </tspan>
                        ) : (
                            `${verticalCoordinateSystem}, ${elevationMeasurementMethodText(t, elevationMeasurementMethod, true, true)}`
                        )}
                    </tspan>
                </text>
            </svg>
        </React.Fragment>
    ) : (
        <React.Fragment />
    );
};
