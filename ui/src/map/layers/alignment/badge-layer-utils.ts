import Feature from 'ol/Feature';
import { Point } from 'ol/geom';
import { Style } from 'ol/style';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import mapStyles from 'map/map.module.scss';
import { LayoutPoint } from 'track-layout/track-layout-model';
import { findOrInterpolateXY } from 'utils/math-utils';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import { Selection } from 'selection/selection-model';
import { LinkingState } from 'linking/linking-model';
import { getAlignmentHeaderStates } from 'map/layers/alignment/alignment-layer-utils';
import { BADGE_DRAW_DISTANCES } from 'map/layers/utils/layer-visibility-limits';

type MapAlignmentBadgePoint = {
    point: number[];
    nextPoint: number[];
};

enum BadgeColor {
    LIGHT,
    DARK,
}

function createBadgeFeature(
    name: string,
    points: MapAlignmentBadgePoint[],
    color: BadgeColor,
    contrast: boolean,
): Feature<Point>[] {
    const badgeStyle = getBadgeStyle(color, contrast);

    return points.map(({ point, nextPoint }) => {
        const badgeRotation = getBadgeRotation(point, nextPoint);
        const badgeFeature = new Feature({ geometry: new Point(point) });

        badgeFeature.setStyle(
            () =>
                new Style({
                    renderer: (coordinates: Coordinate, state: State) => {
                        const ctx = state.context;
                        ctx.font = `${mapStyles['alignment-badge-font-weight']} ${
                            state.pixelRatio * 12
                        }px ${mapStyles['alignment-badge-font-family']}`;
                        const backgroundWidth = ctx.measureText(name).width + 16 * state.pixelRatio;
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
                            name,
                            coordinates[0] +
                                ((badgeRotation.drawFromEnd ? -1 : 1) * backgroundWidth) / 2,
                            coordinates[1] + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        return badgeFeature;
    });
}

function getBadgeStyle(badgeColor: BadgeColor, contrast: boolean) {
    let color = mapStyles['alignment-badge-color-white'];
    let background = mapStyles['alignment-badge-background'];
    let backgroundBorder: string | undefined;

    if (contrast) {
        background = mapStyles['alignment-badge-background-blue'];
    } else if (badgeColor === BadgeColor.LIGHT) {
        color = mapStyles['alignment-badge-color'];
        background = mapStyles['alignment-badge-background-white'];
        backgroundBorder = mapStyles['alignment-badge-background-border'];
    }

    return {
        color,
        background,
        backgroundBorder,
    };
}

function getBadgeRotation(start: Coordinate, end: Coordinate) {
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

function getBadgePoints(points: LayoutPoint[], drawDistance: number): MapAlignmentBadgePoint[] {
    if (points.length < 3) return [];

    const start = Math.ceil(points[1].m / drawDistance);
    const end = Math.floor(points[points.length - 1].m / drawDistance);

    if (start > end) return [];

    return Array.from({ length: 1 + end - start }, (_, i) => {
        const seek = findOrInterpolateXY(points, drawDistance * (i + start));
        if (!seek) return undefined;

        // Control point is the next one if a match was found,
        // the next real point if it was interpolated
        const controlPoint = points[seek.low === seek.high ? seek.high + 1 : seek.high];
        if (!controlPoint) return undefined;

        return {
            point: seek.point,
            nextPoint: pointToCoords(controlPoint),
        };
    }).filter(filterNotEmpty);
}

export function getBadgeDrawDistance(resolution: number): number | undefined {
    const distance = BADGE_DRAW_DISTANCES.find((d) => resolution < d[0]);
    return distance?.[1];
}

export function createAlignmentBadgeFeatures(
    alignments: AlignmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    badgeDrawDistance: number,
): Feature<Point>[] {
    return alignments.flatMap((alignment) => {
        const { selected, isLinking, highlighted } = getAlignmentHeaderStates(
            alignment,
            selection,
            linkingState,
        );

        const isReferenceLine = alignment.header.alignmentType === 'REFERENCE_LINE';
        const badgePoints = getBadgePoints(alignment.points, badgeDrawDistance);

        return createBadgeFeature(
            isReferenceLine && alignment.trackNumber
                ? alignment.trackNumber.number
                : alignment.header.name,
            badgePoints,
            isReferenceLine ? BadgeColor.DARK : BadgeColor.LIGHT,
            selected || isLinking || highlighted,
        );
    });
}
