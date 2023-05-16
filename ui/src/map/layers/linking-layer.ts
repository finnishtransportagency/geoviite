import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Circle, Fill, RegularShape, Stroke, Style, Text } from 'ol/style';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    getMatchingEntities,
    getMatchingLinkPoints,
    getPlanarDistanceUnwrapped,
    getTickStyle,
    MatchOptions,
    pointToCoords,
} from 'map/layers/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/layer-model';
import { LINKING_DOTS } from 'map/layers/layer-visibility-limits';
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
import { createGeometryLinkPointsByTiles, getLinkPointsByTiles } from 'track-layout/layout-map-api';
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
    zIndex: 10,
});

const geometryLineSelectedStyle = strokeStyle(mapStyles.selectedGeometryAlignmentInterval, 3, 11);
const geometryLineSelectedHighlightedStyle = strokeStyle(
    mapStyles.selectedGeometryAlignmentInterval,
    4,
    11,
);

const layoutLineSelectedHighlightedStyle = strokeStyle(
    mapStyles.unselectedAlignmentInterval,
    4,
    11,
);

const layoutLineSelectedHighlightedModifyStyle = strokeStyle(
    mapStyles.selectedLayoutAlignmentInterval,
    4,
    10,
);
const geometryLineUnselectedStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 10);

const geometryPointSelectedStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.selectedGeometryAlignmentInterval,
    linkPointRadius,
    15,
);

const geometryPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.selectedGeometryAlignmentInterval,
    LinkPointSelectedRadius,
    15,
);

const layoutPointSelectedLargeStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.selectedLayoutAlignmentInterval,
    LinkPointSelectedRadius,
    15,
);

const geometryPointUnselectedStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    15,
);

const layoutPointSelectedStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    15,
);

const layoutPointUnselectedModifyStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.unselectedAlignmentInterval,
    linkPointRadius,
    15,
);

const layoutLineUnselectedStyle = strokeStyle(mapStyles.selectedLayoutAlignmentInterval, 3, 11);
const layoutLineSelectedModifyStyle = strokeStyle(mapStyles.selectedLayoutAlignmentInterval, 3, 10);
const layoutLineSelectedStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 10);
const layoutLineUnselectedModifyStyle = strokeStyle(mapStyles.unselectedAlignmentInterval, 3, 10);

const layoutPointUnselectedStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointRadius,
    15,
);

const layoutPointSelectedModifyStyle = pointStyle(
    mapStyles.linkingPointStrokeColor,
    mapStyles.selectedLayoutAlignmentInterval,
    linkPointRadius,
    15,
);

const clusterPointUnSelectedStyle = new Style({
    text: new Text({
        text: '?',
        scale: 1.2,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointRadius,
        stroke: new Stroke({ color: mapStyles.clusterPointStrokeColor }),
        fill: new Fill({ color: mapStyles.clusterPointFillColor }),
    }),
    zIndex: 19,
});

const clusterPointBothSelectedStyle = new Style({
    text: new Text({
        text: '2',
        scale: 1.3,
        fill: new Fill({ color: mapStyles.clusterPointTextColor }),
    }),
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPointStrokeColor }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: 19,
});

const clusterPointGeomSelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPointStrokeColor }),
        fill: new Fill({ color: mapStyles.selectedGeometryAlignmentInterval }),
    }),
    zIndex: 19,
});

const clusterPointLayoutSelectedStyle = new Style({
    image: new Circle({
        radius: clusterLinkPointSelectedRadius,
        stroke: new Stroke({ color: mapStyles.linkingPointStrokeColor }),
        fill: new Fill({ color: mapStyles.selectedLayoutAlignmentInterval }),
    }),
    zIndex: 19,
});

