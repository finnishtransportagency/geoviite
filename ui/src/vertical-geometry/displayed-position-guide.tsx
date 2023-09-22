import React from 'react';
import { Coordinates } from 'vertical-geometry/coordinates';
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
const guideRectangleMinWidthPx = 1;

export interface DisplayedPositionGuideProps {
    coordinates: Coordinates;
    maxMeters: number;
}

export const DisplayedPositionGuide: React.FC<DisplayedPositionGuideProps> = ({
    coordinates,
    maxMeters,
}) => {
    const displayedMetersAtLeftEdge = coordinates.startM;
    const displayedMetersAtRightEdge = coordinates.endM;

    const metersCurrentlyDisplayed = displayedMetersAtRightEdge - displayedMetersAtLeftEdge;
    const ratioOfCurrentlyDisplayedMetersToFullDiagram = metersCurrentlyDisplayed / maxMeters;

    const guideWidthPx = 0.15 * coordinates.diagramWidthPx;

    const guideBackgroundStartPositionPx = guideStartPx;
    const guideBackgroundWidthPx = guideWidthPx;

    const guideRectangleWidthPx = Math.max(
        guideWidthPx * ratioOfCurrentlyDisplayedMetersToFullDiagram,
        guideRectangleMinWidthPx,
    );
    const guideRectangleStartPx =
        guideStartPx + guideWidthPx * (displayedMetersAtLeftEdge / maxMeters);

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
