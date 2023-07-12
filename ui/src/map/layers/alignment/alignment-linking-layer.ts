import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    centroid,
    clearFeatures,
    findIntersectingFeatures,
    getPlanarDistanceUnwrapped,
    pointToCoords,
    sortFeaturesByDistance,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LINKING_DOTS } from 'map/layers/utils/layer-visibility-limits';
import {
    ClusterPoint,
    LinkingState,
    LinkingType,
    LinkInterval,
    LinkPoint,
} from 'linking/linking-model';
import { createUpdatedInterval } from 'linking/linking-store';
import { filterNotEmpty, nonEmptyArray } from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { getGeometryLinkPointsByTiles, getLinkPointsByTiles } from 'track-layout/layout-map-api';
import { ChangeTimes } from 'common/common-slice';
import { getTickStyle } from '../utils/alignment-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

const linkPointRadius = 4;
const linkPointSelectedRadius = 6;
const clusterLinkPointRadius = 7;
const clusterLinkPointSelectedRadius = 9;

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
    zIndex: 5,
});

const geometryAlignmentStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 1);

const geometryAlignmentSelectedStyle = strokeStyle(
    mapStyles.selectedGeometryAlignmentInterval,
    3,
    3,
);

const geometryPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    11,
);

const geometryPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointRadius,
    13,
);

const geometryPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointSelectedRadius,
    15,
);

const layoutAlignmentStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 0);

const layoutAlignmentSelectedStyle = strokeStyle(mapStyles.selectedLayoutAlignmentInterval, 3, 2);

const layoutPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    10,
);

const layoutPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointRadius,
    12,
);

const layoutPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointSelectedRadius,
    14,
);

const clusterPointStyle = new Style({
    text: new Text({
        text: '?',
        scale: 1.2,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointRadius,
        stroke: new Stroke({ color: mapStyles.clusterPointBorder }),
        fill: new Fill({ color: mapStyles.clusterPoint }),
    }),
    zIndex: 20,
});

const clusterPointBothSelectedStyle = new Style({
    text: new Text({
        text: '2',
        scale: 1.3,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: 20,
});

const clusterPointGeometrySelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: 20,
});

const clusterPointLayoutSelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedLayoutAlignmentInterval }),
    }),
    zIndex: 20,
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

function getStyleForSegmentTicksIfNeeded(
    point: LinkPoint,
    controlPoint: LinkPoint | undefined,
    style: Style,
): Style | undefined {
    return point.isSegmentEndPoint && controlPoint
        ? getTickStyle(pointToCoords(point), pointToCoords(controlPoint), 6, 'start', style)
        : undefined;
}

function getPointsByOrder(
    points: LinkPoint[],
    orderStart?: number,
    orderEnd?: number,
): LinkPoint[] {
    if (points.length === 0 || orderStart === undefined || orderEnd === undefined) return [];
    else if (orderEnd < points[0].m || orderStart > points[points.length - 1].m) return [];
    else return points.filter((p) => p.m >= orderStart && p.m <= orderEnd);
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
    const highlightInterval = getHighlightInterval(selectedLinkInterval, highlightLinkPoint);

    const highlightIntervalStart = highlightInterval.start?.m;
    const highlightIntervalEnd = highlightInterval.end?.m || highlightIntervalStart;

    const beforeHighlightPoints = getPointsByOrder(points, 0, highlightIntervalStart || Infinity);
    const afterHighlightPoints = getPointsByOrder(points, highlightIntervalEnd, Infinity);
    const highlightPoints = getPointsByOrder(points, highlightIntervalStart, highlightIntervalEnd);

    return [
        // Line before selected interval
        ...createAlignmentFeature(
            beforeHighlightPoints,
            clusterPoints,
            alignmentStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showDots ? pointStyle : undefined,
                    showDots || point.isEndPoint
                        ? getStyleForSegmentTicksIfNeeded(point, controlPoint, alignmentStyle)
                        : undefined,
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
                    showDots || point.isEndPoint
                        ? getStyleForSegmentTicksIfNeeded(point, controlPoint, alignmentStyle)
                        : undefined,
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
                    showDots || point.isEndPoint
                        ? getStyleForSegmentTicksIfNeeded(
                              point,
                              controlPoint,
                              alignmentHighlightStyle,
                          )
                        : undefined,
                );
            },
            isGeometryAlignment,
        ),
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
    if (distance <= buffer)
        return {
            id: geometryPoint.id + layoutPoint.id,
            x: geometryPoint.x,
            y: geometryPoint.y,
            layoutPoint: layoutPoint,
            geometryPoint: geometryPoint,
        };
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
    const highlightedLayoutPoint = selection.highlightedItems.layoutLinkPoints[0];
    const highlightedGeometryPoint = selection.highlightedItems.geometryLinkPoints[0];

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

let newestLayerId = 0;

