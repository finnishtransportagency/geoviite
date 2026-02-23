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
import { ItemCollections, Selection } from 'selection/selection-model';
import { LinkingState, LinkingType, OperationalPointClusterPoint } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { getAllOperationalPoints } from 'track-layout/layout-operational-point-api';

export const OPERATIONAL_POINT_FEATURE_DATA_PROPERTY = 'operational-point-data';
export const OPERATIONAL_POINT_CLUSTER_FEATURE_DATA_PROPERTY = 'operational-point-cluster-data';

export enum OperationalPointLocationFeatureSize {
    Huge,
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
        case OperationalPointLocationFeatureSize.Huge:
            return 10;
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
        case OperationalPointLocationFeatureSize.Huge:
            return 14;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

const fontString = (fontSizePx: number) => `${fontSizePx}px "Open Sans"`;
export const featureStyleFont = (size: OperationalPointLocationFeatureSize) => {
    switch (size) {
        case OperationalPointLocationFeatureSize.Small:
            return fontString(11);
        case OperationalPointLocationFeatureSize.Medium:
            return fontString(14);
        case OperationalPointLocationFeatureSize.Large:
        case OperationalPointLocationFeatureSize.Huge:
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

export const findMatchingOperationalPointCluster = (
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
): OperationalPointClusterPoint[] =>
    findMatchingEntities<OperationalPointClusterPoint>(
        hitArea,
        source,
        OPERATIONAL_POINT_CLUSTER_FEATURE_DATA_PROPERTY,
        options,
    );

const createOperationalPointClusterCircleStyle = (
    featureMode: OperationalPointFeatureMode,
    clusteredAmount: number,
): Style => {
    const color = featureColor(featureMode);

    const styleArgs = {
        image: new Circle({
            radius: featureStyleRadius(OperationalPointLocationFeatureSize.Huge),
            stroke: new Stroke({ color: 'white', width: 2 }),
            fill: new Fill({ color }),
        }),
        fill: new Fill({
            color: 'white',
        }),
        text: new Text({
            fill: new Fill({ color: 'white' }),
            scale: 1.2,
            offsetY: 1,
            offsetX: 1,
            text: clusteredAmount.toString(10),
        }),
    };
    return new Style(styleArgs);
};

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
    size: OperationalPointLocationFeatureSize,
): Style => {
    const color = featureColor(featureMode);

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

    return new Style(textArgs);
};

function getOperationalPointTextStyleForFeature(
    feature: FeatureLike,
    resolution: number,
    featureMode: OperationalPointFeatureMode,
): Style | undefined {
    const point = feature.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as OperationalPoint;
    const locationFeatureSize =
        operationalPointStyleResolutionsSmallestFirst.find(
            (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
        )?.featureSize ?? OperationalPointLocationFeatureSize.Small;

    return createOperationalPointTextStyle(point.name, featureMode, locationFeatureSize);
}

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

export const renderClusteredOperationalPointCircleFeature = (
    featureMode: OperationalPointFeatureMode,
    clusteredPoints: OperationalPoint[] = [],
): Feature<OlPoint> | undefined => {
    const location = clusteredPoints.find((p) => !!p.location)?.location;
    if (!location) {
        return undefined;
    }

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    const data: OperationalPointClusterPoint = {
        id: clusteredPoints.map((point) => point.id).join('__'),
        x: location.x,
        y: location.y,
        operationalPoints: clusteredPoints.map((point) => ({
            name: point.name,
            id: point.id,
        })),
    };

    feature.set(OPERATIONAL_POINT_CLUSTER_FEATURE_DATA_PROPERTY, data);
    feature.setStyle(() =>
        createOperationalPointClusterCircleStyle(featureMode, clusteredPoints.length),
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
    feature.setStyle((feature, resolution) =>
        getOperationalPointTextStyleForFeature(feature, resolution, featureMode),
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

export const operationalPointClusterFeatureModeBySelection = (
    clusteredOperationalPointIds: OperationalPointId[],
    selection: Selection,
): OperationalPointFeatureMode => {
    if (
        clusteredOperationalPointIds.some((id) =>
            isOperationalPointInItemCollection(id, selection.selectedItems),
        )
    ) {
        return 'SELECTED';
    } else if (
        clusteredOperationalPointIds.some((id) =>
            isOperationalPointInItemCollection(id, selection.highlightedItems),
        )
    ) {
        return 'HIGHLIGHTED';
    } else {
        return 'REGULAR';
    }
};

export const operationalPointFeatureModeBySelection = (
    operationalPointId: OperationalPointId,
    selection: Selection,
): OperationalPointFeatureMode => {
    if (isOperationalPointInItemCollection(operationalPointId, selection.selectedItems)) {
        return 'SELECTED';
    } else if (isOperationalPointInItemCollection(operationalPointId, selection.highlightedItems)) {
        return 'HIGHLIGHTED';
    } else {
        return 'REGULAR';
    }
};

const isOperationalPointInItemCollection = (id: OperationalPointId, selection: ItemCollections) =>
    selection.operationalPoints.includes(id) ||
    selection.operationalPointClusters.some((cluster) =>
        cluster.operationalPoints.some((point) => point.id === id),
    );

export const isBeingMoved = (linkingState: LinkingState | undefined, id: OperationalPointId) =>
    linkingState &&
    linkingState.type === LinkingType.PlacingOperationalPoint &&
    linkingState.operationalPoint.id === id &&
    !!linkingState.location;

export const getOperationalPointsFromApi = async (layoutContext: LayoutContext) =>
    getAllOperationalPoints(layoutContext);

export const filterByResolution = (point: OperationalPoint, resolution: number) => {
    if (resolution <= Limits.OPERATIONAL_POINTS_ALL_TYPES_SHOW) return true;
    else return point.raideType === 'OLP';
};
