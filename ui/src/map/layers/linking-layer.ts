import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    clearFeatures,
    getMatchingEntities,
    getMatchingLinkPoints,
    getPlanarDistanceUnwrapped,
    getTickStyle,
    MatchOptions,
    pointToCoords,
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

const linkPointRadius = 4;
const LinkPointSelectedRadius = 6;
const clusterLinkPointRadius = 7;
const clusterLinkPointSelectedRadius = 9;

function strokeStyle(color: string, width: number, zIndex: number) {
    return new Style({
        stroke: new Stroke({ color: color, width: width }),
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
    zIndex: 0,
});

const geometryAlignmentStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 2);

const geometryAlignmentSelectedStyle = strokeStyle(
    mapStyles.selectedGeometryAlignmentInterval,
    3,
    4,
);

const geometryAlignmentHighlightedStyle = strokeStyle(
    mapStyles.selectedGeometryAlignmentInterval,
    4,
    6,
);

const geometryPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    8,
);

const geometryPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointRadius,
    10,
);

const geometryPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedGeometryAlignmentInterval,
    LinkPointSelectedRadius,
    12,
);

const layoutAlignmentStyle = strokeStyle(mapStyles.selectedLayoutAlignmentInterval, 3, 1);

const layoutAlignmentSelectedStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 3);

const layoutAlignmentHighlightedStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 4, 5);

const layoutPointStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointRadius,
    7,
);

const layoutPointSelectedStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    9,
);

const layoutPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPoint,
    mapStyles.selectedLayoutAlignmentInterval,
    LinkPointSelectedRadius,
    11,
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
    zIndex: 13,
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
    zIndex: 13,
});

const clusterPointGeometrySelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: 13,
});

const clusterPointLayoutSelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPoint }),
        fill: new Fill({ color: mapStyles.selectedLayoutAlignmentInterval }),
    }),
    zIndex: 13,
});

export const FEATURE_PROPERTY_LINK_POINT = 'linkPoint';
export const FEATURE_PROPERTY_CLUSTER_POINT = 'clusterPoint';
export const FEATURE_PROPERTY_TYPE = 'type';

function createLineFeature(startPoint: LinkPoint, endPoint: LinkPoint, segmentStyle: Style) {
    const segmentFeature = new Feature({
        geometry: new LineString([pointToCoords(startPoint), pointToCoords(endPoint)]),
    });

    segmentFeature.setStyle(segmentStyle);
    return segmentFeature;
}

function createClusterPointFeature(
    clusterPoint: ClusterPoint,
    pointStyle: Style[],
): Feature<Point> {
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(clusterPoint)),
    });

    pointFeature.setStyle(pointStyle);
    pointFeature.set(FEATURE_PROPERTY_TYPE, 'cluster');
    pointFeature.set(FEATURE_PROPERTY_CLUSTER_POINT, clusterPoint);

    return pointFeature;
}

function createPointFeature(
    point: LinkPoint,
    pointStyle: Style[],
    isGeometryAlignment: boolean,
    parentPoint?: LinkPoint,
): Feature<Point> {
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(point)),
    });

    pointFeature.setStyle(pointStyle);
    pointFeature.set(FEATURE_PROPERTY_LINK_POINT, parentPoint ? parentPoint : point);
    pointFeature.set(FEATURE_PROPERTY_TYPE, isGeometryAlignment ? 'geometry' : 'layout');

    return pointFeature;
}

function getClusterPointStyle(
    clusterPoint: ClusterPoint,
    alignmentInterval: LinkInterval,
    geometryInterval: LinkInterval,
): Style | undefined {
    const alignmentFound =
        clusterPoint.layoutPoint.id === alignmentInterval.start?.id ||
        clusterPoint.layoutPoint.id === alignmentInterval.end?.id;

    const geometryFound =
        clusterPoint.geometryPoint.id === geometryInterval.start?.id ||
        clusterPoint.geometryPoint.id === geometryInterval.end?.id;

    if (alignmentFound && geometryFound) return clusterPointBothSelectedStyle;
    else if (!alignmentFound && !geometryFound) return clusterPointStyle;
    else if (alignmentFound) return clusterPointLayoutSelectedStyle;
    else if (geometryFound) return clusterPointGeometrySelectedStyle;
}

