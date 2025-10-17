import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { Rectangle } from 'model/geometry';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { OperationalPoint } from 'track-layout/track-layout-model';
import { findMatchingEntities } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import { fieldComparator } from 'utils/array-utils';

export const OPERATIONAL_POINT_FEATURE_DATA_PROPERTY = 'operational-point-data';

export enum OperationalPointFeatureSize {
    Large,
    Medium,
    Small,
    None,
}

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
        case OperationalPointFeatureSize.None:
            return 0;
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
        case OperationalPointFeatureSize.None:
            return undefined;
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
        case OperationalPointFeatureSize.None:
            return undefined;
        default:
            return exhaustiveMatchingGuard(size);
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
