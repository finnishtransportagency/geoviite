import { Coordinate } from 'ol/coordinate';

export function getRotation(start: Coordinate, end: Coordinate) {
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
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
