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

enum AlignmentBadgeColor {
    LIGHT,
    DARK,
}

function createBadgeFeatures(
    name: string,
    points: MapAlignmentBadgePoint[],
    color: AlignmentBadgeColor,
    contrast: boolean,
): Feature<Point>[] {
    const badgeStyle = getBadgeStyle(color, contrast);

    return points.map(({ point, nextPoint }) => {
        const badgeRotation = getBadgeRotation(point, nextPoint);
        const badgeFeature = new Feature({ geometry: new Point(point) });
        const badgePadding = 16;
        const badgeHeight = 14;
        const fontSize = 12;
        const badgeNoseWidth = 7;

        badgeFeature.setStyle(
            () =>
                new Style({
                    zIndex: contrast ? 1 : 0,
                    renderer: ([x, y]: Coordinate, state: State) => {
                        const ctx = state.context;
                        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${
                            state.pixelRatio * fontSize
                        }px ${mapStyles['alignmentBadge-font-family']}`;
                        const badgeWidth =
                            ctx.measureText(name).width + badgePadding * state.pixelRatio;
                        const height = badgeHeight * state.pixelRatio;
                        const halfHeight = height / 2;

                        ctx.save();
                        ctx.beginPath();

                        ctx.translate(x, y);
                        ctx.rotate(badgeRotation.rotation);
                        ctx.translate(-x, -y);

                        ctx.fillStyle = badgeStyle.background;

                        ctx.rect(
                            x - (badgeRotation.drawFromEnd ? badgeWidth : 0),
                            y - halfHeight,
                            badgeWidth,
                            height,
                        );

                        ctx.fill();

                        //Won't work with LIGHT badge color, but for now only reference lines have pointy badge
                        if (color === AlignmentBadgeColor.DARK) {
                            ctx.beginPath();

                            const offsetDirection = badgeRotation.drawFromEnd ? -1 : 1;

                            ctx.moveTo(x + (badgeWidth + badgeNoseWidth) * offsetDirection, y);
                            ctx.lineTo(x + badgeWidth * offsetDirection, y - halfHeight);
                            ctx.lineTo(x + badgeWidth * offsetDirection, y + halfHeight);
                        }

                        ctx.fill();

                        if (badgeStyle.backgroundBorder) {
                            ctx.strokeStyle = badgeStyle.backgroundBorder;
                            ctx.lineWidth = 1;
                            ctx.stroke();
                        }

                        ctx.fillStyle = badgeStyle.color;
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(
                            name,
                            x + ((badgeRotation.drawFromEnd ? -1 : 1) * badgeWidth) / 2,
                            y + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        return badgeFeature;
    });
}

function getBadgeStyle(badgeColor: AlignmentBadgeColor, contrast: boolean) {
    let color = mapStyles['alignmentBadgeWhiteTextColor'];
    let background = mapStyles['alignmentBadge'];
    let backgroundBorder: string | undefined;

    if (contrast) {
        background = mapStyles['alignmentBadgeBlue'];
    } else if (badgeColor === AlignmentBadgeColor.LIGHT) {
        color = mapStyles['alignmentBadgeTextColor'];
        background = mapStyles['alignmentBadgeWhite'];
        backgroundBorder = mapStyles['alignmentBadgeBorder'];
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

        return createBadgeFeatures(
            isReferenceLine && alignment.trackNumber
                ? alignment.trackNumber.number
                : alignment.header.name,
            badgePoints,
            isReferenceLine ? AlignmentBadgeColor.DARK : AlignmentBadgeColor.LIGHT,
            selected || isLinking || highlighted,
        );
    });
}
