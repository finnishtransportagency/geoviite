import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    centroid,
    createLayer,
    findIntersectingFeatures,
    getPlanarDistanceUnwrapped,
    loadLayerData,
    pointToCoords,
    sortFeaturesByDistance,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LINKING_DOTS } from 'map/layers/utils/layer-visibility-limits';
import {
    ClusterPoint,
    LayoutAlignmentTypeAndId,
    LinkingAlignment,
    LinkingGeometryWithAlignment,
    LinkingGeometryWithEmptyAlignment,
    LinkingState,
    LinkingType,
    LinkInterval,
    LinkPoint,
} from 'linking/linking-model';
import { createUpdatedInterval } from 'linking/linking-store';
import { filterNotEmpty, first, last, nonEmptyArray } from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { getGeometryLinkPointsByTiles, getLinkPointsByTiles } from 'track-layout/layout-map-api';
import { ChangeTimes } from 'common/common-slice';
import { getTickStyle } from '../utils/alignment-layer-utils';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getReferenceLine } from 'track-layout/layout-reference-line-api';
import { formatTrackMeter } from 'utils/geography-utils';
import { Point, Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { expectCoordinate, expectDefined } from 'utils/type-utils';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { distance, dot, interpolate, minus, portion } from 'utils/math-utils';
import { Precision, roundToPrecisionNumber } from 'utils/rounding';

const linkPointRadius = 4;
const linkPointSelectedRadius = 6;
const clusterLinkPointRadius = 7;
const clusterLinkPointSelectedRadius = 9;
const linkPointSnapRadiusInPixels = 15;

enum zIndexes {
    layoutAlignment,
    geometryAlignment,
    layoutAlignmentSelected,
    geometryAlignmentSelected,
    connectingLines,
    layoutPoint,
    geometryPoint,
    layoutPointSelected,
    geometryPointSelected,
    geometryPointSelectedLarge,
    layoutPointSelectedLarge,
    clusterPointSelected,
    clusterPoint,
    tags,
}

function strokeStyle(color: string, width: number, zIndex: number) {
    return new Style({
        stroke: new Stroke({ color, width }),
        zIndex: zIndex,
    });
}

function pointStyle(strokeColor: string, fillColor: string, radius: number, zIndex: number) {
    return new Style({
        image: new Circle({
            radius: radius,
            stroke: new Stroke({ color: strokeColor }),
            fill: new Fill({ color: fillColor }),
        }),
        zIndex: zIndex,
    });
}

const connectingLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedLayoutAlignmentInterval,
        width: 2,
        lineDash: [5, 5],
    }),
    zIndex: zIndexes.connectingLines,
});

const geometryAlignmentStyle = strokeStyle(
    mapStyles.unselectedAlignmentInterval,
    3,
    zIndexes.geometryAlignment,
);

const geometryAlignmentSelectedStyle = strokeStyle(
    mapStyles.selectedGeometryAlignmentInterval,
    3,
    zIndexes.geometryAlignmentSelected,
);

const geometryPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    zIndexes.geometryPoint,
);

const geometryPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointRadius,
    zIndexes.geometryPointSelected,
);

const geometryPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointSelectedRadius,
    zIndexes.geometryPointSelectedLarge,
);

const layoutAlignmentStyle = strokeStyle(
    mapStyles.unselectedAlignmentInterval,
    3,
    zIndexes.layoutAlignment,
);

const layoutAlignmentSelectedStyle = strokeStyle(
    mapStyles.selectedLayoutAlignmentInterval,
    3,
    zIndexes.layoutAlignmentSelected,
);

const layoutPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    zIndexes.layoutPoint,
);

const layoutPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointRadius,
    zIndexes.layoutPointSelected,
);

const layoutPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointSelectedRadius,
    zIndexes.layoutPointSelectedLarge,
);

const clusterPointStyle = new Style({
    text: new Text({
        text: '?',
        scale: 1.2,
        offsetY: 1,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointRadius,
        stroke: new Stroke({ color: mapStyles.clusterPointBorder }),
        fill: new Fill({ color: mapStyles.clusterPoint }),
    }),
    zIndex: zIndexes.clusterPoint,
});

const clusterPointBothSelectedStyle = new Style({
    text: new Text({
        text: '2',
        scale: 1.2,
        offsetY: 1,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: zIndexes.clusterPointSelected,
});

const clusterPointGeometrySelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: zIndexes.clusterPointSelected,
});

const clusterPointLayoutSelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedLayoutAlignmentInterval }),
    }),
    zIndex: zIndexes.clusterPointSelected,
});

const LINKING_FEATURE_TYPE_PROPERTY = 'type';
const LINKING_FEATURE_DATA_PROPERTY = 'linking-data';

