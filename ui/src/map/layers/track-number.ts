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

export type MapAlignmentBadgePoint = {
    point: LayoutPoint;
    nextPoint: LayoutPoint;
};

export function createMapAlignmentBadgeFeature(
    alignment: MapAlignment,
    points: MapAlignmentBadgePoint[],
    trackNumber: LayoutTrackNumber,
    lineHighlighted: boolean,
    displayMode: DisplayMode,
): Feature<Point>[] {
    //When zoomed out enough, show track number alignment badges only
    if (displayMode === DisplayMode.NUMBER && alignment.alignmentType == 'LOCATION_TRACK') {
        return [];
    }

    const badgeStyle = getMapAlignmentBadgeStyle(
        trackNumber,
        alignment,
        displayMode,
        lineHighlighted,
    );

    return points.map((numberPoint) => {
        const coordinate: Coordinate = [numberPoint.point.x, numberPoint.point.y];
        const controlCoordinate: Coordinate = [numberPoint.nextPoint.x, numberPoint.nextPoint.y];
        const badgeRotation = calculateBadgeRotation(coordinate, controlCoordinate);

        const badgeFeature = new Feature<Point>({
            geometry: new Point(coordinate),
        });

        badgeFeature.setStyle(
            () =>
                new Style({
                    zIndex: 5,
                    renderer: (coordinates: Coordinate, state: State) => {
                        const ctx = state.context;
                        ctx.font = `${mapStyles['alignment-badge-font-weight']} ${
                            state.pixelRatio * 12
                        }px ${mapStyles['alignment-badge-font-family']}`;
                        const backgroundWidth =
                            ctx.measureText(badgeStyle.text).width + 16 * state.pixelRatio;
                        const backgroundHeight = 14 * state.pixelRatio;

                        ctx.save();
                        ctx.beginPath();

                        ctx.translate(coordinates[0], coordinates[1]);
                        ctx.rotate(badgeRotation.rotation);
                        ctx.translate(-coordinates[0], -coordinates[1]);

                        ctx.fillStyle = badgeStyle.background;
                        ctx.rect(
                            coordinates[0] - (badgeRotation.drawFromEnd ? backgroundWidth : 0),
                            coordinates[1] - backgroundHeight / 2,
                            backgroundWidth,
                            backgroundHeight,
                        );

                        ctx.fill();
                        if (badgeStyle.backgroundBorder) {
                            ctx.strokeStyle = badgeStyle.backgroundBorder;
                            ctx.lineWidth = 1;
                            ctx.stroke();
                        }

                        ctx.closePath();

                        ctx.fillStyle = badgeStyle.color;
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(
                            badgeStyle.text,
                            coordinates[0] +
                                ((badgeRotation.drawFromEnd ? -1 : 1) * backgroundWidth) / 2,
                            coordinates[1] + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        badgeFeature.set('mapAlignmentBadge', trackNumber);
        return badgeFeature;
    });
}

function calculateBadgeRotation(start: Coordinate, end: Coordinate) {
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

function getMapAlignmentBadgeStyle(
    trackNumber: LayoutTrackNumber,
    alignment: MapAlignment,
    displayMode: DisplayMode,
    lineHighlighted: boolean,
) {
    let text: string;
    let color: string;
    let background: string;
    let backgroundBorder: string | undefined = undefined;

    if (displayMode === DisplayMode.NUMBER) {
        text = trackNumber.number;
        color = mapStyles['alignment-badge-color'];

        background = lineHighlighted
            ? mapStyles['alignment-badge-background-selected']
            : mapStyles['alignment-badge-background'];
    } else {
        text =
            alignment.alignmentType == 'REFERENCE_LINE'
                ? trackNumber.number
                : `${trackNumber.number} ${alignment.name}`;

        if (lineHighlighted) {
            color = mapStyles['alignment-badge-color'];
            background = mapStyles['alignment-badge-background-selected'];
        } else {
            backgroundBorder = mapStyles['alignment-badge-background-border'];
            color = mapStyles['alignment-badge-color-near'];
            background = mapStyles['alignment-badge-background-near'];
        }
    }

    return {
        text,
        color,
        background,
        backgroundBorder,
    };
}
