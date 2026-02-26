import Feature from 'ol/Feature';
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
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { LayoutContext } from 'common/common-model';
import { RouteResult } from 'track-layout/layout-routing-api';
import { getSelectedLocationTrackMapAlignmentByTiles } from 'track-layout/layout-map-api';
import { getPartialPolyLine } from 'utils/math-utils';
import { Stroke, Style } from 'ol/style';
import CircleStyle from 'ol/style/Circle';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';

const routeSectionStyle = new Style({
    stroke: new Stroke({
        color: '#00AA00',
        width: 6,
    }),
});

const connectorLineStyle = new Style({
    stroke: new Stroke({
        color: '#00AA00',
        lineDash: [8, 4],
        width: 2,
    }),
});

const pointCircleStyle = new Style({
    image: new CircleStyle({
        radius: 6,
        stroke: new Stroke({
            color: '#00AA00',
            width: 2,
        }),
    }),
});

type RouteFeatures = {
    routeSections: Feature<LineString>[];
    connectors: Feature<LineString>[];
    points: Feature<OlPoint>[];
};

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

    // Create connector line from start clicked point to start track point
    const startClickedCoords = pointToCoords(routeResult.startConnection.requestedLocation);
    const startTrackCoords = pointToCoords(routeResult.startConnection.trackLocation);
    const startConnector = new Feature({
        geometry: new LineString([startClickedCoords, startTrackCoords]),
    });
    startConnector.setStyle(connectorLineStyle);

    // Create connector line from end track point to end clicked point
    const endTrackCoords = pointToCoords(routeResult.endConnection.trackLocation);
    const endClickedCoords = pointToCoords(routeResult.endConnection.requestedLocation);
    const endConnector = new Feature({
        geometry: new LineString([endTrackCoords, endClickedCoords]),
    });
    endConnector.setStyle(connectorLineStyle);

    // Create point circles at clicked locations
    const startPoint = new Feature({ geometry: new OlPoint(startClickedCoords) });
    startPoint.setStyle(pointCircleStyle);

    const endPoint = new Feature({ geometry: new OlPoint(endClickedCoords) });
    endPoint.setStyle(pointCircleStyle);

    return {
        routeSections: routeSections,
        connectors: [startConnector, endConnector],
        points: [startPoint, endPoint],
    };
}

const layerName: MapLayerName = 'route-highlight-layer';

export function createRouteHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    routeResult: RouteResult | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<RouteFeatures> =
        resolution <= HIGHLIGHTS_SHOW && routeResult
            ? createRouteFeatures(mapTiles, layoutContext, changeTimes, routeResult)
            : Promise.resolve({ routeSections: [], connectors: [], points: [] });

    const createOlFeatures = (features: RouteFeatures) =>
        [...features.connectors, ...features.routeSections, ...features.points].filter(
            filterNotEmpty,
        );

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createOlFeatures);

    return { name: layerName, layer: layer };
}
