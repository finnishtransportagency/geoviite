import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { Point, Rectangle } from 'model/geometry';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { OperationalPoint } from 'track-layout/track-layout-model';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import { fieldComparator } from 'utils/array-utils';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke, Text } from 'ol/style';
import Feature, { FeatureLike } from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import mapStyles from 'map/map.module.scss';

export const OPERATIONAL_POINT_FEATURE_DATA_PROPERTY = 'operational-point-data';

export enum OperationalPointFeatureSize {
    Large,
    Medium,
    Small,
}

export type OperationalPointFeatureMode = 'DELETED' | 'HIGHLIGHTED' | 'REGULAR';

const OPERATIONAL_POINT_FEATURE_SIZE_LIMITS: {
    style: OperationalPointFeatureSize;
    resolutionUpperLimit: number;
}[] = [
    {
        style: OperationalPointFeatureSize.Large,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_LARGE,
    },
    {
        style: OperationalPointFeatureSize.Medium,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_MEDIUM,
    },
    {
        style: OperationalPointFeatureSize.Small,
        resolutionUpperLimit: Limits.OPERATIONAL_POINTS_SMALL,
    },
];

export const operationalPointStyleResolutionsSmallestFirst =
    OPERATIONAL_POINT_FEATURE_SIZE_LIMITS.sort(fieldComparator((a) => a.resolutionUpperLimit));

export const featureStyleRadius = (size: OperationalPointFeatureSize) => {
    switch (size) {
        case OperationalPointFeatureSize.Small:
            return 4;
        case OperationalPointFeatureSize.Medium:
            return 5;
        case OperationalPointFeatureSize.Large:
            return 6;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

export const featureStyleOffsetX = (size: OperationalPointFeatureSize) => {
    switch (size) {
        case OperationalPointFeatureSize.Small:
            return 10;
        case OperationalPointFeatureSize.Medium:
            return 12;
        case OperationalPointFeatureSize.Large:
            return 14;
        default:
            return exhaustiveMatchingGuard(size);
    }
};

const fontString = (fontSizePx: number) => `${fontSizePx}px "Open Sans"`;
export const featureStyleFont = (size: OperationalPointFeatureSize) => {
    switch (size) {
        case OperationalPointFeatureSize.Small:
            return fontString(11);
        case OperationalPointFeatureSize.Medium:
            return fontString(14);
        case OperationalPointFeatureSize.Large:
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

const createOperationalPointStyle = (
    operationalPointName: string,
    featureMode: OperationalPointFeatureMode,
    size: OperationalPointFeatureSize,
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

function getOperationalPointStyleForFeature(
    feature: FeatureLike,
    resolution: number,
    featureMode: OperationalPointFeatureMode,
): Style | undefined {
    const point = feature.get(OPERATIONAL_POINT_FEATURE_DATA_PROPERTY) as OperationalPoint;
    const smallestResolutionConf = operationalPointStyleResolutionsSmallestFirst.find(
        (styleResolution) => resolution <= styleResolution.resolutionUpperLimit,
    );

    return smallestResolutionConf
        ? createOperationalPointStyle(point.name, featureMode, smallestResolutionConf.style)
        : undefined;
}

export const renderOperationalPointFeature = (
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
        getOperationalPointStyleForFeature(feature, resolution, featureMode),
    );

    return feature;
};
