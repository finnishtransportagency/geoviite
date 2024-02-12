import { Coordinate } from 'ol/coordinate';
import { getCoordsUnsafe } from 'utils/type-utils';

export const DASHED_LINE_INDICATOR_FONT_SIZE = 12;
export const indicatorLineWidth = (pixelRatio: number) => 120 * pixelRatio;
export const indicatorTextPadding = (pixelRatio: number) => 3 * pixelRatio;
export const indicatorLineDash = [12, 6];
export const indicatorTextBackgroundHeight = (pixelRatio: number) =>
    (DASHED_LINE_INDICATOR_FONT_SIZE + 4) * pixelRatio;
export const indicatorDashedLineWidth = (pixelRatio: number) => pixelRatio;
export const indicatorLineHeight = (pixelRatio: number) =>
    (DASHED_LINE_INDICATOR_FONT_SIZE + 3) * pixelRatio;

export function getRotation(start: Coordinate, end: Coordinate) {
    const [endX, endY] = getCoordsUnsafe(end);
    const [startX, startY] = getCoordsUnsafe(start);

    const dx = endX - startX;
    const dy = endY - startY;
    const angle = Math.atan2(dy, dx);
    const halfPi = Math.PI / 2;

    let positiveXOffset = true;
    let positiveYOffset = true;
    let rotation: number;

    if (angle >= 0) {
        positiveYOffset = false;

        if (angle >= halfPi) {
            //2nd quadrant
            rotation = -angle + halfPi;
        } else {
            //1st quadrant
            rotation = Math.abs(angle - halfPi);
        }
    } else {
        positiveXOffset = false;

        if (angle <= -halfPi) {
            //3rd quadrant
            rotation = Math.abs(angle + halfPi);
        } else {
            //4th quadrant
            rotation = -angle - halfPi;
        }
    }

    return {
        rotation,
        positiveXOffset,
        positiveYOffset,
    };
}