export const endPointStyle = [
    new Style({
        image: new Circle({
            radius: 6,
            fill: new Fill({ color: mapStyles.locationTrackEndPoint }),
            stroke: new Stroke({ color: mapStyles.locationTrackEndPointInnerCircle }),
        }),
        zIndex: 17,
    }),
    new Style({
        image: new RegularShape({
            stroke: new Stroke({ color: mapStyles.locationTrackEndPointCross }),
            points: 4,
            radius: 4,
            radius2: 0,
            angle: 0,
        }),
        zIndex: 17,
    }),
];

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

function createClusterPointFeature(clusterPoint: ClusterPoint, pointStyle: Style[]) {
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(clusterPoint)),
    });
    pointFeature.setStyle(pointStyle);
    pointFeature.set(FEATURE_PROPERTY_TYPE, 'cluster');
    pointFeature.set(FEATURE_PROPERTY_LINK_POINT, undefined);
    pointFeature.set(FEATURE_PROPERTY_CLUSTER_POINT, clusterPoint);

    return pointFeature;
}

function createPointFeature(
    point: LinkPoint,
    pointStyle: Style[],
    isGeometryAlignment: boolean,
    parentPoint?: LinkPoint,
) {
    const type = isGeometryAlignment ? 'geometry' : 'layout';
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(point)),
    });
    pointFeature.setStyle(pointStyle);
    pointFeature.set(FEATURE_PROPERTY_LINK_POINT, parentPoint ? parentPoint : point);
    pointFeature.set(FEATURE_PROPERTY_TYPE, type);
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
    else if (!alignmentFound && !geometryFound) return clusterPointUnSelectedStyle;
    else if (alignmentFound) return clusterPointLayoutSelectedStyle;
    else if (geometryFound) return clusterPointGeomSelectedStyle;
}

