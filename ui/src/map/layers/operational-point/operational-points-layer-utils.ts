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
import { fieldComparator, filterNotEmpty } from 'utils/array-utils';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke, Text } from 'ol/style';
import Feature, { FeatureLike } from 'ol/Feature';
import { LineString, MultiPoint, Point as OlPoint, Polygon as OlPolygon } from 'ol/geom';
import mapStyles from 'map/map.module.scss';
import CircleStyle from 'ol/style/Circle';
import { Selection } from 'selection/selection-model';

export const OPERATIONAL_POINT_FEATURE_DATA_PROPERTY = 'operational-point-data';

export enum OperationalPointCircleFeatureSize {
    Large,
    Medium,
    Small,
}

export type OperationalPointFeatureMode = 'DELETED' | 'HIGHLIGHTED' | 'SELECTED' | 'REGULAR';

export type OperationalPointAreaEditMode = 'ADDING' | 'MODIFYING';

const OPERATIONAL_POINT_FEATURE_SIZE_LIMITS: {
    style: OperationalPointCircleFeatureSize;
    resolutionUpperLimit: number;
}[] = [
    {
        style: OperationalPointCircleFeatureSize.Large,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_LARGE,
    },
    {
        style: OperationalPointCircleFeatureSize.Medium,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_MEDIUM,
    },
    {
        style: OperationalPointCircleFeatureSize.Small,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_SMALL,
    },
];

export const operationalPointStyleResolutionsSmallestFirst =
    OPERATIONAL_POINT_FEATURE_SIZE_LIMITS.sort(fieldComparator((a) => a.resolutionUpperLimit));

export const featureStyleRadius = (size: OperationalPointCircleFeatureSize) => {
    switch (size) {
        case OperationalPointCircleFeatureSize.Small:
            return 4;
        case OperationalPointCircleFeatureSize.Medium:
            return 5;
        case OperationalPointCircleFeatureSize.Large:
            return 6;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

export const featureStyleOffsetX = (size: OperationalPointCircleFeatureSize) => {
    switch (size) {
        case OperationalPointCircleFeatureSize.Small:
            return 10;
        case OperationalPointCircleFeatureSize.Medium:
            return 12;
        case OperationalPointCircleFeatureSize.Large:
            return 14;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

const fontString = (fontSizePx: number) => `${fontSizePx}px "Open Sans"`;
export const featureStyleFont = (size: OperationalPointCircleFeatureSize) => {
    switch (size) {
        case OperationalPointCircleFeatureSize.Small:
            return fontString(11);
        case OperationalPointCircleFeatureSize.Medium:
            return fontString(14);
        case OperationalPointCircleFeatureSize.Large:
            return fontString(16);
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
    operationalPointName: string,
    featureMode: OperationalPointFeatureMode,
    size: OperationalPointCircleFeatureSize,
): Style => {
    const color = featureColor(featureMode);
    const drawText = featureMode !== 'DELETED';

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
    const textArgs = {
        text: new Text({
            text: operationalPointName,
            fill: new Fill({ color }),
            textAlign: 'left',
            backgroundFill: new Fill({ color: 'rgba(255, 255, 255, 0.85)' }),
            scale: 1,
            offsetX: featureStyleOffsetX(size),
            font: featureStyleFont(size),
        }),
    };

    return new Style({ ...styleArgs, ...(drawText ? textArgs : {}) });
};

function getOperationalPointCircleStyleForFeature(
    feature: FeatureLike,
    resolution: number,
    featureMode: OperationalPointFeatureMode,
): Style | undefined {
    const point = feature.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as OperationalPoint;
    const smallestResolutionConf = operationalPointStyleResolutionsSmallestFirst.find(
        (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
    );

    return smallestResolutionConf
        ? createOperationalPointCircleStyle(point.name, featureMode, smallestResolutionConf.style)
        : undefined;
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
    feature.setStyle((feature, resolution) =>
        getOperationalPointCircleStyleForFeature(feature, resolution, featureMode),
    );

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
