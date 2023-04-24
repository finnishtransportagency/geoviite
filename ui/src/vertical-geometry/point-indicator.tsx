import React from 'react';
import { SnappedPoint } from 'vertical-geometry/snapped-point';

export const PointIndicator: React.FC<{ point: SnappedPoint }> = ({ point }) => {
    const roundedPoint = {
        xPositionPx: Math.floor(point.xPositionPx) + 1,
        yPositionPx: Math.floor(point.yPositionPx),
    };
    const strokeWidth = 2;
    const strokeColor = '#004d99';
    const crossLength = 4;
    return (
        <>
            <line
                fill="none"
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                shapeRendering="crispEdges"
                x1={roundedPoint.xPositionPx - crossLength}
                x2={roundedPoint.xPositionPx + crossLength}
                y1={roundedPoint.yPositionPx - crossLength}
                y2={roundedPoint.yPositionPx + crossLength}
            />
            <line
                fill="none"
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                shapeRendering="crispEdges"
                x1={roundedPoint.xPositionPx - crossLength}
                x2={roundedPoint.xPositionPx + crossLength}
                y1={roundedPoint.yPositionPx + crossLength}
                y2={roundedPoint.yPositionPx - crossLength}
            />
        </>
    );
};