enum FeatureType {
    GeometryLine = 'geometryLine',
    LayoutLine = 'layoutLine',
    LayoutPoint = 'layoutPoint',
    GeometryPoint = 'geometryPoint',
    ClusterPoint = 'clusterPoint',
}

function createClusterPointFeature(
    clusterPoint: ClusterPoint,
    pointStyle: Style,
): Feature<OlPoint> {
    const pointFeature = new Feature({
        geometry: new OlPoint(pointToCoords(clusterPoint)),
    });

    pointFeature.setStyle(pointStyle);
    pointFeature.set(LINKING_FEATURE_TYPE_PROPERTY, FeatureType.ClusterPoint);
    pointFeature.set(LINKING_FEATURE_DATA_PROPERTY, clusterPoint);

    return pointFeature;
}

function createPointFeature(
    point: LinkPoint,
    pointStyle: Style[],
    type: FeatureType,
): Feature<OlPoint> {
    const pointFeature = new Feature({
        geometry: new OlPoint(pointToCoords(point)),
    });

    pointFeature.setStyle(pointStyle);
    pointFeature.set(LINKING_FEATURE_TYPE_PROPERTY, type);
    pointFeature.set(LINKING_FEATURE_DATA_PROPERTY, point);

    return pointFeature;
}

function createLineFeature(
    points: LinkPoint[],
    style: Style,
    type: FeatureType,
): Feature<LineString> {
    const alignmentFeature = new Feature({
        geometry: new LineString(points.map((p) => pointToCoords(p))),
    });

    alignmentFeature.setStyle(style);
    alignmentFeature.set(LINKING_FEATURE_TYPE_PROPERTY, type);

    return alignmentFeature;
}

function getClusterPointStyle(
    clusterPoint: ClusterPoint,
    layoutInterval: LinkInterval,
    geometryInterval: LinkInterval,
): Style | undefined {
    const layoutPointFound =
        clusterPoint.layoutPoint.id === layoutInterval.start?.id ||
        clusterPoint.layoutPoint.id === layoutInterval.end?.id;

    const geometryPointFound =
        clusterPoint.geometryPoint.id === geometryInterval.start?.id ||
        clusterPoint.geometryPoint.id === geometryInterval.end?.id;

    if (layoutPointFound && geometryPointFound) return clusterPointBothSelectedStyle;
    else if (!layoutPointFound && !geometryPointFound) return clusterPointStyle;
    else if (layoutPointFound) return clusterPointLayoutSelectedStyle;
    else if (geometryPointFound) return clusterPointGeometrySelectedStyle;
    else return undefined;
}

function createClusterPointFeatures(
    clusterPoints: ClusterPoint[],
    unselectedStyle: Style,
    layoutInterval: LinkInterval,
    geometryInterval: LinkInterval,
): Feature<OlPoint>[] {
    return clusterPoints.map((point) => {
        const clusterPointStyle = getClusterPointStyle(point, layoutInterval, geometryInterval);

        return createClusterPointFeature(point, clusterPointStyle ?? unselectedStyle);
    });
}

function createAlignmentFeature(
    points: LinkPoint[],
    clusterPoints: LinkPoint[],
    alignmentStyle: Style,
    pointStyleFunc: (
        point: LinkPoint,
        anotherPoint: LinkPoint | undefined,
        isEndPoint: boolean,
    ) => Style[],
    isGeometryAlignment: boolean,
): Feature<OlPoint | LineString>[] {
    const features: Feature<OlPoint | LineString>[] = [];
    if (points.length >= 2) {
        features.push(
            createLineFeature(
                points,
                alignmentStyle,
                isGeometryAlignment ? FeatureType.GeometryLine : FeatureType.LayoutLine,
            ),
        );
    }

    points.forEach((point, index) => {
        if (!clusterPoints.find((cPoint) => cPoint.id === point.id)) {
            const pointStyles = pointStyleFunc(
                point,
                points[index + 1] ?? points[index - 1],
                index === 0 || index === points.length - 1,
            );

            features.push(
                createPointFeature(
                    point,
                    pointStyles,
                    isGeometryAlignment ? FeatureType.GeometryPoint : FeatureType.LayoutPoint,
                ),
            );
        }
    });

    return features;
}

function getStyleForSegmentTickIfNeeded(
    showDots: boolean,
    point: LinkPoint,
    controlPoint: LinkPoint | undefined,
    style: Style,
): Style | undefined {
    return ((showDots && point.isSegmentEndPoint) || point.isEndPoint) && controlPoint
        ? getTickStyle(pointToCoords(point), pointToCoords(controlPoint), 6, 'start', style)
        : undefined;
}