function createFeaturesForClusteredPoints(
    clusterPoints: ClusterPoint[] | undefined,
    clusterPointUnSelectedStyle: Style,
    alignmentInterval: LinkInterval,
    geometryInterval: LinkInterval,
): Feature<Point>[] {
    const perPointFeatures: Feature<Point>[] = [];
    if (clusterPoints && clusterPoints?.length > 0) {
        for (const i in clusterPoints) {
            const clusterPointStyle = getClusterPointStyle(
                clusterPoints[i],
                alignmentInterval,
                geometryInterval,
            );
            const clusterPoint = createClusterPointFeature(
                clusterPoints[i],
                clusterPointStyle ? [clusterPointStyle] : [clusterPointUnSelectedStyle],
            );

            perPointFeatures.push(clusterPoint);
        }
    }
    return perPointFeatures;
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
    const perPointFeatures: Feature<Point | LineString>[] = [];
    points.forEach((point, index) => {
        const nextPoint = points[index + 1];

        if (nextPoint) {
            const segmentFeature = createLineFeature(point, nextPoint, segmentStyle);
            perPointFeatures.push(segmentFeature);
        }

        if (!clusterPoints.find((cPoint) => cPoint.id === point.id)) {
            const pointStyle = pointStyleFunc(
                point,
                nextPoint ? nextPoint : points[index - 1],
                index == 0 || index == points.length - 1,
            );

            const pointFeature = createPointFeature(point, pointStyle, isGeometryAlignment);
            perPointFeatures.push(pointFeature);
        }
    });

    return perPointFeatures;
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

function createFeaturesForAlignment(
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

    const highlightedInterval =
        highlightedLinkPoint != undefined
            ? createUpdatedInterval(selectedInterval, highlightedLinkPoint, true)
            : selectedInterval;
    const highlightedIntervalStart = highlightedInterval.start?.m;
    const highlightedIntervalEnd = highlightedInterval.end?.m || highlightedIntervalStart;

    const beforeSelectionPoints = getPointsByOrder(points, 0, selectedIntervalStart || Infinity);
    const afterSelectionPoints = getPointsByOrder(points, selectedIntervalEnd, Infinity);
    const highlightedPoints =
        highlightedIntervalStart != selectedIntervalStart ||
        highlightedIntervalEnd != selectedIntervalEnd
            ? getPointsByOrder(points, highlightedIntervalStart, highlightedIntervalEnd)
            : [];
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

function pointsOverlapping(
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

function createConnectingLine(start: LinkPoint, end: LinkPoint): Feature<Point | LineString> {
    const linePoints = [start, end].map(pointToCoords);
    const lineString = new LineString(linePoints);
    const feature = new Feature({ geometry: lineString });
    feature.setStyle([connectingLineStyle]);
    return feature;
}

function createFeaturesWhenUpdatingLayoutAlignment(
    selection: Selection,
    points: LinkPoint[],
    alignmentInterval: LinkInterval,
    resolution: number,
) {
    return createFeaturesForAlignment(
        points,
        selection.highlightedItems.layoutLinkPoints[0],
        [],
        alignmentInterval,
        resolution <= LINKING_DOTS,
        false,
        layoutPointUnselectedModifyStyle,
        layoutLineUnselectedModifyStyle,
        layoutPointSelectedModifyStyle,
        layoutPointSelectedLargeStyle,
        layoutLineSelectedModifyStyle,
        layoutLineSelectedHighlightedModifyStyle,
    );
}

function createClusterPoints(
    layoutPoints: LinkPoint[],
    geometryPoints: LinkPoint[],
): [ClusterPoint[], LinkPoint[]] {
    const clusterPoints: ClusterPoint[] = [];
    const overlappingPoints: LinkPoint[] = [];
    layoutPoints.map((layoutPoint) => {
        geometryPoints.find((geometryPoint) => {
            const clusterPoint = pointsOverlapping(layoutPoint, geometryPoint);
            if (clusterPoint != undefined) {
                clusterPoints.push(clusterPoint);
                overlappingPoints.push(layoutPoint);
                overlappingPoints.push(geometryPoint);
            }
        });
    });

    return [clusterPoints, overlappingPoints];
}

function createFeaturesWhenLinkingGeometryWithLayoutAlignment(
    selection: Selection,
    alignmentInterval: LinkInterval,
    geometryInterval: LinkInterval,
    resolution: number,
    layoutPoints: LinkPoint[],
    geometryPoints: LinkPoint[],
): Feature<Point | LineString>[] {
    const allFeatures: Feature<Point | LineString>[] = [];
    const [clusterPoints, overlappingPoints] = createClusterPoints(layoutPoints, geometryPoints);
    const renderLinkingDots = resolution <= LINKING_DOTS;

    if (renderLinkingDots) {
        allFeatures.push(
            ...createFeaturesForClusteredPoints(
                clusterPoints,
                clusterPointUnSelectedStyle,
                alignmentInterval,
                geometryInterval,
            ),
        );
    }

    allFeatures.push(
        ...createFeaturesForAlignment(
            layoutPoints,
            selection.highlightedItems.layoutLinkPoints[0],
            overlappingPoints,
            alignmentInterval,
            renderLinkingDots,
            false,
            layoutPointUnselectedStyle,
            layoutLineUnselectedStyle,
            layoutPointSelectedStyle,
            layoutPointSelectedLargeStyle,
            layoutLineSelectedStyle,
            layoutLineSelectedHighlightedStyle,
        ),
    );

    allFeatures.push(
        ...createFeaturesForAlignment(
            geometryPoints,
            selection.highlightedItems.geometryLinkPoints[0],
            overlappingPoints,
            geometryInterval,
            resolution <= LINKING_DOTS,
            true,
            geometryPointUnselectedStyle,
            geometryLineUnselectedStyle,
            geometryPointSelectedStyle,
            geometryPointSelectedLargeStyle,
            geometryLineSelectedStyle,
            geometryLineSelectedHighlightedStyle,
        ),
    );

    if (
        alignmentInterval.start &&
        geometryInterval.start &&
        alignmentInterval.start.id !== alignmentInterval?.end?.id &&
        !alignmentInterval.start.isEndPoint
    ) {
        allFeatures.push(createConnectingLine(alignmentInterval.start, geometryInterval.start));
    }

    if (
        alignmentInterval.end &&
        geometryInterval.end &&
        alignmentInterval?.start?.id !== alignmentInterval.end.id &&
        !alignmentInterval.end.isEndPoint
    ) {
        allFeatures.push(createConnectingLine(alignmentInterval.end, geometryInterval.end));
    }

    if (
        alignmentInterval.start?.id === alignmentInterval?.end?.id &&
        alignmentInterval.end?.isEndPoint &&
        alignmentInterval.start?.isEndPoint &&
        geometryInterval.start &&
        geometryInterval.end
    ) {
        if (alignmentInterval.start.m === 0) {
            allFeatures.push(createConnectingLine(alignmentInterval.start, geometryInterval.end));
        } else {
            allFeatures.push(createConnectingLine(alignmentInterval.end, geometryInterval.start));
        }
    }

    return allFeatures;
}

let newestLinkingLayerId = 0;

export function createLinkingLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Point | LineString>> | undefined,
    selection: Selection,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLinkingLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    const clearFeatures = () => {
        vectorSource.clear;
    };

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
                    if (layerId != newestLinkingLayerId) return;
                    const allFeatures = createFeaturesWhenUpdatingLayoutAlignment(
                        selection,
                        points,
                        linkingState.layoutAlignmentInterval,
                        resolution,
                    );

                    clearFeatures();
                    vectorSource.addFeatures(allFeatures);
                })
                .catch(clearFeatures);
        } else if (linkingState.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
            const geometryPlanId = linkingState.geometryPlanId;
            const geometryAlignmentId = linkingState.geometryAlignmentId;

            const geometryPointsPromise = createGeometryLinkPointsByTiles(
                geometryPlanId,
                geometryAlignmentId,
                mapTiles,
                [
                    linkingState.geometryAlignmentInterval.start,
                    linkingState.geometryAlignmentInterval.end,
                ].filter(filterNotEmpty),
            );

            geometryPointsPromise
                .then((points) =>
                    createFeaturesForAlignment(
                        points,
                        selection.highlightedItems.geometryLinkPoints[0],
                        [],
                        linkingState.geometryAlignmentInterval,
                        resolution <= LINKING_DOTS,
                        true,
                        geometryPointUnselectedStyle,
                        geometryLineUnselectedStyle,
                        geometryPointSelectedStyle,
                        geometryPointSelectedLargeStyle,
                        geometryLineSelectedStyle,
                        geometryLineSelectedHighlightedStyle,
                    ),
                )
                .then((features) => {
                    if (layerId != newestLinkingLayerId) return;

                    clearFeatures();
                    vectorSource.addFeatures(features);
                })
                .catch(clearFeatures);
        } else if (linkingState?.type === LinkingType.LinkingGeometryWithAlignment) {
            const alignmentId = linkingState.layoutAlignmentId;
            const geometryPlanId = linkingState.geometryPlanId;
            const geometryAlignmentId = linkingState.geometryAlignmentId;

            const geometryPointsPromise = createGeometryLinkPointsByTiles(
                geometryPlanId,
                geometryAlignmentId,
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
                alignmentId,
                linkingState.layoutAlignmentType,
            );

            Promise.all([layoutPointsPromise, geometryPointsPromise])
                .then(([layoutPoints, geometryPoints]) => {
                    return createFeaturesWhenLinkingGeometryWithLayoutAlignment(
                        selection,
                        linkingState.layoutAlignmentInterval,
                        linkingState.geometryAlignmentInterval,
                        resolution,
                        layoutPoints,
                        geometryPoints,
                    );
                })
                .then((features) => {
                    if (layerId != newestLinkingLayerId) return;

                    clearFeatures();
                    vectorSource.addFeatures(features);
                })
                .catch(clearFeatures);
        } else {
            clearFeatures();
        }
    } else {
        clearFeatures();
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