function createClusterPointFeatures(
    clusterPoints: ClusterPoint[] | undefined,
    clusterPointUnselectedStyle: Style,
    alignmentInterval: LinkInterval,
    geometryInterval: LinkInterval,
): Feature<Point>[] {
    return (
        clusterPoints?.map((point) => {
            const clusterPointStyle = getClusterPointStyle(
                point,
                alignmentInterval,
                geometryInterval,
            );

            return createClusterPointFeature(
                point,
                clusterPointStyle ? [clusterPointStyle] : [clusterPointUnselectedStyle],
            );
        }) ?? []
    );
}

function createPointLineFeatures(
    points: LinkPoint[],
    clusterPoints: LinkPoint[],
    segmentStyle: Style,
    pointStyleFunc: (
        point: LinkPoint,
        anotherPoint: LinkPoint | undefined,
        isEndPoint: boolean,
    ) => Style[],
    isGeometryAlignment: boolean,
): Feature<Point | LineString>[] {
    const features: Feature<Point | LineString>[] = [];
    points.forEach((point, index) => {
        const nextPoint = points[index + 1];

        if (nextPoint) {
            const segmentFeature = createLineFeature(point, nextPoint, segmentStyle);
            features.push(segmentFeature);
        }

        if (!clusterPoints.find((cPoint) => cPoint.id === point.id)) {
            const pointStyles = pointStyleFunc(
                point,
                nextPoint ? nextPoint : points[index - 1],
                index == 0 || index == points.length - 1,
            );

            const pointFeature = createPointFeature(point, pointStyles, isGeometryAlignment);
            features.push(pointFeature);
        }
    });

    return features;
}

function getStyleForSegmentTicksIfNeeded(
    point: LinkPoint,
    controlPoint: LinkPoint | undefined,
    style: Style,
) {
    return point.isSegmentEndPoint && controlPoint
        ? getStyleForSegmentTicks(point, controlPoint, style)
        : undefined;
}

function getStyleForSegmentTicks(point1: LinkPoint, point2: LinkPoint, style: Style) {
    return getTickStyle(pointToCoords(point1), pointToCoords(point2), 6, 'start', style);
}

function getPointsByOrder(
    allPoints: LinkPoint[],
    orderStart?: number,
    orderEnd?: number,
): LinkPoint[] {
    if (allPoints.length == 0 || orderStart == undefined || orderEnd == undefined) return [];
    else if (orderEnd < allPoints[0].m || orderStart > allPoints[allPoints.length - 1].m) return [];
    else return allPoints.filter((p) => p.m >= orderStart && p.m <= orderEnd);
}