function getPointsByOrder(
    points: LinkPoint[],
    orderStart?: number,
    orderEnd?: number,
): LinkPoint[] {
    const firstPoint = first(points);
    const lastPoint = last(points);

    if (!firstPoint || !lastPoint || orderStart === undefined || orderEnd === undefined) return [];
    else if (orderEnd < firstPoint.m || orderStart > lastPoint.m) return [];
    else return points.filter((p) => p.m >= orderStart && p.m <= orderEnd);
}

function createPointTagFeature(
    point: LinkPoint,
    pointType: 'layout' | 'geometry',
): Feature<OlPoint> {
    const color =
        pointType == 'geometry'
            ? mapStyles.selectedGeometryAlignmentInterval
            : mapStyles.selectedLayoutAlignmentInterval;

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point)),
    });

    const showAtLeftSide = pointType == 'geometry';
    const rotationByPointDirection = point.direction ? -point.direction + Math.PI / 2 : 0;
    const rotation = rotationByPointDirection + (showAtLeftSide ? Math.PI : 0);

    const renderer = (coord: Coordinate, { pixelRatio, context }: State) => {
        const [x, y] = expectCoordinate(coord);
        const fontSize = 12;
        const textPadding = 3 * pixelRatio;
        const textBackgroundHeight = (fontSize + 4) * pixelRatio;
        const arrowSpacing = 8 * pixelRatio;
        const arrowTailPadding = 8 * pixelRatio;

        const ctx = context;

        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${pixelRatio * fontSize}px ${
            mapStyles['alignmentBadge-font-family']
        }`;

        ctx.save();

        const text = point.address ? formatTrackMeter(point.address) : '';
        const textWidth = ctx.measureText(text).width;
        const arrowHeight = textBackgroundHeight + textPadding * 2;
        const arrowTipLength = arrowHeight / 2;
        const arrowLength = arrowTipLength + textWidth + textPadding + arrowTailPadding;
        const textStartX = arrowSpacing + arrowTipLength + textPadding;

        // Arrow (or sign) shape, pointing from right to left
        const arrowShapePolygon = [
            [0, 0],
            [arrowTipLength, arrowHeight / 2],
            [arrowLength, arrowHeight / 2],
            [arrowLength, -arrowHeight / 2],
            [arrowTipLength, -arrowHeight / 2],
        ] as const;

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.moveTo(x + arrowSpacing + arrowShapePolygon[0][0], y + arrowShapePolygon[0][1]);
        arrowShapePolygon
            .slice(1)
            .forEach(([coordinateX, coordinateY]) =>
                ctx.lineTo(x + arrowSpacing + coordinateX, y + coordinateY),
            );
        ctx.closePath();
        ctx.fill();

        if (rotation > Math.PI / 2 || rotation < -Math.PI / 2) {
            // When arrow is pointing from left to right, flip text horizontally/vertically
            const textCenterX = x + textStartX + textWidth / 2;
            ctx.translate(textCenterX, y);
            ctx.scale(-1, -1);
            ctx.translate(-textCenterX, -y);
        }

        ctx.fillStyle = '#fff';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, x + textStartX, y);

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer, zIndex: zIndexes.tags }));
    return feature;
}

function createAlignmentFeatures(
    points: LinkPoint[],
    highlightLinkPoint: LinkPoint | undefined,
    clusterPoints: LinkPoint[],
    selectedLinkInterval: LinkInterval,
    showDots: boolean,
    isGeometryAlignment: boolean,
    pointStyle: Style,
    alignmentStyle: Style,
    pointHighlightStyle: Style,
    pointHighlightLargeStyle: Style,
    alignmentHighlightStyle: Style,
): Feature<OlPoint | LineString>[] {
    const interpolatedPoints = [
        highlightLinkPoint,
        selectedLinkInterval.start,
        selectedLinkInterval.end,
    ]
        .filter(filterNotEmpty)
        .filter((p) => p.isInterpolated);

    const allPoints = [...points, ...interpolatedPoints].sort((a, b) => a.m - b.m);

    const highlightInterval = getHighlightInterval(selectedLinkInterval, highlightLinkPoint);

    const highlightIntervalStart = highlightInterval.start?.m;
    const highlightIntervalEnd = highlightInterval.end?.m || highlightIntervalStart;

    const beforeHighlightPoints = getPointsByOrder(
        allPoints,
        0,
        highlightIntervalStart || Infinity,
    );
    const afterHighlightPoints = getPointsByOrder(allPoints, highlightIntervalEnd, Infinity);
    const highlightPoints = getPointsByOrder(
        allPoints,
        highlightIntervalStart,
        highlightIntervalEnd,
    );

    return [
        // Line before selected interval
        ...createAlignmentFeature(
            beforeHighlightPoints,
            clusterPoints,
            alignmentStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showDots ? pointStyle : undefined,
                    getStyleForSegmentTickIfNeeded(showDots, point, controlPoint, alignmentStyle),
                ),
            isGeometryAlignment,
        ),
        // Line after selected interval
        ...createAlignmentFeature(
            afterHighlightPoints,
            clusterPoints,
            alignmentStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showDots ? pointStyle : undefined,
                    getStyleForSegmentTickIfNeeded(showDots, point, controlPoint, alignmentStyle),
                ),
            isGeometryAlignment,
        ),
        // Selected interval line
        ...createAlignmentFeature(
            highlightPoints,
            clusterPoints,
            alignmentHighlightStyle,
            (point, controlPoint, isEndPoint) => {
                const pointStyle = isEndPoint ? pointHighlightLargeStyle : pointHighlightStyle;
                return nonEmptyArray(
                    showDots ? pointStyle : undefined,
                    getStyleForSegmentTickIfNeeded(
                        showDots,
                        point,
                        controlPoint,
                        alignmentHighlightStyle,
                    ),
                );
            },
            isGeometryAlignment,
        ),
        ...(selectedLinkInterval.start && selectedLinkInterval.start.address
            ? [
                  createPointTagFeature(
                      selectedLinkInterval.start,
                      isGeometryAlignment ? 'geometry' : 'layout',
                  ),
              ]
            : []),
        ...(selectedLinkInterval.end && selectedLinkInterval.end.address
            ? [
                  createPointTagFeature(
                      selectedLinkInterval.end,
                      isGeometryAlignment ? 'geometry' : 'layout',
                  ),
              ]
            : []),
        ...(highlightLinkPoint && highlightLinkPoint.address
            ? [
                  createPointTagFeature(
                      highlightLinkPoint,
                      isGeometryAlignment ? 'geometry' : 'layout',
                  ),
              ]
            : []),
    ];
}

function overlappingPoint(
    layoutPoint: LinkPoint,
    geometryPoint: LinkPoint,
): ClusterPoint | undefined {
    const distance = getPlanarDistanceUnwrapped(
        geometryPoint.x,
        geometryPoint.y,
        layoutPoint.x,
        layoutPoint.y,
    );
    const buffer = 0.01;
    return distance <= buffer
        ? {
              id: geometryPoint.id + layoutPoint.id,
              x: geometryPoint.x,
              y: geometryPoint.y,
              layoutPoint: layoutPoint,
              geometryPoint: geometryPoint,
          }
        : undefined;
}

function createConnectingLineFeature(start: LinkPoint, end: LinkPoint): Feature<LineString> {
    const lineFeature = new Feature({
        geometry: new LineString([pointToCoords(start), pointToCoords(end)]),
    });

    lineFeature.setStyle(connectingLineStyle);
    return lineFeature;
}

function createLinkingAlignmentFeatures(
    points: LinkPoint[],
    layoutInterval: LinkInterval,
    highlightPoint: LinkPoint | undefined,
    showDots: boolean,
): Feature<LineString | OlPoint>[] {
    return createAlignmentFeatures(
        points,
        highlightPoint,
        [],
        layoutInterval,
        showDots,
        false,
        layoutPointStyle,
        layoutAlignmentStyle,
        layoutPointSelectedStyle,
        layoutPointSelectedLargeStyle,
        layoutAlignmentSelectedStyle,
    );
}

function getClusterPoints(
    layoutPoints: LinkPoint[],
    geometryPoints: LinkPoint[],
): [ClusterPoint[], LinkPoint[]] {
    const clusterPoints: ClusterPoint[] = [];
    const overlappingPoints: LinkPoint[] = [];

    layoutPoints.forEach((layoutPoint) => {
        geometryPoints.forEach((geometryPoint) => {
            const clusterPoint = overlappingPoint(layoutPoint, geometryPoint);

            if (clusterPoint) {
                clusterPoints.push(clusterPoint);
                overlappingPoints.push(layoutPoint);
                overlappingPoints.push(geometryPoint);
            }
        });
    });

    return [clusterPoints, overlappingPoints];
}

function createLinkingGeometryWithAlignmentFeatures(
    selection: Selection,
    selectedLayoutInterval: LinkInterval,
    selectedGeometryInterval: LinkInterval,
    showDots: boolean,
    layoutPoints: LinkPoint[],
    geometryPoints: LinkPoint[],
): Feature<OlPoint | LineString>[] {
    const features: Feature<OlPoint | LineString>[] = [];
    const [clusterPoints, overlappingPoints] = getClusterPoints(layoutPoints, geometryPoints);
    const highlightedLayoutPoint = first(selection.highlightedItems.layoutLinkPoints);
    const highlightedGeometryPoint = first(selection.highlightedItems.geometryLinkPoints);

    if (showDots) {
        features.push(
            ...createClusterPointFeatures(
                clusterPoints,
                clusterPointStyle,
                selectedLayoutInterval,
                selectedGeometryInterval,
            ),
        );
    }

    features.push(
        ...createAlignmentFeatures(
            layoutPoints,
            highlightedLayoutPoint,
            overlappingPoints,
            selectedLayoutInterval,
            showDots,
            false,
            layoutPointSelectedStyle,
            layoutAlignmentSelectedStyle,
            layoutPointStyle,
            layoutPointSelectedLargeStyle,
            layoutAlignmentStyle,
        ),
    );

    features.push(
        ...createAlignmentFeatures(
            geometryPoints,
            highlightedGeometryPoint,
            overlappingPoints,
            selectedGeometryInterval,
            showDots,
            true,
            geometryPointStyle,
            geometryAlignmentStyle,
            geometryPointSelectedStyle,
            geometryPointSelectedLargeStyle,
            geometryAlignmentSelectedStyle,
        ),
    );

    const layoutInterval = getHighlightInterval(selectedLayoutInterval, highlightedLayoutPoint);
    const geometryInterval = getHighlightInterval(
        selectedGeometryInterval,
        highlightedGeometryPoint,
    );

    if (
        layoutInterval.start &&
        geometryInterval.start &&
        layoutInterval.start.id !== layoutInterval?.end?.id &&
        !layoutInterval.start.isEndPoint
    ) {
        features.push(createConnectingLineFeature(layoutInterval.start, geometryInterval.start));
    }

    if (
        layoutInterval.end &&
        geometryInterval.end &&
        layoutInterval?.start?.id !== layoutInterval.end.id &&
        !layoutInterval.end.isEndPoint
    ) {
        features.push(createConnectingLineFeature(layoutInterval.end, geometryInterval.end));
    }

    if (
        layoutInterval.start?.id === layoutInterval?.end?.id &&
        layoutInterval.end?.isEndPoint &&
        layoutInterval.start?.isEndPoint &&
        geometryInterval.start &&
        geometryInterval.end
    ) {
        if (layoutInterval.start.m === 0) {
            features.push(createConnectingLineFeature(layoutInterval.start, geometryInterval.end));
        } else {
            features.push(createConnectingLineFeature(layoutInterval.end, geometryInterval.start));
        }
    }

    return features;
}

type LinkPointContainer = { [k: string]: LinkPoint | undefined };

async function getLinkPointsWithAddresses<
    T extends LinkPointContainer,
    TPropertyName extends keyof T,
>(layoutContext: LayoutContext, layoutAlignment: LayoutAlignmentTypeAndId, points: T): Promise<T> {
    const trackNumberId = await (layoutAlignment.type == 'LOCATION_TRACK'
        ? getLocationTrack(layoutAlignment.id, draftLayoutContext(layoutContext)).then(
              (locationTrack) => locationTrack?.trackNumberId,
          )
        : getReferenceLine(layoutAlignment.id, draftLayoutContext(layoutContext)).then(
              (referenceLine) => referenceLine?.trackNumberId,
          ));

    if (!trackNumberId) {
        return points;
    }

    const propertyNames = Object.keys(points) as TPropertyName[];
    const promises = propertyNames
        .map((propertyName: TPropertyName) => {
            const originalPoint = points[propertyName];
            return originalPoint != undefined
                ? // This is commented out for now to re-evaluate the linking tag feature
                  //getAddress(trackNumberId, originalPoint, 'DRAFT')
                  Promise.resolve(undefined).then((address) => ({
                      propertyName: propertyName,
                      address: address || undefined,
                  }))
                : undefined;
        })
        .filter(filterNotEmpty);

    return Promise.all(promises).then((loadedLinkPointsWithAddresses) => {
        const pointsWithAddresses = { ...points };
        loadedLinkPointsWithAddresses.forEach(({ propertyName, address }) => {
            if (pointsWithAddresses[propertyName]) {
                pointsWithAddresses[propertyName] = {
                    ...pointsWithAddresses[propertyName],
                    address: address,
                };
            }
        });
        return pointsWithAddresses;
    });
}

type LayoutSection = {
    layoutStart: LinkPoint | undefined;
    layoutEnd: LinkPoint | undefined;
    layoutHighlight: LinkPoint | undefined;
};

type GeometrySection = {
    geometryStart: LinkPoint | undefined;
    geometryEnd: LinkPoint | undefined;
    geometryHighlight: LinkPoint | undefined;
};

type AlignmentLinkingData = {
    type: LinkingType.LinkingAlignment;
    state: LinkingAlignment;
    points: LinkPoint[];
    pointAddresses: LayoutSection;
};

type GeometryWithEmptyAlignmentLinkingData = {
    type: LinkingType.LinkingGeometryWithEmptyAlignment;
    state: LinkingGeometryWithEmptyAlignment;
    points: LinkPoint[];
    pointAddresses: GeometrySection;
};

type GeometryWithAlignmentLinkingData = {
    type: LinkingType.LinkingGeometryWithAlignment;
    state: LinkingGeometryWithAlignment;
    layoutPoints: LinkPoint[];
    geometryPoints: LinkPoint[];
    pointAddresses: GeometrySection & LayoutSection;
};

type EmptyData = {
    type: 'empty';
};

type LinkingData =
    | AlignmentLinkingData
    | GeometryWithEmptyAlignmentLinkingData
    | GeometryWithAlignmentLinkingData
    | EmptyData;

async function getLinkingData(
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    selection: Selection,
    state: LinkingState | undefined,
    changeTimes: ChangeTimes,
    includeSegmentEndPoints: boolean,
): Promise<LinkingData> {
    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
    );
    const { highlightedItems } = selection;

    if (state === undefined || state.state === 'preliminary') {
        return { type: 'empty' };
    } else if (state.type === LinkingType.LinkingAlignment) {
        const [points, pointAddresses] = await Promise.all([
            getLinkPointsByTiles(
                changeTime,
                layoutContext,
                mapTiles,
                state.layoutAlignment,
                includeSegmentEndPoints,
            ),
            getLinkPointsWithAddresses(layoutContext, state.layoutAlignment, {
                layoutStart: state.layoutAlignmentInterval.start,
                layoutEnd: state.layoutAlignmentInterval.end,
                layoutHighlight: first(highlightedItems.layoutLinkPoints),
            }),
        ]);
        return { type: LinkingType.LinkingAlignment, state, points, pointAddresses };
    } else if (state.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
        const [points, pointAddresses] = await Promise.all([
            getGeometryLinkPointsByTiles(
                state.geometryPlanId,
                state.geometryAlignmentId,
                mapTiles,
                [state.geometryAlignmentInterval.start, state.geometryAlignmentInterval.end].filter(
                    filterNotEmpty,
                ),
            ),
            getLinkPointsWithAddresses(layoutContext, state.layoutAlignment, {
                geometryStart: state.geometryAlignmentInterval.start,
                geometryEnd: state.geometryAlignmentInterval.end,
                geometryHighlight: first(highlightedItems.geometryLinkPoints),
            }),
        ]);
        return {
            type: LinkingType.LinkingGeometryWithEmptyAlignment,
            state,
            points,
            pointAddresses,
        };
    } else if (state.type === LinkingType.LinkingGeometryWithAlignment) {
        const [geometryPoints, layoutPoints, pointAddresses] = await Promise.all([
            getGeometryLinkPointsByTiles(
                state.geometryPlanId,
                state.geometryAlignmentId,
                mapTiles,
                [
                    state.geometryAlignmentInterval.start,
                    state.geometryAlignmentInterval.end,
                    state.layoutAlignmentInterval.start,
                    state.layoutAlignmentInterval.end,
                ].filter(filterNotEmpty),
            ),
            getLinkPointsByTiles(changeTime, layoutContext, mapTiles, state.layoutAlignment),
            getLinkPointsWithAddresses(layoutContext, state.layoutAlignment, {
                layoutStart: state.layoutAlignmentInterval.start,
                layoutEnd: state.layoutAlignmentInterval.end,
                layoutHighlight: first(highlightedItems.layoutLinkPoints),
                geometryStart: state.geometryAlignmentInterval.start,
                geometryEnd: state.geometryAlignmentInterval.end,
                geometryHighlight: first(highlightedItems.geometryLinkPoints),
            }),
        ]);
        return {
            type: LinkingType.LinkingGeometryWithAlignment,
            state: state,
            layoutPoints,
            geometryPoints,
            pointAddresses,
        };
    } else {
        return { type: 'empty' };
    }
}

const createFeatures = (
    data: LinkingData,
    selection: Selection,
    drawLinkingDots: boolean,
): Feature<LineString | OlPoint>[] => {
    switch (data.type) {
        case LinkingType.LinkingAlignment:
            return createLinkingAlignmentFeatures(
                data.points,
                {
                    ...data.state.layoutAlignmentInterval,
                    start: data.pointAddresses.layoutStart,
                    end: data.pointAddresses.layoutEnd,
                },
                data.pointAddresses.layoutHighlight,
                drawLinkingDots,
            );

        case LinkingType.LinkingGeometryWithEmptyAlignment:
            return createAlignmentFeatures(
                data.points,
                data.pointAddresses.geometryHighlight,
                [],
                {
                    ...data.state.geometryAlignmentInterval,
                    start: data.pointAddresses.geometryStart,
                    end: data.pointAddresses.geometryEnd,
                },
                drawLinkingDots,
                true,
                geometryPointStyle,
                geometryAlignmentStyle,
                geometryPointSelectedStyle,
                geometryPointSelectedLargeStyle,
                geometryAlignmentSelectedStyle,
            );

        case LinkingType.LinkingGeometryWithAlignment:
            return createLinkingGeometryWithAlignmentFeatures(
                {
                    ...selection,
                    highlightedItems: {
                        ...selection.highlightedItems,
                        layoutLinkPoints: data.pointAddresses.layoutHighlight
                            ? [data.pointAddresses.layoutHighlight]
                            : [],
                        geometryLinkPoints: data.pointAddresses.geometryHighlight
                            ? [data.pointAddresses.geometryHighlight]
                            : [],
                    },
                },
                {
                    ...data.state.layoutAlignmentInterval,
                    start: data.pointAddresses.layoutStart,
                    end: data.pointAddresses.layoutEnd,
                },
                {
                    ...data.state.geometryAlignmentInterval,
                    start: data.pointAddresses.geometryStart,
                    end: data.pointAddresses.geometryEnd,
                },
                drawLinkingDots,
                data.layoutPoints,
                data.geometryPoints,
            );

        case 'empty':
            return [];
    }
};

const emptySearchResult = (): LayerItemSearchResult => ({
    layoutLinkPoints: [],
    geometryLinkPoints: [],
    clusterPoints: [],
});

function getPointByOlPoint(olPoint: OlPoint): Point {
    const c = olPoint.getCoordinates();
    return {
        x: expectDefined(c[0]),
        y: expectDefined(c[1]),
    };
}

function createInterpolatedLinkPoint(p1: LinkPoint, p2: LinkPoint, targetPoint: Point): LinkPoint {
    const portionBetweenPoints = portion(p1, p2, targetPoint);
    const interpolatedM = roundToPrecisionNumber(
        interpolate(p1.m, p2.m, portionBetweenPoints),
        Precision.alignmentM,
    );
    return {
        ...p1,
        id: 'interpolated_' + interpolatedM,
        x: interpolate(p1.x, p2.x, portionBetweenPoints),
        y: interpolate(p1.y, p2.y, portionBetweenPoints),
        m: interpolatedM,
        isSegmentEndPoint: false,
        isEndPoint: false,
        isInterpolated: true,
    };
}

function getInterpolatedLinkPoint(
    linkPointFeatures: Feature<OlPoint>[],
    targetPoint: Point,
    featureType: FeatureType,
    resolution: number,
): LinkPoint | undefined {
    const filteredLinkPointFeatures = linkPointFeatures.filter((f) => {
        const linkPoint = getFeatureData(f) as LinkPoint;
        return !linkPoint.isInterpolated;
    });

    const closestLinkPoint = findFirstOfType(filteredLinkPointFeatures, featureType) as LinkPoint;
    if (!closestLinkPoint) {
        return undefined;
    }

    const closestLinkPointDistanceInPixels = distance(targetPoint, closestLinkPoint) / resolution;
    if (closestLinkPointDistanceInPixels > linkPointSnapRadiusInPixels) {
        const fromClosestPointToTargetVector = minus(targetPoint, closestLinkPoint);

        const neighbourPointFeature = linkPointFeatures.find((f) => {
            const testPointType = getFeatureType(f);
            if (testPointType !== featureType) {
                return false;
            }
            const testLinkPoint = getFeatureData(f) as LinkPoint;
            const fromTestPointToTargetVector = minus(targetPoint, testLinkPoint);
            const dotProduct = dot(fromClosestPointToTargetVector, fromTestPointToTargetVector);
            const oppositeDirection = dotProduct < 0;
            return closestLinkPoint.id !== testLinkPoint.id && oppositeDirection;
        });

        if (neighbourPointFeature) {
            const neighbourLinkPoint = getFeatureData(neighbourPointFeature) as LinkPoint;
            return createInterpolatedLinkPoint(closestLinkPoint, neighbourLinkPoint, targetPoint);
        }
    }
    return closestLinkPoint;
}

const searchItems = (
    source: VectorSource,
    hitArea: Rectangle,
    resolution: number,
    allowInterpolation: boolean,
): LayerItemSearchResult => {
    const features = findIntersectingFeatures<OlPoint | LineString>(hitArea, source);

    const clusterPoint: ClusterPoint | undefined = findFirstOfType<ClusterPoint>(
        features,
        FeatureType.ClusterPoint,
    );

    let layoutLinkPoint: LinkPoint | undefined;
    let geometryLinkPoint: LinkPoint | undefined;

    if (!clusterPoint) {
        const centerOlPoint = centroid(hitArea);
        const centerPoint = getPointByOlPoint(centerOlPoint);
        const linkPointFeatures = getSortedLinkPointFeatures(source.getFeatures(), centerOlPoint);

        const onLayoutLine = containsType(features, FeatureType.LayoutLine);
        const onGeometryLine = containsType(features, FeatureType.GeometryLine);

        if (onLayoutLine && onGeometryLine) {
            const closestPoint = first(linkPointFeatures);
            const closestPointType = getFeatureType(closestPoint);

            if (closestPoint && closestPointType === FeatureType.GeometryPoint) {
                geometryLinkPoint = getFeatureData(closestPoint);
            } else if (closestPoint && closestPointType === FeatureType.LayoutPoint) {
                layoutLinkPoint = getFeatureData(closestPoint);
            }
        } else if (onGeometryLine) {
            geometryLinkPoint = findFirstOfType(linkPointFeatures, FeatureType.GeometryPoint);
        } else if (onLayoutLine) {
            if (allowInterpolation) {
                layoutLinkPoint = getInterpolatedLinkPoint(
                    linkPointFeatures,
                    centerPoint,
                    FeatureType.LayoutPoint,
                    resolution,
                );
            } else {
                layoutLinkPoint = findFirstOfType(linkPointFeatures, FeatureType.LayoutPoint);
            }
        }
    }

    return {
        layoutLinkPoints: layoutLinkPoint ? [layoutLinkPoint] : [],
        geometryLinkPoints: geometryLinkPoint ? [geometryLinkPoint] : [],
        clusterPoints: clusterPoint ? [clusterPoint] : [],
    };
};

const layerName: MapLayerName = 'alignment-linking-layer';

export function createAlignmentLinkingLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<OlPoint | LineString>> | undefined,
    layoutContext: LayoutContext,
    selection: Selection,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const drawLinkingDots = resolution <= LINKING_DOTS;
    const loadSegmentEndPoints = drawLinkingDots;

    const dataPromise = getLinkingData(
        mapTiles,
        layoutContext,
        selection,
        linkingState,
        changeTimes,
        loadSegmentEndPoints,
    );

    loadLayerData(source, isLatest, onLoadingData, dataPromise, (data) =>
        createFeatures(data, selection, drawLinkingDots),
    );

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, _options: SearchItemsOptions): LayerItemSearchResult => {
            if (!drawLinkingDots) {
                //If dots are not drawn, do not select anything
                return emptySearchResult();
            } else {
                // Currently link point interpolation is used for shortening layout alignments only.
                // To use interpolated link points to link a geometry alignment and a layout alignment the whole concept
                // must be revised, as in this case geometry points and layout points locate at somewhat the same line,
                // and therefore finding an interpolated point hops between geometry and alignment points and this could
                // be very disturbing.
                const allowInterpolatedLinkPoints =
                    linkingState?.type === LinkingType.LinkingAlignment;
                return searchItems(source, hitArea, resolution, allowInterpolatedLinkPoints);
            }
        },
    };
}

function containsType(features: Feature[], type: FeatureType): boolean {
    return features.some((f) => getFeatureType(f) === type);
}

function findFirstOfType<T>(features: Feature[], type: FeatureType): T | undefined {
    const f = features.find((f) => getFeatureType(f) === type);
    return f ? (getFeatureData(f) as T) : undefined;
}

function getFeatureType(feature: Feature | undefined): FeatureType | undefined {
    return feature?.get(LINKING_FEATURE_TYPE_PROPERTY);
}

function getFeatureData<T>(feature: Feature): T {
    return feature.get(LINKING_FEATURE_DATA_PROPERTY) as T;
}

function getSortedLinkPointFeatures(features: Feature[], hitArea: OlPoint): Feature<OlPoint>[] {
    const pointFeatures = features.filter((f) => {
        const type = getFeatureType(f);
        return type === FeatureType.LayoutPoint || type === FeatureType.GeometryPoint;
    }) as Feature<OlPoint>[];

    return sortFeaturesByDistance(pointFeatures, hitArea);
}

function getHighlightInterval(
    selectedInterval: LinkInterval,
    highlightPoint: LinkPoint | undefined,
) {
    return highlightPoint &&
        highlightPoint.m !== selectedInterval.start?.m &&
        highlightPoint.m !== selectedInterval.end?.m
        ? createUpdatedInterval(selectedInterval, highlightPoint, true)
        : selectedInterval;
}
