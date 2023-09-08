import React from 'react';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import { Coordinates } from 'vertical-geometry/coordinates';
import { last } from 'utils/array-utils';
import { Translate } from 'vertical-geometry/translate';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';

const guideStartPx = 0;

const guideDividerTopPositionPx = 4;
const guideDividerBottomPositionPx = guideDividerTopPositionPx + 12;

const guideBackgroundTopPositionPx = 0;
const guideBackgroundBottomPositionPx = guideDividerBottomPositionPx + 6;

const guideTrackPositionPx = guideDividerTopPositionPx + 6;

const guideRectangleTopPositionPx = guideDividerTopPositionPx + 4;
const guideRectangleHeightPx = 4;

export interface DisplayedPositionGuideProps {
    coordinates: Coordinates;
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
}

export const DisplayedPositionGuide: React.FC<DisplayedPositionGuideProps> = ({
    planLinkingSummary,
    coordinates,
}) => {
    if (!planLinkingSummary || planLinkingSummary.length === 0) return <React.Fragment />;

    const maxDisplayedMeters = last(planLinkingSummary).endM;

    const displayedMetersAtLeftEdge = coordinates.startM;
    const displayedMetersAtRightEdge = coordinates.endM;

    const metersCurrentlyDisplayed = displayedMetersAtRightEdge - displayedMetersAtLeftEdge;
    const ratioOfCurrentlyDisplayedMetersToFullDiagram =
        metersCurrentlyDisplayed / maxDisplayedMeters;

    const guideWidthPx = 0.15 * coordinates.diagramWidthPx;

    const guideBackgroundStartPositionPx = guideStartPx;
    const guideBackgroundWidthPx = guideWidthPx;

    const guideRectangleWidthPx = guideWidthPx * ratioOfCurrentlyDisplayedMetersToFullDiagram;
    const guideRectangleStartPx =
        guideStartPx + guideWidthPx * (displayedMetersAtLeftEdge / maxDisplayedMeters);

    return (
        <Translate x={coordinates.diagramWidthPx - guideWidthPx - 40} y={0}>
            <rect
                key="displayed-position-guide-background"
                className={styles['vertical-geometry-diagram-displayed-position-guide__background']}
                x={guideBackgroundStartPositionPx}
                y={guideBackgroundTopPositionPx}
                width={guideBackgroundWidthPx}
                height={guideBackgroundBottomPositionPx}
            />
            <line
                key="displayed-position-guide-track"
                className={styles['vertical-geometry-diagram-displayed-position-guide__track']}
                x1={guideStartPx}
                x2={guideWidthPx}
                y1={guideTrackPositionPx}
                y2={guideTrackPositionPx}
            />
            <rect
                key="displayed-position-guide-rectangle"
                className={styles['vertical-geometry-diagram-displayed-position-guide__guide']}
                x={guideRectangleStartPx}
                y={guideRectangleTopPositionPx}
                width={guideRectangleWidthPx}
                height={guideRectangleHeightPx}
            />
            <line
                key="displayed-position-guide-left-divider"
                className={styles['vertical-geometry-diagram-displayed-position-guide__divider']}
                x1={guideStartPx}
                x2={guideStartPx}
                y1={guideDividerTopPositionPx}
                y2={guideDividerBottomPositionPx}
            />
            <line
                key="displayed-position-guide-right-divider"
                className={styles['vertical-geometry-diagram-displayed-position-guide__divider']}
                x1={guideWidthPx}
                x2={guideWidthPx}
                y1={guideDividerTopPositionPx}
                y2={guideDividerBottomPositionPx}
            />
        </Translate>
    );
};
