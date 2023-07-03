import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Circle, Fill, Stroke, Style, Text } from 'ol/style';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    centroid,
    clearFeatures,
    getIntersectingFeatures,
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
import {
    LocationTrackId,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getReferenceLine } from 'track-layout/layout-reference-line-api';
import { getAddress } from 'common/geocoding-api';
import { formatTrackMeter } from 'utils/geography-utils';

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

function createClusterPointFeature(clusterPoint: ClusterPoint, pointStyle: Style): Feature<Point> {
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(clusterPoint)),
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
): Feature<Point> {
    const pointFeature = new Feature({
        geometry: new Point(pointToCoords(point)),
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
): Feature<Point>[] {
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
): Feature<Point | LineString>[] {
    const features: Feature<Point | LineString>[] = [];
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

function createPointTagFeature(point: LinkPoint, pointType: 'layout' | 'geometry'): Feature<Point> {
    const color =
        pointType == 'geometry'
            ? mapStyles.selectedGeometryAlignmentInterval
            : mapStyles.selectedLayoutAlignmentInterval;

    const feature = new Feature({
        geometry: new Point(pointToCoords(point)),
    });

    const showAtLeftSide = pointType == 'geometry';
    const rotationByPointDirection = point.direction ? -point.direction + Math.PI / 2 : 0;
    const rotation = rotationByPointDirection + (showAtLeftSide ? Math.PI : 0);

    const renderer = ([x, y]: Coordinate, { pixelRatio, context }: State) => {
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

        const textX = arrowSpacing + arrowTipLength + textPadding;

        const arrowFormPolygon = [
            [0, 0],
            [arrowTipLength, arrowHeight / 2],
            [arrowLength, arrowHeight / 2],
            [arrowLength, -arrowHeight / 2],
            [arrowTipLength, -arrowHeight / 2],
        ];

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.moveTo(x + arrowSpacing + arrowFormPolygon[0][0], y + arrowFormPolygon[0][1]);
        arrowFormPolygon
            .slice(1)
            .forEach((coordinate) =>
                ctx.lineTo(x + arrowSpacing + coordinate[0], y + coordinate[1]),
            );
        ctx.closePath();
        ctx.fill();

        if (rotation > Math.PI / 2 || rotation < -Math.PI / 2) {
            // flip text
            const textCenterX = x + textX + textWidth / 2;
            ctx.translate(textCenterX, y);
            ctx.scale(-1, -1);
            ctx.translate(-textCenterX, -y);
        }

        ctx.fillStyle = '#fff';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, x + textX, y);

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer }));

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
): Feature<Point | LineString>[] {
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
        ...(selectedLinkInterval.start
            ? [
                  createPointTagFeature(
                      selectedLinkInterval.start,
                      isGeometryAlignment ? 'geometry' : 'layout',
                  ),
              ]
            : []),
        ...(selectedLinkInterval.end
            ? [
                  createPointTagFeature(
                      selectedLinkInterval.end,
                      isGeometryAlignment ? 'geometry' : 'layout',
                  ),
              ]
            : []),
        ...(highlightLinkPoint
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
): Feature<LineString | Point>[] {
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
): Feature<Point | LineString>[] {
    const features: Feature<Point | LineString>[] = [];
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

type LinkPointContainer = { [k: string]: LinkPoint | undefined };

async function getLinkPointsWithAddresses<
    T extends LinkPointContainer,
    TPropertyName extends keyof T,
>(
    layoutAlignmentType: MapAlignmentType,
    layoutAlignmentId: LocationTrackId | ReferenceLineId,
    points: T,
): Promise<T> {
    const trackNumberId = await (layoutAlignmentType == 'LOCATION_TRACK'
        ? getLocationTrack(layoutAlignmentId, 'DRAFT').then(
              (locationTrack) => locationTrack?.trackNumberId,
          )
        : getReferenceLine(layoutAlignmentId, 'DRAFT').then(
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
                ? getAddress(trackNumberId, originalPoint, 'DRAFT').then((address) => ({
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

let newestLayerId = 0;

export function createAlignmentLinkingLayer(
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
    const drawLinkingDots = resolution <= LINKING_DOTS;

    if (linkingState?.state === 'setup' || linkingState?.state === 'allSet') {
        if (linkingState.type === LinkingType.LinkingAlignment) {
            const changeTime = getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            );

            Promise.all([
                getLinkPointsByTiles(
                    changeTime,
                    mapTiles,
                    linkingState.layoutAlignmentId,
                    linkingState.layoutAlignmentType,
                ),
                getLinkPointsWithAddresses(
                    linkingState.layoutAlignmentType,
                    linkingState.layoutAlignmentId,
                    {
                        layoutStart: linkingState.layoutAlignmentInterval.start,
                        layoutEnd: linkingState.layoutAlignmentInterval.end,
                        layoutHighlight: selection.highlightedItems.layoutLinkPoints[0],
                    },
                ),
            ])
                .then(([points, linkPointAddresses]) => {
                    if (layerId !== newestLayerId) return;
                    const features = createLinkingAlignmentFeatures(
                        points,
                        {
                            ...linkingState.layoutAlignmentInterval,
                            start: linkPointAddresses.layoutStart,
                            end: linkPointAddresses.layoutEnd,
                        },
                        linkPointAddresses.layoutHighlight,
                        drawLinkingDots,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                })
                .catch(() => clearFeatures(vectorSource));
        } else if (linkingState.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
            Promise.all([
                getGeometryLinkPointsByTiles(
                    linkingState.geometryPlanId,
                    linkingState.geometryAlignmentId,
                    mapTiles,
                    [
                        linkingState.geometryAlignmentInterval.start,
                        linkingState.geometryAlignmentInterval.end,
                    ].filter(filterNotEmpty),
                ),
                getLinkPointsWithAddresses(
                    linkingState.layoutAlignmentType,
                    linkingState.layoutAlignmentId,
                    {
                        geometryStart: linkingState.geometryAlignmentInterval.start,
                        geometryEnd: linkingState.geometryAlignmentInterval.end,
                        geometryHighlight: selection.highlightedItems.geometryLinkPoints[0],
                    },
                ),
            ])
                .then(([points, linkPointAddresses]) => {
                    if (layerId !== newestLayerId) return;

                    const features = createAlignmentFeatures(
                        points,
                        linkPointAddresses.geometryHighlight,
                        [],
                        {
                            ...linkingState.geometryAlignmentInterval,
                            start: linkPointAddresses.geometryStart,
                            end: linkPointAddresses.geometryEnd,
                        },
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
                .catch(() => clearFeatures(vectorSource));
        } else if (linkingState?.type === LinkingType.LinkingGeometryWithAlignment) {
            console.log('x');
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

            const linkPointAddressesPromise = getLinkPointsWithAddresses(
                linkingState.layoutAlignmentType,
                linkingState.layoutAlignmentId,
                {
                    layoutStart: linkingState.layoutAlignmentInterval.start,
                    layoutEnd: linkingState.layoutAlignmentInterval.end,
                    layoutHighlight: selection.highlightedItems.layoutLinkPoints[0],
                    geometryStart: linkingState.geometryAlignmentInterval.start,
                    geometryEnd: linkingState.geometryAlignmentInterval.end,
                    geometryHighlight: selection.highlightedItems.geometryLinkPoints[0],
                },
            );

            Promise.all([layoutPointsPromise, geometryPointsPromise, linkPointAddressesPromise])
                .then(([layoutPoints, geometryPoints, linkPointAddresses]) => {
                    if (layerId !== newestLayerId) return;

                    const features = createLinkingGeometryWithAlignmentFeatures(
                        {
                            ...selection,
                            highlightedItems: {
                                ...selection.highlightedItems,
                                layoutLinkPoints: [linkPointAddresses.layoutHighlight],
                                geometryLinkPoints: [linkPointAddresses.geometryHighlight],
                            },
                        },
                        {
                            ...linkingState.layoutAlignmentInterval,
                            start: linkPointAddresses.layoutStart,
                            end: linkPointAddresses.layoutEnd,
                        },
                        {
                            ...linkingState.geometryAlignmentInterval,
                            start: linkPointAddresses.geometryStart,
                            end: linkPointAddresses.geometryEnd,
                        },
                        drawLinkingDots,
                        layoutPoints,
                        geometryPoints,
                    );

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
        name: 'alignment-linking-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, _options: SearchItemsOptions): LayerItemSearchResult => {
            //If dots are not drawn, do not select anything
            if (!drawLinkingDots) {
                return {
                    layoutLinkPoints: [],
                    geometryLinkPoints: [],
                    clusterPoints: [],
                };
            }

            const features = getIntersectingFeatures<Point | LineString>(hitArea, vectorSource);
            const hitPoint = centroid(hitArea);

            const closestPointFeature = getSortedPointFeatures(features, hitPoint)[0];

            const clusterPoint = getIfType<ClusterPoint>(
                closestPointFeature,
                FeatureType.ClusterPoint,
            );
            const layoutPoint = getIfType<LinkPoint>(closestPointFeature, FeatureType.LayoutPoint);
            const geometryPoint = getIfType<LinkPoint>(
                closestPointFeature,
                FeatureType.GeometryPoint,
            );

            return {
                layoutLinkPoints:
                    layoutPoint && containsType(features, FeatureType.LayoutLine)
                        ? [layoutPoint]
                        : [],
                geometryLinkPoints:
                    geometryPoint && containsType(features, FeatureType.GeometryLine)
                        ? [geometryPoint]
                        : [],
                clusterPoints: clusterPoint ? [clusterPoint] : [],
            };
        },
    };
}

function containsType(features: Feature[], type: FeatureType): boolean {
    return features.some((f) => f.get(LINKING_FEATURE_TYPE_PROPERTY) === type);
}

function getIfType<T>(feature: Feature, type: FeatureType): T | undefined {
    return feature && feature.get(LINKING_FEATURE_TYPE_PROPERTY) === type
        ? (feature.get(LINKING_FEATURE_DATA_PROPERTY) as T)
        : undefined;
}

function getSortedPointFeatures(features: Feature[], hitArea: Point): Feature<Point>[] {
    const pointFeatures = features.filter((f) => {
        const type = f.get(LINKING_FEATURE_TYPE_PROPERTY);
        return (
            type === FeatureType.LayoutPoint ||
            type === FeatureType.GeometryPoint ||
            type === FeatureType.ClusterPoint
        );
    }) as Feature<Point>[];

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
