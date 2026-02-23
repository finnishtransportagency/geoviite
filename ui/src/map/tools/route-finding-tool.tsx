import * as React from 'react';
import { MapToolWithButton } from 'map/tools/tool-model';
import OlMap from 'ol/Map';
import { Stroke, Style } from 'ol/style';
import CircleStyle from 'ol/style/Circle';
import { LineString } from 'ol/geom';
import { Coordinate, distance } from 'ol/coordinate';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';
import mapStyles from '../map.module.scss';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import { debounce } from 'ts-debounce';
import { coordsToPoint } from 'model/geometry';
import { LayoutContext } from 'common/common-model';
import { ClosestTrackPoint, getClosestTrackPoint, getRoute } from 'track-layout/layout-routing-api';

const DEBOUNCE_MS = 100;
const MAX_TRACK_SEEK_DISTANCE = 100.0;

type RouteLocation = {
    clickedCoordinate: Coordinate;
    closestTrackPoint: ClosestTrackPoint;
};

const dashedLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.measurementTooltipLine,
        lineDash: [8, 4],
        width: 2,
    }),
});

const pointCircleStyle = new Style({
    image: new CircleStyle({
        radius: 6,
        stroke: new Stroke({
            color: mapStyles.measurementTooltipCircle,
            width: 2,
        }),
    }),
});

function createConnector(c1: Coordinate, c2: Coordinate): Feature<LineString> {
    return new Feature({ geometry: new LineString([c1, c2]) });
}

function createTrackConnector(location: RouteLocation): Feature<LineString> {
    const trackCoordinate = pointToCoords(location.closestTrackPoint.trackLocation);
    return createConnector(location.clickedCoordinate, trackCoordinate);
}

export function createRouteFindingTool(layoutContext: LayoutContext): MapToolWithButton {
    const tool: MapToolWithButton = {
        id: 'route-finding',
        activate: (map: OlMap) => {
            console.log('Activating route finding tool');

            const vectorSource = new VectorSource();
            const vectorLayer = new VectorLayer({
                source: vectorSource,
                style: [dashedLineStyle, pointCircleStyle],
                zIndex: 1000,
            });

            map.addLayer(vectorLayer);

            let firstLocation: RouteLocation | undefined;
            let secondLocation: RouteLocation | undefined;
            let hoverCoordinate: Coordinate | undefined;
            let hoverClosestPoint: ClosestTrackPoint | undefined;

            const updateVisuals = () => {
                vectorSource.clear();

                const features: Feature<LineString>[] = [];

                // Dashed lines from clicked/hovered locations to the closest point on track
                if (firstLocation) features.push(createTrackConnector(firstLocation));
                if (secondLocation) features.push(createTrackConnector(secondLocation));
                if (hoverCoordinate && hoverClosestPoint && !secondLocation)
                    features.push(
                        createTrackConnector({
                            clickedCoordinate: hoverCoordinate,
                            closestTrackPoint: hoverClosestPoint,
                        }),
                    );

                vectorSource.addFeatures(features);
            };

            const debouncedUpdateHover = debounce(async (coordinate: Coordinate) => {
                const point = coordsToPoint(coordinate);
                hoverClosestPoint = await getClosestTrackPoint(
                    layoutContext,
                    point,
                    MAX_TRACK_SEEK_DISTANCE,
                );
                updateVisuals();
            }, DEBOUNCE_MS);

            const handlePointerMove = (event: PointerEvent) => {
                const pixel = map.getEventPixel(event);
                hoverCoordinate = map.getCoordinateFromPixel(pixel);

                const currentClosest =
                    hoverClosestPoint && pointToCoords(hoverClosestPoint.trackLocation);
                if (
                    currentClosest &&
                    distance(currentClosest, hoverCoordinate) > MAX_TRACK_SEEK_DISTANCE
                ) {
                    hoverClosestPoint = undefined;
                }
                if (!secondLocation) debouncedUpdateHover(hoverCoordinate);
                updateVisuals();
            };

            const handleClick = async (event: PointerEvent) => {
                if (firstLocation && secondLocation) {
                    firstLocation = undefined;
                    secondLocation = undefined;
                    return;
                }

                const pixel = map.getEventPixel(event);
                const coordinate = map.getCoordinateFromPixel(pixel);
                console.log('click', pixel, coordinate);
                if (!coordinate) return;

                const closestPoint = await getClosestTrackPoint(
                    layoutContext,
                    coordsToPoint(coordinate),
                    MAX_TRACK_SEEK_DISTANCE,
                );

                if (closestPoint) {
                    if (!firstLocation) {
                        firstLocation = {
                            clickedCoordinate: coordinate,
                            closestTrackPoint: closestPoint,
                        };
                    } else if (!secondLocation) {
                        secondLocation = {
                            clickedCoordinate: coordinate,
                            closestTrackPoint: closestPoint,
                        };
                    }
                }
                // Reset the hover attach point when clicking to avoid a connector that lingers due to debounce
                hoverClosestPoint = undefined;

                if (firstLocation && secondLocation) {
                    // Call the backend to get the route
                    console.log('Requesting route from backend...');
                    const routeResult = await getRoute(
                        layoutContext,
                        firstLocation.closestTrackPoint.requestedLocation,
                        secondLocation.closestTrackPoint.requestedLocation,
                        MAX_TRACK_SEEK_DISTANCE,
                    );
                    console.log('Route result:', routeResult);
                }

                console.log('click END:');
                console.log(' - clicked:', coordinate, closestPoint);
                console.log(' - first', firstLocation);
                console.log(' - second', secondLocation);
                updateVisuals();
            };

            const mapViewport = map.getViewport();
            mapViewport.addEventListener('pointermove', handlePointerMove);
            mapViewport.addEventListener('click', handleClick);

            // Return cleanup function
            return () => {
                mapViewport.removeEventListener('pointermove', handlePointerMove);
                mapViewport.removeEventListener('click', handleClick);
                map.removeLayer(vectorLayer);
            };
        },

        component: ({ isActive, setActiveTool }) => {
            return (
                <MapToolButton
                    isActive={isActive}
                    setActive={() => setActiveTool(tool)}
                    icon={Icons.VectorRight}
                />
            );
        },
    };
    return tool;
}