function createAlignmentFeatures(
    points: LinkPoint[],
    highlightedLinkPoint: LinkPoint,
    clusterPoints: LinkPoint[],
    linkInterval: LinkInterval,
    showAllLinkingPoints: boolean,
    isGeometryAlignment: boolean,
    pointUnselectedStyle: Style,
    lineUnselectedStyle: Style,
    pointSelectedStyle: Style,
    pointSelectedLargeStyle: Style,
    lineSelectedStyle: Style,
    lineSelectedHighlightedStyle: Style,
): Feature<Point | LineString>[] {
    const selectedInterval = linkInterval;
    const selectedIntervalStart = selectedInterval.start?.m;
    const selectedIntervalEnd = selectedInterval.end?.m || selectedIntervalStart;

    const highlightedInterval = highlightedLinkPoint
        ? createUpdatedInterval(selectedInterval, highlightedLinkPoint, true)
        : selectedInterval;
    const highlightedIntervalStart = highlightedInterval.start?.m;
    const highlightedIntervalEnd = highlightedInterval.end?.m || highlightedIntervalStart;

    const beforeSelectionPoints = getPointsByOrder(points, 0, selectedIntervalStart || Infinity);
    const afterSelectionPoints = getPointsByOrder(points, selectedIntervalEnd, Infinity);
    const highlightedPoints =
        highlightedIntervalStart === selectedIntervalStart &&
        highlightedIntervalEnd === selectedIntervalEnd
            ? []
            : getPointsByOrder(points, highlightedIntervalStart, highlightedIntervalEnd);
    const selectedPoints = getPointsByOrder(points, selectedIntervalStart, selectedIntervalEnd);

    return [
        // First leftover line
        ...createPointLineFeatures(
            beforeSelectionPoints,
            clusterPoints,
            lineUnselectedStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showAllLinkingPoints ? pointUnselectedStyle : undefined,
                    getStyleForSegmentTicksIfNeeded(point, controlPoint, lineUnselectedStyle),
                ),
            isGeometryAlignment,
        ),
        // Second leftover line
        ...createPointLineFeatures(
            afterSelectionPoints,
            clusterPoints,
            lineUnselectedStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showAllLinkingPoints ? pointUnselectedStyle : undefined,
                    getStyleForSegmentTicksIfNeeded(point, controlPoint, lineUnselectedStyle),
                ),
            isGeometryAlignment,
        ),
        // Highlighted interval line
        ...createPointLineFeatures(
            highlightedPoints,
            clusterPoints,
            lineSelectedHighlightedStyle,
            (point, controlPoint) =>
                nonEmptyArray(
                    showAllLinkingPoints ? pointSelectedStyle : undefined,
                    getStyleForSegmentTicksIfNeeded(
                        point,
                        controlPoint,
                        lineSelectedHighlightedStyle,
                    ),
                ),
            isGeometryAlignment,
        ),
        // Selected interval line
        ...createPointLineFeatures(
            selectedPoints,
            clusterPoints,
            lineSelectedStyle,
            (point, controlPoint, isEndPoint) => {
                const pointStyle = isEndPoint ? pointSelectedLargeStyle : pointSelectedStyle;
                return nonEmptyArray(
                    showAllLinkingPoints ? pointStyle : undefined,
                    getStyleForSegmentTicksIfNeeded(
                        point,
                        controlPoint,
                        lineSelectedHighlightedStyle,
                    ),
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

function createConnectingLineFeature(
    start: LinkPoint,
    end: LinkPoint,
): Feature<Point | LineString> {
    const linePoints = [start, end].map(pointToCoords);
    const lineString = new LineString(linePoints);
    const feature = new Feature({ geometry: lineString });
    feature.setStyle(connectingLineStyle);
    return feature;
}

function createLinkingAlignmentFeatures(
    selection: Selection,
    points: LinkPoint[],
    alignmentInterval: LinkInterval,
    resolution: number,
): Feature<LineString | Point>[] {
    return createAlignmentFeatures(
        points,
        selection.highlightedItems.layoutLinkPoints[0],
        [],
        alignmentInterval,
        resolution <= LINKING_DOTS,
        false,
        layoutPointSelectedStyle,
        layoutAlignmentStyle,
        layoutPointSelectedStyle,
        layoutPointSelectedLargeStyle,
        layoutAlignmentSelectedStyle,
        layoutAlignmentHighlightedStyle,
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
    alignmentInterval: LinkInterval,
    geometryInterval: LinkInterval,
    resolution: number,
    layoutPoints: LinkPoint[],
    geometryPoints: LinkPoint[],
): Feature<Point | LineString>[] {
    const features: Feature<Point | LineString>[] = [];
    const [clusterPoints, overlappingPoints] = getClusterPoints(layoutPoints, geometryPoints);
    const renderLinkingDots = resolution <= LINKING_DOTS;

    if (renderLinkingDots) {
        features.push(
            ...createClusterPointFeatures(
                clusterPoints,
                clusterPointStyle,
                alignmentInterval,
                geometryInterval,
            ),
        );
    }

    features.push(
        ...createAlignmentFeatures(
            layoutPoints,
            selection.highlightedItems.layoutLinkPoints[0],
            overlappingPoints,
            alignmentInterval,
            renderLinkingDots,
            false,
            layoutPointStyle,
            layoutAlignmentStyle,
            layoutPointSelectedStyle,
            layoutPointSelectedLargeStyle,
            layoutAlignmentSelectedStyle,
            layoutAlignmentHighlightedStyle,
        ),
    );

    features.push(
        ...createAlignmentFeatures(
            geometryPoints,
            selection.highlightedItems.geometryLinkPoints[0],
            overlappingPoints,
            geometryInterval,
            resolution <= LINKING_DOTS,
            true,
            geometryPointStyle,
            geometryAlignmentStyle,
            geometryPointSelectedStyle,
            geometryPointSelectedLargeStyle,
            geometryAlignmentSelectedStyle,
            geometryAlignmentHighlightedStyle,
        ),
    );

    if (
        alignmentInterval.start &&
        geometryInterval.start &&
        alignmentInterval.start.id !== alignmentInterval?.end?.id &&
        !alignmentInterval.start.isEndPoint
    ) {
        features.push(createConnectingLineFeature(alignmentInterval.start, geometryInterval.start));
    }

    if (
        alignmentInterval.end &&
        geometryInterval.end &&
        alignmentInterval?.start?.id !== alignmentInterval.end.id &&
        !alignmentInterval.end.isEndPoint
    ) {
        features.push(createConnectingLineFeature(alignmentInterval.end, geometryInterval.end));
    }

    if (
        alignmentInterval.start?.id === alignmentInterval?.end?.id &&
        alignmentInterval.end?.isEndPoint &&
        alignmentInterval.start?.isEndPoint &&
        geometryInterval.start &&
        geometryInterval.end
    ) {
        if (alignmentInterval.start.m === 0) {
            features.push(
                createConnectingLineFeature(alignmentInterval.start, geometryInterval.end),
            );
        } else {
            features.push(
                createConnectingLineFeature(alignmentInterval.end, geometryInterval.start),
            );
        }
    }

    return features;
}

let newestLayerId = 0;

export function createLinkingLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Point | LineString>> | undefined,
    selection: Selection,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (linkingState?.state === 'setup' || linkingState?.state === 'allSet') {
        if (linkingState.type === LinkingType.LinkingAlignment) {
            const changeTime = getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            );

            getLinkPointsByTiles(
                changeTime,
                mapTiles,
                linkingState.layoutAlignmentId,
                linkingState.layoutAlignmentType,
            )
                .then((points) => {
                    if (layerId !== newestLayerId) return;
                    const features = createLinkingAlignmentFeatures(
                        selection,
                        points,
                        linkingState.layoutAlignmentInterval,
                        resolution,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource));
        } else if (linkingState.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
            getGeometryLinkPointsByTiles(
                linkingState.geometryPlanId,
                linkingState.geometryAlignmentId,
                mapTiles,
                [
                    linkingState.geometryAlignmentInterval.start,
                    linkingState.geometryAlignmentInterval.end,
                ].filter(filterNotEmpty),
            )
                .then((points) =>
                    createAlignmentFeatures(
                        points,
                        selection.highlightedItems.geometryLinkPoints[0],
                        [],
                        linkingState.geometryAlignmentInterval,
                        resolution <= LINKING_DOTS,
                        true,
                        geometryPointStyle,
                        geometryAlignmentStyle,
                        geometryPointSelectedStyle,
                        geometryPointSelectedLargeStyle,
                        geometryAlignmentSelectedStyle,
                        geometryAlignmentHighlightedStyle,
                    ),
                )
                .then((features) => {
                    if (layerId !== newestLayerId) return;

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource));
        } else if (linkingState?.type === LinkingType.LinkingGeometryWithAlignment) {
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
                    return createLinkingGeometryWithAlignmentFeatures(
                        selection,
                        linkingState.layoutAlignmentInterval,
                        linkingState.geometryAlignmentInterval,
                        resolution,
                        layoutPoints,
                        geometryPoints,
                    );
                })
                .then((features) => {
                    if (layerId !== newestLayerId) return;

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource));
        } else {
            clearFeatures(vectorSource);
        }
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'linking-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const matchOptions: MatchOptions = {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: options.limit,
            };
            const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
            const clusterPoints = getMatchingEntities<ClusterPoint>(
                hitArea,
                features,
                FEATURE_PROPERTY_CLUSTER_POINT,
                matchOptions,
            );
            return {
                layoutLinkPoints: getMatchingLinkPoints(hitArea, 'layout', features, matchOptions),
                geometryLinkPoints: getMatchingLinkPoints(
                    hitArea,
                    'geometry',
                    features,
                    matchOptions,
                ),
                clusterPoints: clusterPoints,
            };
        },
    };
}
