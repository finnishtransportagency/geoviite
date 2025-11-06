import { LayoutKmPost } from 'track-layout/track-layout-model';
import mapStyles from 'map/map.module.scss';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import Style, { RenderFunction } from 'ol/style/Style';
import KmPost from 'vayla-design-lib/icon/glyphs/misc/kmpost.svg';
import DeletedKmPost from 'vayla-design-lib/icon/glyphs/misc/kmpost-deleted.svg';
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
import { Point as OlPoint, Polygon as OlPolygon } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { expectCoordinate } from 'utils/type-utils';
import { cache, Cache } from 'cache/cache';

const kmPostImg: HTMLImageElement = new Image();
kmPostImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(KmPost)}`;

const deletedKmPostImg: HTMLImageElement = new Image();
deletedKmPostImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(DeletedKmPost)}`;

export type KmPostType = 'layoutKmPost' | 'geometryKmPost';
export type KmPostIconType = 'NORMAL' | 'DELETED';

/**
 * Steps of km post skip step
 */
const kmPostSteps = [1, 2, 5, 10, 20, 50, 100];

export function getKmPostStepByResolution(resolution: number): number {
    return kmPostSteps.find((step) => step * 10 >= resolution) || 0;
}

export function createKmPostFeature(
    kmPost: LayoutKmPost,
    isSelected: (kmPost: LayoutKmPost) => boolean,
    isHighlighted: (kmPost: LayoutKmPost) => boolean,
    kmPostType: 'layoutKmPost' | 'geometryKmPost',
    isLinked: ((kmPost: LayoutKmPost) => boolean) | undefined,
    resolution: number,
    planId: string | undefined,
): [Feature<OlPoint>, Feature<OlPolygon>] {
    const location = kmPost.layoutLocation as Point;
    const feature = new Feature({ geometry: new OlPoint(pointToCoords(location)) });

    const selected = isSelected(kmPost);
    const highlighted = isHighlighted(kmPost);

    feature.setStyle(
        new Style({
            zIndex: highlighted ? 2 : selected ? 1 : 0,
            renderer:
                selected || highlighted
                    ? getSelectedKmPostRenderer(kmPost, kmPostType, isLinked && isLinked(kmPost))
                    : getKmPostRenderer(kmPost, kmPostType, isLinked && isLinked(kmPost)),
        }),
    );

    // Create a feature to act as a clickable area
    const width = 35 * resolution;
    const height = 15 * resolution;
    const clickableX = location.x - 5 * resolution; // offset x a bit
    const polygon = new OlPolygon([
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
}

export const createKmPostBadgeFeature = (
    kmPost: LayoutKmPost,
    iconType: KmPostIconType = 'NORMAL',
) => {
    const location = kmPost.layoutLocation as Point;
    const feature = new Feature({ geometry: new OlPoint(pointToCoords(location)) });

    feature.setStyle(
        new Style({
            zIndex: 2,
            renderer: getRenderer(kmPost, 14, [kmPostIconDrawFunction(6, 12, iconType)]),
        }),
    );

    return [feature];
};

export function createKmPostFeatures(
    kmPosts: LayoutKmPost[],
    isSelected: (kmPost: LayoutKmPost) => boolean,
    isHighlighted: (kmPost: LayoutKmPost) => boolean,
    kmPostType: KmPostType,
    resolution: number,
    planId?: GeometryPlanId,
    isLinked: ((kmPost: LayoutKmPost) => boolean) | undefined = undefined,
): Feature<OlPoint | Rectangle>[] {
    return kmPosts
        .filter((kmPost) => kmPost.layoutLocation)
        .flatMap((kmPost) =>
            createKmPostFeature(
                kmPost,
                isSelected,
                isHighlighted,
                kmPostType,
                isLinked,
                resolution,
                planId,
            ),
        );
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

            const [x, y] = expectCoordinate(coord);
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

            const [x, y] = expectCoordinate(coord);
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
            const [x, y] = expectCoordinate(coord);
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

            const [x, y] = expectCoordinate(coord);
            const paddingX = iconRadius + 10 + paddingRl + iconSize + textMargin;
            ctx.fillText(kmNumber, x + paddingX * state.pixelRatio, y + 2 * state.pixelRatio);
        },
    );

    return getRenderer(kmPost, 14, dFunctions);
}

const kmPostIconCache: Cache<number, ImageBitmap> = cache();

const drawKmPostBitmap = (size: number, iconType: KmPostIconType): ImageBitmap => {
    const icon = iconType === 'NORMAL' ? kmPostImg : deletedKmPostImg;
    const canvas = new OffscreenCanvas(size, size);
    canvas.getContext('2d')?.drawImage(icon, 0, 0, size, size);
    return canvas.transferToImageBitmap();
};

const getKmPostBitmap = (pixelSize: number, iconType: KmPostIconType): ImageBitmap =>
    iconType === 'NORMAL'
        ? kmPostIconCache.getOrCreate(pixelSize, () => drawKmPostBitmap(pixelSize, iconType))
        : drawKmPostBitmap(pixelSize, iconType);

const kmPostIconDrawFunction =
    (iconRadius: number, iconSize: number, iconType: KmPostIconType = 'NORMAL') =>
    (_: LayoutKmPost, coord: Coordinate, ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
        const pixelSize = iconSize * pixelRatio;

        const [x, y] = expectCoordinate(coord);
        ctx.drawImage(
            getKmPostBitmap(pixelSize, iconType),
            x - iconRadius * pixelRatio,
            y - iconRadius * pixelRatio,
        );
    };

function getKmPostRenderer(
    kmPost: LayoutKmPost,
    kmPostType: KmPostType,
    isLinked = false,
    iconType: KmPostIconType = 'NORMAL',
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
            const [x, y] = expectCoordinate(coord);

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

    dFunctions.push(kmPostIconDrawFunction(iconRadius, iconSize, iconType));

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

            const [x, y] = expectCoordinate(coord);
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
