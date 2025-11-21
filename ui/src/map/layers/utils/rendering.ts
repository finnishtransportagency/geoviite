import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { RenderFunction } from 'ol/style/Style';

export type ContextInitializer = (ctx: CanvasRenderingContext2D, state: State) => void;
export type PointRenderFunction<T> = (
    item: T,
    coordinates: Coordinate,
    ctx: CanvasRenderingContext2D,
    state: State,
) => void;

export function getCanvasRenderer<T>(
    item: T,
    contextInitializer: ContextInitializer,
    drawFunctions: PointRenderFunction<T>[],
): RenderFunction {
    return (coordinates: Coordinate, state: State) => {
        const ctx = state.context;
        ctx.save();

        contextInitializer(ctx, state);

        drawFunctions.forEach((drawFunction) => {
            ctx.beginPath();
            drawFunction(item, coordinates, ctx, state);
            ctx.closePath();
        });

        ctx.restore();
    };
}

export function wrapCanvasRenderer(renderFunction: RenderFunction) {
    return (coordinates: Coordinate, state: State) => {
        const ctx = state.context;
        ctx.save();
        ctx.beginPath();
        renderFunction(coordinates, state);
        ctx.closePath();
        ctx.restore();
    };
}

export function drawCircle(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    radius: number,
): void {
    ctx.arc(x, y, radius, 0, 2 * Math.PI);
    ctx.fill();
    ctx.stroke();
}

export function drawRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    height: number,
): void {
    ctx.rect(x, y, width, height);
    ctx.fill();
}

export function drawRoundedRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    height: number,
    radius: number,
): void {
    ctx.moveTo(x + radius, y);

    ctx.lineTo(x + width - radius, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + radius);

    ctx.lineTo(x + width, y + height - radius);
    ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);

    ctx.lineTo(x + radius, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - radius);

    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);

    ctx.fill();
    ctx.stroke();
}
