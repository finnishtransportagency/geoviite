import * as React from 'react';
import { MapToolWithButton } from 'map/tools/tool-model';
import OlMap from 'ol/Map';
import { Stroke, Style } from 'ol/style';
import CircleStyle from 'ol/style/Circle';
import { LineString } from 'ol/geom';
import { Coordinate } from 'ol/coordinate';
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
import {
    getClosestTrackPoint,
    getRoute,
    ClosestTrackPoint,
} from 'track-layout/layout-routing-api';

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

export function createRouteFindingTool(layoutContext: LayoutContext): MapToolWithButton {
    return {
        id: 'route-finding',
        activate: (map: OlMap) => {
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

                // If we have the first location, draw dashed line from clicked point to track
                if (firstLocation) {
                    const trackCoordinate = pointToCoords(
                        firstLocation.closestTrackPoint.trackLocation,
                    );
                    features.push(
                        new Feature({
                            geometry: new LineString([
                                firstLocation.clickedCoordinate,
                                trackCoordinate,
                            ]),
                        }),
                    );
                }

                // If we have the second location, draw dashed line from clicked point to track
                if (secondLocation) {
                    const trackCoordinate = pointToCoords(
                        secondLocation.closestTrackPoint.trackLocation,
                    );
                    features.push(
                        new Feature({
                            geometry: new LineString([
                                secondLocation.clickedCoordinate,
                                trackCoordinate,
                            ]),
                        }),
                    );
                }

                // If we're hovering and have less than 2 locations, show preview line
                if (hoverCoordinate && hoverClosestPoint) {
                    if (!secondLocation) {
                        const trackCoordinate = pointToCoords(hoverClosestPoint.trackLocation);
                        // Show preview from hover to track point
                        features.push(
                            new Feature({
                                geometry: new LineString([hoverCoordinate, trackCoordinate]),
                            }),
                        );
                    }
                }

                vectorSource.addFeatures(features);
            };

            const debouncedUpdateHover = debounce(async (coordinate: Coordinate) => {
                const point = coordsToPoint(coordinate);
                try {
                    const closestPoint = await getClosestTrackPoint(
                        layoutContext,
                        point,
                        MAX_TRACK_SEEK_DISTANCE,
                    );
                    hoverClosestPoint = closestPoint;
                    updateVisuals();
                } catch (error) {
                    console.error('Error finding closest track point:', error);
                    hoverClosestPoint = undefined;
                }
            }, DEBOUNCE_MS);

            const handlePointerMove = (event: PointerEvent) => {
                const pixel = map.getEventPixel(event);
                const coordinate = map.getCoordinateFromPixel(pixel);

                if (coordinate) {
                    hoverCoordinate = coordinate;

                    // Only update hover track point if we don't have both locations yet
                    if (!secondLocation) {
                        debouncedUpdateHover(coordinate);
                    }
                }
            };

            const handleClick = async (event: PointerEvent) => {
                const pixel = map.getEventPixel(event);
                const coordinate = map.getCoordinateFromPixel(pixel);

                if (!coordinate) return;

                const point = coordsToPoint(coordinate);

                try {
                    const closestPoint = await getClosestTrackPoint(
                        layoutContext,
                        point,
                        MAX_TRACK_SEEK_DISTANCE,
                    );

                    if (!closestPoint) {
                        // No track nearby, ignore click
                        return;
                    }

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

                        // Call the backend to get the route
                        console.log('Requesting route from backend...');
                        try {
                            const routeResult = await getRoute(
                                layoutContext,
                                firstLocation.closestTrackPoint.requestedLocation,
                                secondLocation.closestTrackPoint.requestedLocation,
                                MAX_TRACK_SEEK_DISTANCE,
                            );
                            console.log('Route result:', routeResult);
                        } catch (error) {
                            console.error('Error getting route:', error);
                        }
                    } else {
                        // Reset and start over
                        firstLocation = {
                            clickedCoordinate: coordinate,
                            closestTrackPoint: closestPoint,
                        };
                        secondLocation = undefined;
                    }

                    updateVisuals();
                } catch (error) {
                    console.error('Error finding closest track point:', error);
                }
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
            // Create a fresh tool instance with current layoutContext
            const tool = createRouteFindingTool(layoutContext);
            return (
                <MapToolButton
                    isActive={isActive}
                    setActive={() => setActiveTool(tool)}
                    icon={Icons.VectorRight}
                />
            );
        },
    };
}
