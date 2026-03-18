import Feature from 'ol/Feature';
import mapStyles from 'map/map.module.scss';
import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName } from 'map/map-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { Style } from 'ol/style';
import { filterNotEmpty } from 'utils/array-utils';
import { RouteLocation, RouteLocations } from 'track-layout/track-layout-slice';
import { Point } from 'model/geometry';
import { createIconBitmap, getCanvasRenderer } from 'map/layers/utils/rendering';
import { State } from 'ol/render';
import { Coordinate } from 'ol/coordinate';
import { expectCoordinate } from 'utils/type-utils';
import PinSolidSvg from 'vayla-design-lib/icon/glyphs/misc/pin-solid.svg';

const MARKER_ICON_SIZE_PX = 40;
const layerName: MapLayerName = 'route-marker-layer';

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
                const iconSize = MARKER_ICON_SIZE_PX;

                const sizeInPixels = iconSize * pixelRatio;
                const color =
                    markerState === RouteMarkerState.Pending
                        ? mapStyles['routeHoverColor']
                        : mapStyles['routeColor'];
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

export function createRouteMarkerLayer(
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    hoveredRouteLocation: RouteLocation | undefined,
    routeLocations: RouteLocations | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const createOlFeatures = () => [
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
    loadLayerData(source, isLatest, onLoadingData, Promise.resolve(), createOlFeatures);

    return { name: layerName, layer: layer };
}