export function createAlignmentLinkingLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint | LineString>> | undefined,
    selection: Selection,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    const drawLinkingDots = resolution <= LINKING_DOTS;

    let inFlight = false;
    if (linkingState?.state === 'setup' || linkingState?.state === 'allSet') {
        if (linkingState.type === LinkingType.LinkingAlignment) {
            const changeTime = getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            );

            inFlight = true;
            getLinkPointsByTiles(
                changeTime,
                mapTiles,
                linkingState.layoutAlignmentId,
                linkingState.layoutAlignmentType,
            )
                .then((points) => {
                    if (layerId !== newestLayerId) return;
                    const features = createLinkingAlignmentFeatures(
                        points,
                        linkingState.layoutAlignmentInterval,
                        selection.highlightedItems.layoutLinkPoints[0],
                        drawLinkingDots,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource))
                .finally(() => {
                    inFlight = false;
                });
        } else if (linkingState.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
            inFlight = true;
            getGeometryLinkPointsByTiles(
                linkingState.geometryPlanId,
                linkingState.geometryAlignmentId,
                mapTiles,
                [
                    linkingState.geometryAlignmentInterval.start,
                    linkingState.geometryAlignmentInterval.end,
                ].filter(filterNotEmpty),
            )
                .then((points) => {
                    if (layerId !== newestLayerId) return;

                    const features = createAlignmentFeatures(
                        points,
                        selection.highlightedItems.geometryLinkPoints[0],
                        [],
                        linkingState.geometryAlignmentInterval,
                        drawLinkingDots,
                        true,
                        geometryPointStyle,
                        geometryAlignmentStyle,
                        geometryPointSelectedStyle,
                        geometryPointSelectedLargeStyle,
                        geometryAlignmentSelectedStyle,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource))
                .finally(() => {
                    inFlight = false;
                });
        } else if (linkingState?.type === LinkingType.LinkingGeometryWithAlignment) {
            inFlight = true;
            const geometryPointsPromise = getGeometryLinkPointsByTiles(
                linkingState.geometryPlanId,
                linkingState.geometryAlignmentId,
                mapTiles,
                [
                    linkingState.geometryAlignmentInterval.start,
                    linkingState.geometryAlignmentInterval.end,
                    linkingState.layoutAlignmentInterval.start,
                    linkingState.layoutAlignmentInterval.end,
                ].filter(filterNotEmpty),
            );

            const changeTime = getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            );

            const layoutPointsPromise = getLinkPointsByTiles(
                changeTime,
                mapTiles,
                linkingState.layoutAlignmentId,
                linkingState.layoutAlignmentType,
            );

            Promise.all([layoutPointsPromise, geometryPointsPromise])
                .then(([layoutPoints, geometryPoints]) => {
                    if (layerId !== newestLayerId) return;

                    const features = createLinkingGeometryWithAlignmentFeatures(
                        selection,
                        linkingState.layoutAlignmentInterval,
                        linkingState.geometryAlignmentInterval,
                        drawLinkingDots,
                        layoutPoints,
                        geometryPoints,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource))
                .finally(() => {
                    inFlight = false;
                });
        } else {
            clearFeatures(vectorSource);
        }
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'alignment-linking-layer',
        layer: layer,
        searchItems: (hitArea: Rectangle, _options: SearchItemsOptions): LayerItemSearchResult => {
            //If dots are not drawn, do not select anything
            if (!drawLinkingDots) {
                return {
                    layoutLinkPoints: [],
                    geometryLinkPoints: [],
                    clusterPoints: [],
                };
            }

            const features = findIntersectingFeatures<OlPoint | LineString>(hitArea, vectorSource);

            const clusterPoint: ClusterPoint | undefined = findFirstOfType<ClusterPoint>(
                features,
                FeatureType.ClusterPoint,
            );

            let layoutLinkPoint: LinkPoint | undefined;
            let geometryLinkPoint: LinkPoint | undefined;

            if (!clusterPoint) {
                const linkPointFeatures = getSortedLinkPointFeatures(
                    vectorSource.getFeatures(),
                    centroid(hitArea),
                );

                const onLayoutLine = containsType(features, FeatureType.LayoutLine);
                const onGeometryLine = containsType(features, FeatureType.GeometryLine);

                if (onLayoutLine && onGeometryLine) {
                    const closestPoint = linkPointFeatures[0];
                    const closestPointType = getFeatureType(closestPoint);

                    if (closestPointType === FeatureType.GeometryPoint) {
                        geometryLinkPoint = getFeatureData(closestPoint);
                    } else if (closestPointType === FeatureType.LayoutPoint) {
                        layoutLinkPoint = getFeatureData(closestPoint);
                    }
                } else if (onGeometryLine) {
                    geometryLinkPoint = findFirstOfType(
                        linkPointFeatures,
                        FeatureType.GeometryPoint,
                    );
                } else if (onLayoutLine) {
                    layoutLinkPoint = findFirstOfType(linkPointFeatures, FeatureType.LayoutPoint);
                }
            }

            return {
                layoutLinkPoints: layoutLinkPoint ? [layoutLinkPoint] : [],
                geometryLinkPoints: geometryLinkPoint ? [geometryLinkPoint] : [],
                clusterPoints: clusterPoint ? [clusterPoint] : [],
            };
        },
        requestInFlight: () => inFlight,
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
