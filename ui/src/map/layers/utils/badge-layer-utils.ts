import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import { Style } from 'ol/style';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import mapStyles from 'map/map.module.scss';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { findOrInterpolateXY } from 'utils/math-utils';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { filterNotEmpty, last } from 'utils/array-utils';
import { LayoutAlignmentDataHolder } from 'track-layout/layout-map-api';
import { Selection } from 'selection/selection-model';
import { LinkingState } from 'linking/linking-model';
import { getAlignmentHeaderStates } from 'map/layers/utils/alignment-layer-utils';
import { BADGE_DRAW_DISTANCES } from 'map/layers/utils/layer-visibility-limits';
import { expectCoordinate, ifDefined } from 'utils/type-utils';

type MapAlignmentBadgePoint = {
    point: number[];
    nextPoint: number[];
};

export enum AlignmentBadgeColor {
    LIGHT,
    DARK,
}

export function createBadgeFeatures(
    name: string,
    points: MapAlignmentBadgePoint[],
    color: AlignmentBadgeColor,
    contrast: boolean,
): Feature<OlPoint>[] {
    const badgeStyle = getBadgeStyle(color, contrast);

    return points.map(({ point, nextPoint }) => {
        const badgeRotation = getBadgeRotation(point, nextPoint);
        const badgeFeature = new Feature({ geometry: new OlPoint(point) });
        const badgePadding = 16;
        const badgeHeight = 14;
        const fontSize = 12;
        const badgeNoseWidth = 4;

        const renderer = (coord: Coordinate, state: State) => {
            const [x, y] = expectCoordinate(coord);
            const ctx = state.context;
            ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${
                state.pixelRatio * fontSize
            }px ${mapStyles['alignmentBadge-font-family']}`;

            ctx.save();

            const badgeWidth = ctx.measureText(name).width + badgePadding * state.pixelRatio;
            const height = badgeHeight * state.pixelRatio;
            const halfHeight = height / 2;

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

            if (badgeStyle.backgroundBorder) {
                ctx.strokeStyle = badgeStyle.backgroundBorder;
                ctx.lineWidth = state.pixelRatio;
                ctx.stroke();
            }

            ctx.fill();

            //Won't work with LIGHT badge color, but for now only reference lines have pointy badge
            if (color === AlignmentBadgeColor.DARK) {
                ctx.beginPath();

                const offsetDirection = badgeRotation.drawFromEnd ? -1 : 1;

                ctx.moveTo(
                    x + (badgeWidth + badgeNoseWidth * state.pixelRatio) * offsetDirection,
                    y,
                );
                ctx.lineTo(x + badgeWidth * offsetDirection, y - halfHeight);
                ctx.lineTo(x + badgeWidth * offsetDirection, y + halfHeight);
            }

            ctx.fill();

            ctx.fillStyle = badgeStyle.color;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(
                name,
                x + ((badgeRotation.drawFromEnd ? -1 : 1) * badgeWidth) / 2,
                y + state.pixelRatio,
            );

            ctx.restore();
        };

        badgeFeature.setStyle(
            new Style({
                zIndex: contrast ? 1 : 0,
                renderer: renderer,
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

function getBadgeRotation(startCoord: Coordinate, endCoord: Coordinate) {
    const [startX, startY] = expectCoordinate(startCoord);
    const [endX, endY] = expectCoordinate(endCoord);

    const dx = endX - startX;
    const dy = endY - startY;
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

export function getBadgePoints(
    points: AlignmentPoint[],
    drawDistance: number,
): MapAlignmentBadgePoint[] {
    const [second, last] = [points[1], points[points.length - 1]];
    if (!second || !last) return [];

    const start = Math.ceil(second.m / drawDistance);
    const end = Math.floor(last.m / drawDistance);

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
    return ifDefined(distance, last);
}

export function createAlignmentBadgeFeatures(
    alignments: LayoutAlignmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    badgeDrawDistance: number,
): Feature<OlPoint>[] {
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
