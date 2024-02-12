import { LayoutKmPost } from 'track-layout/track-layout-model';
import mapStyles from 'map/map.module.scss';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import Style, { RenderFunction } from 'ol/style/Style';
import KmPost from 'vayla-design-lib/icon/glyphs/misc/kmpost.svg';
import {
    drawCircle,
    drawRect,
    drawRoundedRect,
    getCanvasRenderer,
    PointRenderFunction,
} from 'map/layers/utils/rendering';
import { GeometryPlanId } from 'geometry/geometry-model';
import { Point, Rectangle } from 'model/geometry';
import Feature from 'ol/Feature';
import { Point as OlPoint, Polygon } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { getCoordsUnsafe } from 'utils/type-utils';

const kmPostImg: HTMLImageElement = new Image();
kmPostImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(KmPost)}`;

export type KmPostType = 'layoutKmPost' | 'geometryKmPost';

/**
 * Steps of km post skip step
 */
const kmPostSteps = [1, 2, 5, 10, 20, 50, 100];
export function getKmPostStepByResolution(resolution: number): number {
    return kmPostSteps.find((step) => step * 10 >= resolution) || 0;
}

export function createKmPostFeatures(
    kmPosts: LayoutKmPost[],
    isSelected: (kmPost: LayoutKmPost) => boolean,
    kmPostType: KmPostType,
    resolution: number,
    planId?: GeometryPlanId,
    isLinked: ((kmPost: LayoutKmPost) => boolean) | undefined = undefined,
): Feature<OlPoint | Rectangle>[] {
    return kmPosts
        .filter((kmPost) => kmPost.location)
        .flatMap((kmPost) => {
            const location = kmPost.location as Point;
            const feature = new Feature({ geometry: new OlPoint(pointToCoords(location)) });

            const selected = isSelected(kmPost);

            feature.setStyle(
                new Style({
                    zIndex: selected ? 1 : 0,
                    renderer: selected
                        ? getSelectedKmPostRenderer(
                              kmPost,
                              kmPostType,
                              isLinked && isLinked(kmPost),
                          )
                        : getKmPostRenderer(kmPost, kmPostType, isLinked && isLinked(kmPost)),
                }),
            );

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
            const hitAreaFeature = new Feature({ geometry: polygon });
            setKmPostFeatureProperty(hitAreaFeature, { kmPost, planId });

            return [feature, hitAreaFeature];
        });
}

function getRenderer(
    kmPost: LayoutKmPost,
    fontSize: number,
    drawFunctions: PointRenderFunction<LayoutKmPost>[],
): RenderFunction {
    return getCanvasRenderer<LayoutKmPost>(
        kmPost,
        (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
            ctx.font = `${mapStyles['kmPost-font-weight']} ${pixelRatio * fontSize}px ${
                mapStyles['kmPost-font-family']
            }`;
            ctx.lineWidth = pixelRatio;
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
            ? mapStyles['selectedKmPostLabel']
            : isLinked
            ? mapStyles['selectedLinkedKmPostLabel']
            : mapStyles['selectedUnlinkedKmPostLabel'];

    const [paddingTb, paddingRl] = [6, 6];
    const textMargin = 4;
    const iconSize = 12;
    const iconRadius = iconSize / 2;

    dFunctions.push(
        (
            _: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            { pixelRatio }: State,
        ) => {
            ctx.fillStyle = mapStyles['selectedKmPostDot'];
            ctx.strokeStyle = mapStyles['selectedKmPostDotBorder'];

            const [x, y] = getCoordsUnsafe(coord);
            drawCircle(ctx, x, y, iconRadius * pixelRatio);
        },
    );

    dFunctions.push(
        (
            { kmNumber }: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            { pixelRatio }: State,
        ) => {
            ctx.fillStyle = fillColor;
            ctx.strokeStyle = mapStyles['selectedKmPostLabelBorder'];

            const [x, y] = getCoordsUnsafe(coord);
            drawRoundedRect(
                ctx,
                x + (iconRadius + 10) * pixelRatio,
                y - (iconRadius + paddingTb) * pixelRatio,
                ctx.measureText(kmNumber).width +
                    (paddingRl + iconSize + textMargin + paddingRl) * pixelRatio,
                (iconSize + paddingTb * 2) * pixelRatio,
                2 * pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            _: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            { pixelRatio }: State,
        ) => {
            const [x, y] = getCoordsUnsafe(coord);
            ctx.drawImage(
                kmPostImg,
                x + (iconRadius + 10 + paddingRl) * pixelRatio,
                y - iconRadius * pixelRatio,
                iconSize * pixelRatio,
                iconSize * pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            { kmNumber }: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = mapStyles['kmPostTextColor'];
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            const [x, y] = getCoordsUnsafe(coord);
            const paddingX = iconRadius + 10 + paddingRl + iconSize + textMargin;
            ctx.fillText(kmNumber, x + paddingX * state.pixelRatio, y + 2 * state.pixelRatio);
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
            { kmNumber }: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            { pixelRatio }: State,
        ) => {
            const textWidth = ctx.measureText(kmNumber).width;
            const [x, y] = getCoordsUnsafe(coord);

            if (kmPostType === 'layoutKmPost') {
                ctx.fillStyle = mapStyles['kmPostLabel'];

                drawRect(
                    ctx,
                    x + iconRadius * pixelRatio,
                    y - (iconRadius - 1) * pixelRatio,
                    textWidth + paddingRl * 2 * pixelRatio,
                    iconSize * pixelRatio,
                );
            } else {
                ctx.fillStyle = isLinked
                    ? mapStyles['linkedKmPostLabel']
                    : mapStyles['unlinkedKmPostLabel'];

                drawRoundedRect(
                    ctx,
                    x - (iconRadius + paddingRl) * pixelRatio,
                    y - (iconRadius + paddingTb) * pixelRatio,
                    textWidth + (paddingRl + iconSize + textMargin + paddingRl) * pixelRatio,
                    (iconSize + paddingTb * 2) * pixelRatio,
                    2 * pixelRatio,
                );
            }
        },
    );

    dFunctions.push(
        (
            _: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            { pixelRatio }: State,
        ) => {
            const [x, y] = getCoordsUnsafe(coord);
            ctx.drawImage(
                kmPostImg,
                x - iconRadius * pixelRatio,
                y - iconRadius * pixelRatio,
                iconSize * pixelRatio,
                iconSize * pixelRatio,
            );
        },
    );

    dFunctions.push(
        (
            { kmNumber }: LayoutKmPost,
            coord: Coordinate,
            ctx: CanvasRenderingContext2D,
            state: State,
        ) => {
            ctx.fillStyle = mapStyles['kmPostTextColor'];
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            const [x, y] = getCoordsUnsafe(coord);
            ctx.fillText(
                kmNumber,
                x + (iconRadius + textMargin) * state.pixelRatio,
                y + 2 * state.pixelRatio,
            );
        },
    );

    return getRenderer(kmPost, 10, dFunctions);
}

export function findMatchingKmPosts(
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
): KmPostFeatureProperty[] {
    return findMatchingEntities<KmPostFeatureProperty>(
        hitArea,
        source,
        KM_POST_FEATURE_DATA_PROPERTY,
        options,
    );
}

type KmPostFeatureProperty = {
    kmPost: LayoutKmPost;
    planId?: GeometryPlanId;
};

const KM_POST_FEATURE_DATA_PROPERTY = 'km-post-data';

function setKmPostFeatureProperty(feature: Feature<Rectangle>, data: KmPostFeatureProperty) {
    feature.set(KM_POST_FEATURE_DATA_PROPERTY, data);
}
