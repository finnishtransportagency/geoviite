import Feature from 'ol/Feature';
import mapStyles from 'map/map.module.scss';
import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext } from 'common/common-model';
import { RouteResult } from 'track-layout/layout-routing-api';
import { getSelectedLocationTrackMapAlignmentByTiles } from 'track-layout/layout-map-api';
import { getPartialPolyLine } from 'utils/math-utils';
import { Stroke, Style } from 'ol/style';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { RouteLocation, RouteLocations } from 'track-layout/track-layout-slice';
import { Point } from 'model/geometry';
import { createIconBitmap, getCanvasRenderer } from 'map/layers/utils/rendering';
import { State } from 'ol/render';
import { Coordinate } from 'ol/coordinate';
import { expectCoordinate } from 'utils/type-utils';
import PinSolidSvg from 'vayla-design-lib/icon/glyphs/misc/pin-solid.svg';

const routeSectionStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['routeColor'],
        width: 6,
    }),
});

// const connectorLineStyle = new Style({
//     stroke: new Stroke({
//         color: '#00AA00',
//         lineDash: [8, 4],
//         width: 2,
//     }),
// });
//
// const pointCircleStyle = new Style({
//     image: new CircleStyle({
//         radius: 6,
//         stroke: new Stroke({
//             color: '#00AA00',
//             width: 2,
//         }),
//     }),
// });

export enum RouteMarkerState {
    Pending,
    Final,
}

export function createRouteMarkerFeature(
    trackPoint: Point,
    markerState: RouteMarkerState,
): Feature<OlPoint> {
    const coordinateOnTrack = pointToCoords(trackPoint);

    const renderer = getCanvasRenderer(
        coordinateOnTrack,
        (_ctx: CanvasRenderingContext2D, _state: State) => {},
        [
            (
                _: Coordinate,
                coord: Coordinate,
                ctx: CanvasRenderingContext2D,
                { pixelRatio }: State,
            ) => {
                const [x, y] = expectCoordinate(coord);
                const iconSize = 40;

                const sizeInPixels = iconSize * pixelRatio;
                const color =
                    markerState === RouteMarkerState.Pending
                        ? mapStyles['routeHoverColor']
                        : mapStyles['routeColor'];
                // ctx.globalAlpha = markerState === RouteMarkerState.Pending ? 0.5 : 1;
                ctx.drawImage(
                    createIconBitmap(sizeInPixels, PinSolidSvg, color),
                    x - (iconSize / 2) * pixelRatio,
                    y - iconSize * pixelRatio,
                );
            },
        ],
    );

    const feature = new Feature({
        geometry: new OlPoint(coordinateOnTrack),
    });
    feature.setStyle(
        new Style({
            renderer: renderer,
        }),
    );
    return feature;
}

type RouteFeatures = {
    routeSections: Feature<LineString>[];
    connectors: Feature<LineString>[];
    points: Feature<OlPoint>[];
};

function createRoutLocationFeatures(routeLocations: RouteLocations): Feature<OlPoint>[] {
    return [
        routeLocations.start?.closestTrackPoint.trackLocation,
        routeLocations.end?.closestTrackPoint.trackLocation,
    ]
        .filter(filterNotEmpty)
        .map((point) => {
            return createRouteMarkerFeature(point, RouteMarkerState.Final);
        });
}

async function createRouteFeatures(
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    routeResult: RouteResult,
): Promise<RouteFeatures> {
    // Get unique track IDs from route sections
    const trackIds = deduplicate(routeResult.route.sections.map((s) => s.trackId));

    // Fetch alignment data for all tracks
    const alignmentDataPromises = trackIds.map((trackId) =>
        getSelectedLocationTrackMapAlignmentByTiles(
            changeTimes,
            mapTiles,
            layoutContext,
            trackId as LocationTrackId,
        ),
    );

    const alignmentDataArrays = await Promise.all(alignmentDataPromises);

    // Create a map of trackId -> alignment points
    const trackAlignmentMap = new Map();
    alignmentDataArrays.forEach((alignmentArray) => {
        if (alignmentArray.length > 0) {
            const alignment = alignmentArray[0];
            if (alignment) {
                trackAlignmentMap.set(alignment.header.id, alignment.points);
            }
        }
    });

    // Create features for each route section
    const routeSections = routeResult.route.sections
        .map((section) => {
            const points = trackAlignmentMap.get(section.trackId);
            if (points && points.length > 1) {
                const polyline = getPartialPolyLine(points, section.mRange.min, section.mRange.max);

                if (polyline.length > 1) {
                    const lineString = new LineString(polyline);
                    const feature = new Feature({ geometry: lineString });
                    feature.setStyle(routeSectionStyle);
                    return feature;
                }
            }
            return undefined;
        })
        .filter(filterNotEmpty);

    // // Create connector line from start clicked point to start track point
    // const startClickedCoords = pointToCoords(routeResult.startConnection.requestedLocation);
    // const startTrackCoords = pointToCoords(routeResult.startConnection.trackLocation);
    // const startConnector = new Feature({
    //     geometry: new LineString([startClickedCoords, startTrackCoords]),
    // });
    // startConnector.setStyle(connectorLineStyle);
    //
    // // Create connector line from end track point to end clicked point
    // const endTrackCoords = pointToCoords(routeResult.endConnection.trackLocation);
    // const endClickedCoords = pointToCoords(routeResult.endConnection.requestedLocation);
    // const endConnector = new Feature({
    //     geometry: new LineString([endTrackCoords, endClickedCoords]),
    // });
    // endConnector.setStyle(connectorLineStyle);
    //
    // // Create point circles at clicked locations
    // const startPoint = new Feature({ geometry: new OlPoint(startClickedCoords) });
    // startPoint.setStyle(pointCircleStyle);
    //
    // const endPoint = new Feature({ geometry: new OlPoint(endClickedCoords) });
    // endPoint.setStyle(pointCircleStyle);

    return {
        routeSections: routeSections,
        connectors: [],
        points: [],
        // connectors: [startConnector, endConnector],
        // points: [startPoint, endPoint],
    };
}

const layerName: MapLayerName = 'route-highlight-layer';

export function createRouteHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    _resolution: number,
    hoveredRouteLocation: RouteLocation | undefined,
    routeLocations: RouteLocations | undefined,
    routeResult: RouteResult | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    console.log('routeLocations in layer', routeLocations);

    const dataPromise: Promise<RouteFeatures> = routeResult
        ? createRouteFeatures(mapTiles, layoutContext, changeTimes, routeResult)
        : Promise.resolve({ routeSections: [], connectors: [], points: [] });

    const routeMarkers = [
        ...(routeLocations ? createRoutLocationFeatures(routeLocations) : []),
        ...(hoveredRouteLocation
            ? [
                  createRouteMarkerFeature(
                      hoveredRouteLocation.closestTrackPoint.trackLocation,
                      RouteMarkerState.Pending,
                  ),
              ]
            : []),
    ];

    const createOlFeatures = (features: RouteFeatures) =>
        [
            ...routeMarkers,
            ...features.connectors,
            ...features.routeSections,
            ...features.points,
        ].filter(filterNotEmpty);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createOlFeatures);

    return { name: layerName, layer: layer };
}
