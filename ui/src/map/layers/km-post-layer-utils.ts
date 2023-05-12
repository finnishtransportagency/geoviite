import { LayoutKmPost } from 'track-layout/track-layout-model';
import mapStyles from 'map/map.module.scss';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import Style, { RenderFunction } from 'ol/style/Style';
import KmPost from 'vayla-design-lib/icon/glyphs/misc/kmpost.svg';
import {
    createPointRenderer,
    drawCircle,
    drawRect,
    drawRoundedRect,
    PointRenderFunction,
} from 'map/layers/rendering';
import { GeometryPlanId } from 'geometry/geometry-model';
import { Point } from 'model/geometry';
import { Feature } from 'ol';
import { Point as OlPoint, Polygon } from 'ol/geom';
import { pointToCoords } from 'map/layers/layer-utils';

const kmPostImg: HTMLImageElement = new Image();
kmPostImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(KmPost)}`;

export type KmPostType = 'layoutKmPost' | 'geometryKmPost';

/**
 * Steps of km post skip step
 */
const stepSteps = [1, 2, 5, 10, 20, 50, 100];

export function getStepByResolution(resolution: number): number {
    const step = Math.ceil(resolution / 10);
    return stepSteps.find((stepStep) => step <= stepStep) || 0;
}

export function createKmPostFeature(
    kmPosts: LayoutKmPost[],
    isSelected: (kmPost: LayoutKmPost) => boolean,
    kmPostType: KmPostType,
    resolution: number,
    planId?: GeometryPlanId,
    isLinked: ((kmPost: LayoutKmPost) => boolean) | undefined = undefined,
): Feature<OlPoint | Polygon>[] {
    return kmPosts
        .filter((kmPost) => kmPost.location != null)
        .flatMap((kmPost) => {
            const location = kmPost.location as Point;
            const point = new OlPoint(pointToCoords(location));
            const feature = new Feature<OlPoint>({
                geometry: point,
            });

            const selected = isSelected(kmPost);

            feature.setStyle(() => {
                return new Style({
                    zIndex: selected ? 1 : 0,
                    renderer: selected
                        ? getSelectedKmPostRenderer(
                              kmPost,
                              kmPostType,
                              isLinked && isLinked(kmPost),
                          )
                        : getKmPostRenderer(kmPost, kmPostType, isLinked && isLinked(kmPost)),
                });
            });
            feature.set('kmPost-data', {
                kmPost: kmPost,
                planId: planId,
            });

            // Create a feature to act as a clickable area
            const width = 35 * resolution;
            const height = 15 * resolution;
            const clickableX = location.x - 5 * resolution; // offset x a bit
            const polygon = new Polygon([
                [
                    [clickableX, location.y - height / 2],
                    [clickableX + width, location.y - height / 2],
                    [clickableX + width, location.y + height / 2],
                    [clickableX, location.y + height / 2],
                    [clickableX, location.y - height / 2],
                ],
            ]);
            const hitAreaFeature = new Feature<Polygon>({
                geometry: polygon,
            });
            hitAreaFeature.setStyle(undefined);
            hitAreaFeature.set('kmPost-data', {
                kmPost: kmPost,
                planId: planId,
            });

            return [feature, hitAreaFeature];
        });
}

function getRenderer(
    kmPost: LayoutKmPost,
    fontSize: number,
    drawFunctions: PointRenderFunction<LayoutKmPost>[],
) {
    return createPointRenderer<LayoutKmPost>(
        kmPost,
        (ctx: CanvasRenderingContext2D, state: State) => {
            ctx.font = `${mapStyles['kmpost-font-weight']} ${state.pixelRatio * fontSize}px ${
                mapStyles['kmpost-font-family']
            }`;
            ctx.lineWidth = state.pixelRatio;
        },
        drawFunctions,
    );
}

function getSelectedKmPostRenderer(
    kmPost: LayoutKmPost,
    kmPostType: KmPostType,
    isLinked = false,
): RenderFunction {
    const dFunctions: PointRenderFunction<LayoutKmPost>[] = [];
    const fillColor =
        kmPostType === 'layoutKmPost'
            ? mapStyles['selected-kmPost-background']
            : isLinked
            ? mapStyles['selected-linked-geometry-kmPost-background']
            : mapStyles['selected-not-linked-geometry-kmPost-background'];

    const [paddingTb, paddingRl] = [6, 6];
    const textMargin = 4;
    const iconSize = 12;
    const iconRadius = iconSize / 2;

    dFunctions.push(
        (
            _kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = mapStyles['selected-kmPost-dot-background'];
            ctx.strokeStyle = mapStyles['selected-kmPost-dot-border'];

            drawCircle(ctx, coordinates[0], coordinates[1], iconRadius * state.pixelRatio);
        },
    );

    dFunctions.push(
        (
            kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = fillColor;
            ctx.strokeStyle = mapStyles['selected-kmPost-border-color'];

            drawRoundedRect(
                ctx,
                coordinates[0] + (iconRadius + 10) * state.pixelRatio,
                coordinates[1] - (iconRadius + paddingTb) * state.pixelRatio,
                ctx.measureText(kmPost.kmNumber).width +
                    (paddingRl + iconSize + textMargin + paddingRl) * state.pixelRatio,
                (iconSize + paddingTb * 2) * state.pixelRatio,
                2 * state.pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            _kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            const x = coordinates[0] + (iconRadius + 10 + paddingRl) * state.pixelRatio;
            const y = coordinates[1] - iconRadius * state.pixelRatio;
            ctx.drawImage(
                kmPostImg,
                x,
                y,
                iconSize * state.pixelRatio,
                iconSize * state.pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = mapStyles['kmPost-text'];
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            const paddingX = iconRadius + 10 + paddingRl + iconSize + textMargin;
            const x = coordinates[0] + paddingX * state.pixelRatio;
            const y = coordinates[1] + 2 * state.pixelRatio;
            ctx.fillText(kmPost.kmNumber, x, y);
        },
    );

    return getRenderer(kmPost, 14, dFunctions);
}

function getKmPostRenderer(
    kmPost: LayoutKmPost,
    kmPostType: KmPostType,
    isLinked = false,
): RenderFunction {
    const dFunctions: PointRenderFunction<LayoutKmPost>[] = [];

    const [paddingTb, paddingRl] = [3, 4];
    const textMargin = 3;
    const iconSize = 12;
    const iconRadius = iconSize / 2;

    dFunctions.push(
        (
            kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            const textWidth = ctx.measureText(kmPost.kmNumber).width;

            if (kmPostType === 'layoutKmPost') {
                ctx.fillStyle = mapStyles['kmPost-background'];

                drawRect(
                    ctx,
                    coordinates[0] + iconRadius * state.pixelRatio,
                    coordinates[1] - (iconRadius - 1) * state.pixelRatio,
                    textWidth + paddingRl * 2 * state.pixelRatio,
                    iconSize * state.pixelRatio,
                );
            } else {
                ctx.fillStyle = isLinked
                    ? mapStyles['geometry-linked-kmPost-background']
                    : mapStyles['geometry-not-linked-kmPost-background'];

                drawRoundedRect(
                    ctx,
                    coordinates[0] - (iconRadius + paddingRl) * state.pixelRatio,
                    coordinates[1] - (iconRadius + paddingTb) * state.pixelRatio,
                    textWidth + (paddingRl + iconSize + textMargin + paddingRl) * state.pixelRatio,
                    (iconSize + paddingTb * 2) * state.pixelRatio,
                    2 * state.pixelRatio,
                );
            }
        },
    );

    dFunctions.push(
        (
            _kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            const x = coordinates[0] - iconRadius * state.pixelRatio;
            const y = coordinates[1] - iconRadius * state.pixelRatio;
            ctx.drawImage(
                kmPostImg,
                x,
                y,
                iconSize * state.pixelRatio,
                iconSize * state.pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            kmPost: LayoutKmPost,
            coordinates: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = mapStyles['kmPost-text'];
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            const x = coordinates[0] + (iconRadius + textMargin) * state.pixelRatio;
            const y = coordinates[1] + 2 * state.pixelRatio;
            ctx.fillText(kmPost.kmNumber, x, y);
        },
    );

    return getRenderer(kmPost, 10, dFunctions);
}
