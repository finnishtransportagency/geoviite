import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { isValidPolygon, Point, Polygon, Rectangle } from 'model/geometry';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import {
    findMatchingEntities,
    getFeatureCoords,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import { fieldComparator, filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke } from 'ol/style';
import Feature, { FeatureLike } from 'ol/Feature';
import { LineString, MultiPoint, Point as OlPoint, Polygon as OlPolygon } from 'ol/geom';
import mapStyles from 'map/map.module.scss';
import CircleStyle from 'ol/style/Circle';
import { Selection } from 'selection/selection-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { getOperationalPointsByLocation } from 'track-layout/layout-operational-point-api';
import { MapTile } from 'map/map-model';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';

export const OPERATIONAL_POINT_FEATURE_DATA_PROPERTY = 'operational-point-data';

export enum OperationalPointLocationFeatureSize {
    Large,
    Medium,
    Small,
}

export type OperationalPointFeatureMode = 'DELETED' | 'HIGHLIGHTED' | 'SELECTED' | 'REGULAR';

export type OperationalPointAreaEditMode = 'ADDING' | 'MODIFYING';

const OPERATIONAL_POINT_FEATURE_SIZE_UPPER_LIMITS: {
    featureSize: OperationalPointLocationFeatureSize;
    resolutionUpperLimit: number;
}[] = [
    {
        featureSize: OperationalPointLocationFeatureSize.Large,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_LARGE,
    },
    {
        featureSize: OperationalPointLocationFeatureSize.Medium,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_MEDIUM,
    },
];

export const operationalPointStyleResolutionsSmallestFirst =
    OPERATIONAL_POINT_FEATURE_SIZE_UPPER_LIMITS.sort(
        fieldComparator((a) => a.resolutionUpperLimit),
    );

export const featureStyleRadius = (size: OperationalPointLocationFeatureSize) => {
    switch (size) {
        case OperationalPointLocationFeatureSize.Small:
            return 4;
        case OperationalPointLocationFeatureSize.Medium:
            return 5;
        case OperationalPointLocationFeatureSize.Large:
            return 6;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

export const featureStyleOffsetX = (size: OperationalPointLocationFeatureSize) => {
    switch (size) {
        case OperationalPointLocationFeatureSize.Small:
            return 10;
        case OperationalPointLocationFeatureSize.Medium:
            return 12;
        case OperationalPointLocationFeatureSize.Large:
            return 14;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

const fontString = (fontSizePx: number) => `${fontSizePx}px "Open Sans"`;

export const featureFontHeight = (
    size: OperationalPointLocationFeatureSize,
    pixelRatio: number,
) => {
    switch (size) {
        case OperationalPointLocationFeatureSize.Small:
            return 11 * pixelRatio;
        case OperationalPointLocationFeatureSize.Medium:
            return 14 * pixelRatio;
        case OperationalPointLocationFeatureSize.Large:
            return 16 * pixelRatio;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

const featureColor = (mode: OperationalPointFeatureMode) => {
    switch (mode) {
        case 'DELETED':
            return mapStyles.deletedOperationalPointColor;
        case 'HIGHLIGHTED':
        case 'SELECTED':
            return mapStyles.selectedOrHighlightedOperationalPointColor;
        case 'REGULAR':
            return mapStyles.operationalPointColor;
        default:
            return exhaustiveMatchingGuard(mode);
    }
};

export const findMatchingOperationalPoints = (
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
): OperationalPoint[] =>
    findMatchingEntities<OperationalPoint>(
        hitArea,
        source,
        OPERATIONAL_POINT_FEATURE_DATA_PROPERTY,
        options,
    );

const createOperationalPointCircleStyle = (
    featureMode: OperationalPointFeatureMode,
    size: OperationalPointLocationFeatureSize,
): Style => {
    const color = featureColor(featureMode);

    const styleArgs = {
        image: new Circle({
            radius: featureStyleRadius(size),
            stroke: new Stroke({ color: 'white', width: 2 }),
            fill: new Fill({ color }),
        }),
        fill: new Fill({
            color: 'white',
        }),
    };
    return new Style(styleArgs);
};

const createOperationalPointTextStyle = (
    operationalPointName: string,
    featureMode: OperationalPointFeatureMode,
): Style => {
    const color = featureColor(featureMode);

    const stylerooni = new Style({
        renderer: (coordinates: Coordinate, state: State) => {
            const size =
                operationalPointStyleResolutionsSmallestFirst.find(
                    (styleResolution) => state.resolution <= styleResolution.resolutionUpperLimit,
                )?.featureSize ?? OperationalPointLocationFeatureSize.Small;

            const x = (coordinates[0] ?? 0) + featureStyleOffsetX(size);
            const y = coordinates[1] ?? 0;
            console.log('renderer', coordinates);
            const ctx = state.context;
            const labelTextStroke = color;
            const fontSize = featureFontHeight(size, state.pixelRatio);
            const borderWidth = 4 * state.pixelRatio;

            ctx.strokeStyle = labelTextStroke;
            ctx.lineWidth = 1;
            ctx.textAlign = 'left';
            ctx.font = fontString(fontSize);
            const _textMeasurements = ctx.measureText(operationalPointName);

            ctx.beginPath();
            ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
            ctx.strokeStyle = 'white';
            drawRect(
                x - borderWidth,
                y - (fontSize + borderWidth * 2) / 2,
                _textMeasurements.width * state.pixelRatio + borderWidth * 2,
                fontSize + borderWidth * 2,
                ctx,
            );
            ctx.restore();

            ctx.beginPath();
            ctx.fillStyle = color;
            ctx.fillText(operationalPointName, x, y + borderWidth);
            ctx.restore();
        },
    });

    stylerooni.setHitDetectionRenderer((coordinates: Coordinate, state: State) => {
        const size =
            operationalPointStyleResolutionsSmallestFirst.find(
                (styleResolution) => state.resolution <= styleResolution.resolutionUpperLimit,
            )?.featureSize ?? OperationalPointLocationFeatureSize.Small;
        const x = (coordinates[0] ?? 0) + featureStyleOffsetX(size);
        const y = coordinates[1] ?? 0;

        console.log('hitbox', coordinates);
        const ctx = state.context;
        const fontSize = featureFontHeight(size, state.pixelRatio);
        const borderWidth = 4 * state.pixelRatio;
        console.log(state, state.pixelRatio);

        ctx.lineWidth = 1;
        ctx.textAlign = 'left';
        ctx.font = fontString(fontSize);

        ctx.beginPath();
        const _textMeasurements = ctx.measureText(operationalPointName);
        ctx.fillStyle = 'black';
        ctx.strokeStyle = 'black';
        drawRect(
            x - borderWidth,
            y - (fontSize + borderWidth * 2) / 2,
            _textMeasurements.width * state.pixelRatio + borderWidth * 2,
            fontSize + borderWidth * 2,
            ctx,
        );
        ctx.restore();
    });
    return stylerooni;
};

function getOperationalPointTextStyleForFeature(
    feature: FeatureLike,
    featureMode: OperationalPointFeatureMode,
): Style | undefined {
    const point = feature.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as OperationalPoint;

    return createOperationalPointTextStyle(point.name, featureMode);
}

const drawRect = (
    x: number,
    y: number,
    width: number,
    height: number,
    ctx: CanvasRenderingContext2D,
) => {
    ctx.beginPath();
    ctx.rect(x, y, width, height);
    ctx.fill();
    ctx.stroke();
};

function getOperationalPointCircleStyleForFeature(
    resolution: number,
    featureMode: OperationalPointFeatureMode,
): Style | undefined {
    const locationFeatureSize =
        operationalPointStyleResolutionsSmallestFirst.find(
            (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
        )?.featureSize ?? OperationalPointLocationFeatureSize.Small;

    return createOperationalPointCircleStyle(featureMode, locationFeatureSize);
}

export const renderOperationalPointCircleFeature = (
    point: OperationalPoint,
    featureMode: OperationalPointFeatureMode,
    location: Point | undefined = point.location,
): Feature<OlPoint> | undefined => {
    if (!location) {
        return undefined;
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    feature.set(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY, point);
    feature.setStyle((_, resolution) =>
        getOperationalPointCircleStyleForFeature(resolution, featureMode),
    );

    return feature;
};

export const renderOperationalPointTextFeature = (
    point: OperationalPoint,
    featureMode: OperationalPointFeatureMode,
    location: Point | undefined = point.location,
): Feature<OlPoint> | undefined => {
    if (!location) {
        return undefined;
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    feature.set(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY, point);
    feature.setStyle((feature) => getOperationalPointTextStyleForFeature(feature, featureMode));

    return feature;
};

export const renderOperationalPointAreaFeature = (
    area: Polygon,
    featureMode: OperationalPointFeatureMode,
    areaEditMode: OperationalPointAreaEditMode | undefined,
): Feature<OlPolygon> => {
    const coords = area.points.map(pointToCoords);

    const feature = new Feature({
        geometry: new OlPolygon([coords]),
    });
    feature.setStyle(operationalPointPolygonStylesFunc(featureMode, areaEditMode));
    return feature;
};

const operationalPointAreaExteriorColor = (
    editMode: OperationalPointAreaEditMode | undefined,
    featureMode: OperationalPointFeatureMode,
    isValid: boolean,
) => {
    if (editMode) {
        return isValid
            ? mapStyles.selectedOrHighlightedOperationalPointAreaExteriorColor
            : mapStyles.invalidOrDeletedOperationalPointAreaExteriorColor;
    } else {
        switch (featureMode) {
            case 'DELETED':
                return mapStyles.invalidOrDeletedOperationalPointAreaExteriorColor;
            case 'HIGHLIGHTED':
            case 'SELECTED':
                return mapStyles.selectedOrHighlightedOperationalPointAreaExteriorColor;
            case 'REGULAR':
                return mapStyles.operationalPointAreaExteriorColor;
            default:
                return exhaustiveMatchingGuard(featureMode);
        }
    }
};

const operationalPointAreaFillColor = (
    editMode: OperationalPointAreaEditMode | undefined,
    featureMode: OperationalPointFeatureMode,
    isValid: boolean,
): string => {
    if (editMode) {
        return isValid
            ? mapStyles.selectedOrHighlightedOperationalPointAreaFillColor
            : mapStyles.invalidOrDeletedOperationalPointAreaFillColor;
    } else {
        switch (featureMode) {
            case 'DELETED':
                return mapStyles.invalidOrDeletedOperationalPointAreaFillColor;
            case 'HIGHLIGHTED':
            case 'SELECTED':
                return mapStyles.selectedOrHighlightedOperationalPointAreaFillColor;
            case 'REGULAR':
                return mapStyles.operationalPointAreaFillColor;
            default:
                return exhaustiveMatchingGuard(featureMode);
        }
    }
};

const polygonLineStyle = (
    borderColor: string,
    borderWidth: number,
    drawLastLineSegment: boolean,
): Style => {
    return new Style({
        stroke: new Stroke({
            color: borderColor,
            width: borderWidth,
        }),
        geometry: function (feature: Feature<LineString>) {
            const coordinates = getFeatureCoords(feature);
            const refined = !drawLastLineSegment ? coordinates.slice(0, -1) : coordinates;
            return new LineString(refined);
        },
    });
};
const polygonPointStyle = (lineColor: string): Style =>
    new Style({
        image: new CircleStyle({
            radius: 5,
            fill: new Fill({
                color: lineColor,
            }),
        }),
        geometry: function (feature: Feature<MultiPoint>) {
            // return the coordinates of the first ring of the polygon
            const coordinates = getFeatureCoords(feature).slice(0, -1);
            return new MultiPoint(coordinates);
        },
    });

const polygonFillStyle = (fillColor: string): Style =>
    new Style({
        fill: new Fill({
            color: fillColor,
        }),
    });

type OperationalPointAreaStyleProperties = {
    exteriorColor: string;
    fillColor: string;
    borderWidth: number;
    drawLastLineSegment: boolean;
};

const operationalPointAreaStyleProps = (
    featureMode: OperationalPointFeatureMode,
    areaEditMode: OperationalPointAreaEditMode | undefined,
    isValid: boolean,
): OperationalPointAreaStyleProperties => ({
    exteriorColor: operationalPointAreaExteriorColor(areaEditMode, featureMode, isValid),
    fillColor: operationalPointAreaFillColor(areaEditMode, featureMode, isValid),
    borderWidth: areaEditMode ? 2 : 1,
    drawLastLineSegment: !areaEditMode || areaEditMode !== 'ADDING',
});

export const operationalPointPolygonStylesFunc =
    (
        featureMode: OperationalPointFeatureMode,
        areaEditMode: OperationalPointAreaEditMode | undefined,
    ) =>
    (feature: Feature<OlPolygon>): Style[] => {
        const coords = getFeatureCoords(feature);
        const isValid = isValidPolygon(coords, areaEditMode === 'ADDING');
        const styleProps = operationalPointAreaStyleProps(featureMode, areaEditMode, isValid);

        return coords.length
            ? [
                  polygonLineStyle(
                      styleProps.exteriorColor,
                      styleProps.borderWidth,
                      styleProps.drawLastLineSegment,
                  ),
                  polygonFillStyle(styleProps.fillColor),
                  areaEditMode ? polygonPointStyle(styleProps.exteriorColor) : undefined,
              ].filter(filterNotEmpty)
            : [];
    };

export const operationalPointFeatureModeBySelection = (
    operationalPointId: OperationalPointId,
    selection: Selection,
): OperationalPointFeatureMode => {
    if (selection.selectedItems.operationalPoints.includes(operationalPointId)) {
        return 'SELECTED';
    } else if (selection.highlightedItems.operationalPoints.includes(operationalPointId)) {
        return 'HIGHLIGHTED';
    } else {
        return 'REGULAR';
    }
};

export const isBeingMoved = (linkingState: LinkingState | undefined, id: OperationalPointId) =>
    linkingState &&
    linkingState.type === LinkingType.PlacingOperationalPoint &&
    linkingState.operationalPoint.id === id &&
    !!linkingState.location;

export const getOperationalPointsFromApi = async (
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    operationalPointChangeTime: TimeStamp,
) =>
    (
        await Promise.all(
            mapTiles.map((tile) =>
                getOperationalPointsByLocation(tile, layoutContext, operationalPointChangeTime),
            ),
        )
    )
        .flat()
        .filter(filterUniqueById((point) => point.id));

export const filterByResolution = (point: OperationalPoint, resolution: number) => {
    if (resolution <= Limits.OPERATIONAL_POINTS_ALL_TYPES_SHOW) return true;
    else return point.raideType === 'OLP';
};
