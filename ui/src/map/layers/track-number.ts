import { LayoutPoint, LayoutTrackNumber, MapAlignment } from 'track-layout/track-layout-model';
import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { Point } from 'ol/geom';
import { Style } from 'ol/style';
import { State } from 'ol/render';
import { Coordinate } from 'ol/coordinate';

export enum DisplayMode {
    NONE,
    NUMBER,
    NAME,
}

export type TrackNumberPoint = {
    point: LayoutPoint;
    nextPoint: LayoutPoint;
};

export function createTrackNumberFeature(
    referenceLine: MapAlignment,
    points: TrackNumberPoint[],
    trackNumber: LayoutTrackNumber,
    referenceLineHighlighted: boolean,
    displayMode: DisplayMode,
): Feature<Point>[] {
    const trackNumberStyle = getTrackNumberStyle(
        trackNumber,
        referenceLine,
        displayMode,
        referenceLineHighlighted,
    );

    return points.map((numberPoint) => {
        const trackNumberCoordinate: Coordinate = [numberPoint.point.x, numberPoint.point.y];
        const trackNumberControlCoordinate: Coordinate = [
            numberPoint.nextPoint.x,
            numberPoint.nextPoint.y,
        ];
        const labelRotation = calculateTrackNumberLabelRotation(
            trackNumberCoordinate,
            trackNumberControlCoordinate,
        );

        const trackNumberFeature = new Feature<Point>({
            geometry: new Point(trackNumberCoordinate),
        });

        trackNumberFeature.setStyle(
            () =>
                new Style({
                    zIndex: 5,
                    renderer: (coordinates: Coordinate, state: State) => {
                        const ctx = state.context;
                        ctx.font = `${mapStyles['tracknumber-font-weight']} ${
                            state.pixelRatio * 12
                        }px ${mapStyles['tracknumber-font-family']}`;
                        const backgroundWidth =
                            ctx.measureText(trackNumberStyle.text).width + 16 * state.pixelRatio;
                        const backgroundHeight = 14 * state.pixelRatio;

                        ctx.save();
                        ctx.beginPath();

                        ctx.translate(coordinates[0], coordinates[1]);
                        ctx.rotate(labelRotation.rotation);
                        ctx.translate(-coordinates[0], -coordinates[1]);

                        ctx.fillStyle = trackNumberStyle.background;
                        ctx.rect(
                            coordinates[0] - (labelRotation.drawFromEnd ? backgroundWidth : 0),
                            coordinates[1] - backgroundHeight / 2,
                            backgroundWidth,
                            backgroundHeight,
                        );

                        ctx.fill();
                        if (trackNumberStyle.backgroundBorder) {
                            ctx.strokeStyle = trackNumberStyle.backgroundBorder;
                            ctx.lineWidth = 1;
                            ctx.stroke();
                        }

                        ctx.closePath();

                        ctx.fillStyle = trackNumberStyle.color;
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(
                            trackNumberStyle.text,
                            coordinates[0] +
                                ((labelRotation.drawFromEnd ? -1 : 1) * backgroundWidth) / 2,
                            coordinates[1] + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        trackNumberFeature.set('trackNumber', trackNumber);
        return trackNumberFeature;
    });
}

function calculateTrackNumberLabelRotation(start: Coordinate, end: Coordinate) {
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
    const angle = Math.atan2(dy, dx);
    let rotation: number;
    let drawFromEnd = true;

    if (Math.abs(angle) <= Math.PI / 2) {
        // 1st and 4th quadrant
        rotation = -angle;
        drawFromEnd = false;
    } else if (angle < 0) {
        rotation = -Math.PI - angle; // 3rd quadrant
    } else {
        rotation = Math.PI - angle; // 2nd quadrant
    }

    return {
        drawFromEnd,
        rotation,
    };
}

function getTrackNumberStyle(
    trackNumber: LayoutTrackNumber,
    referenceLine: MapAlignment,
    displayMode: DisplayMode,
    referenceLineHighlighted: boolean,
) {
    let text: string;
    let color: string;
    let background: string;
    let backgroundBorder: string | undefined = undefined;

    if (displayMode === DisplayMode.NUMBER) {
        text = trackNumber.number;
        color = mapStyles['trackNumber-color'];

        background = referenceLineHighlighted
            ? mapStyles['trackNumber-background-selected']
            : mapStyles['trackNumber-background'];
    } else {
        text = `${trackNumber.number} ${referenceLine.name}`;

        if (referenceLineHighlighted) {
            color = mapStyles['trackNumber-color'];
            background = mapStyles['trackNumber-background-selected'];
        } else {
            backgroundBorder = mapStyles['trackNumber-background-border'];
            color = mapStyles['trackNumber-color-near'];
            background = mapStyles['trackNumber-background-near'];
        }
    }

    return {
        text,
        color,
        background,
        backgroundBorder,
    };
}
