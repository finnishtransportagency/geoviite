import { LayoutKmPost } from 'track-layout/track-layout-model';
import mapStyles from 'map/map.module.scss';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { RenderFunction } from 'ol/style/Style';
import KmPost from 'vayla-design-lib/icon/glyphs/misc/kmpost.svg';
import {
    createPointRenderer,
    drawCircle,
    drawRect,
    drawRoundedRect,
    PointRenderFunction,
} from 'map/layers/rendering';
import { KmPostType } from 'map/layers/km-post-layer';

const kmPostImg: HTMLImageElement = new Image();
kmPostImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(KmPost)}`;

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

export function getSelectedKmPostRenderer(
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

export function getKmPostRenderer(
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
