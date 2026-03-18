import * as React from 'react';
import { MapToolWithButton } from 'map/tools/tool-model';
import OlMap from 'ol/Map';
import { Coordinate } from 'ol/coordinate';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';
import { debounce } from 'ts-debounce';
import { coordsToPoint } from 'model/geometry';
import { LayoutContext } from 'common/common-model';
import { getClosestTrackPoint } from 'track-layout/layout-routing-api';
import { RouteLocation, RouteLocations } from 'track-layout/track-layout-slice';
import { pixelsToMeters } from 'map/map-utils';

const HOVER_DEBOUNCE_MS = 30;
const MAX_TRACK_SEEK_DISTANCE_IN_PIXELS = 40.0;

// const dashedLineStyle = new Style({
//     stroke: new Stroke({
//         color: mapStyles.measurementTooltipLine,
//         lineDash: [8, 4],
//         width: 2,
//     }),
// });
//
// const pointCircleStyle = new Style({
//     image: new CircleStyle({
//         radius: 6,
//         stroke: new Stroke({
//             color: mapStyles.measurementTooltipCircle,
//             width: 2,
//         }),
//     }),
// });

// function createTrackConnectorFeature(
//     location: RouteLocation,
//     markerState: RouteMarkerState,
// ): Feature<OlPoint> {
//     return createRouteMarkerFeature(location.closestTrackPoint.trackLocation, markerState);
// }

const id = 'route-finding';
export function createRouteFindingTool(
    layoutContext: LayoutContext,
    // onRouteFound: (route: RouteResult | undefined) => void,
    routeLocations: RouteLocations,
    onHoveredLocationChange: (hoveredLocation: RouteLocation | undefined) => void,
    onRouteLocationsChange: (locations: RouteLocations) => void,
): MapToolWithButton {
    // let route: RouteResult | undefined;
    const tool: MapToolWithButton = {
        id,
        activate: (map: OlMap) => {
            console.log('routeLocations in tool', routeLocations);
            // const vectorSource = new VectorSource();
            // const vectorLayer = new VectorLayer({
            //     source: vectorSource,
            //     style: [dashedLineStyle, pointCircleStyle],
            //     zIndex: 1000,
            // });

            // let firstLocation: RouteLocation | undefined;
            // let secondLocation: RouteLocation | undefined;
            // let hoverCoordinate: Coordinate | undefined;
            // let hoverClosestPoint: ClosestTrackPoint | undefined;

            //            map.addLayer(vectorLayer);

            // const updateVisuals = () => {
            //     vectorSource.clear();
            //
            //     const features: Feature<OlPoint>[] = [];
            //
            //     if (!route) {
            //         // Dashed lines from clicked/hovered locations to the closest point on track
            //         if (firstLocation)
            //             features.push(
            //                 createTrackConnectorFeature(firstLocation, RouteMarkerState.Final),
            //             );
            //         if (secondLocation)
            //             features.push(
            //                 createTrackConnectorFeature(secondLocation, RouteMarkerState.Final),
            //             );
            //         if (hoverCoordinate && hoverClosestPoint && !secondLocation)
            //             features.push(
            //                 createTrackConnectorFeature(
            //                     {
            //                         selectedCoordinate: hoverCoordinate,
            //                         closestTrackPoint: hoverClosestPoint,
            //                     },
            //                     RouteMarkerState.Pending,
            //                 ),
            //             );
            //     }
            //
            //     //vectorSource.addFeatures(features);
            // };
            //
            // const resetTempVisuals = () => {
            //     firstLocation = undefined;
            //     secondLocation = undefined;
            //     hoverClosestPoint = undefined;
            //     updateVisuals();
            // };

            function getSeekDistance(): number | undefined {
                const resolution = map.getView().getResolution();
                if (!resolution) return undefined;
                return pixelsToMeters(resolution, MAX_TRACK_SEEK_DISTANCE_IN_PIXELS);
            }

            const getClosest = async (coordinate: Coordinate, seekDistance: number) => {
                const point = coordsToPoint(coordinate);
                return await getClosestTrackPoint(layoutContext, point, seekDistance);
            };

            // const debouncedUpdateHover = debounce(async (coordinate: Coordinate) => {
            //     hoverClosestPoint = await getClosest(coordinate);
            //     // updateVisuals();
            // }, HOVER_DEBOUNCE_MS);

            const debouncedUpdateHover2 = debounce(async (coordinate: Coordinate) => {
                const seekDistance = getSeekDistance();
                if (!seekDistance) return;
                const hoverClosestPoint = await getClosest(coordinate, seekDistance);
                const hoveredRouteLocation = hoverClosestPoint
                    ? {
                          selectedCoordinate: coordinate,
                          closestTrackPoint: hoverClosestPoint,
                          seekDistance: seekDistance,
                      }
                    : undefined;
                onHoveredLocationChange(hoveredRouteLocation);
            }, HOVER_DEBOUNCE_MS);

            const handlePointerMove = (event: PointerEvent) => {
                const pixel = map.getEventPixel(event);
                const hoverCoordinate = map.getCoordinateFromPixel(pixel);

                // const currentClosest =
                //     hoverClosestPoint && pointToCoords(hoverClosestPoint.trackLocation);
                // if (
                //     currentClosest &&
                //     distance(currentClosest, hoverCoordinate) > MAX_TRACK_SEEK_DISTANCE_IN_PIXELS
                // ) {
                //     hoverClosestPoint = undefined;
                // }
                debouncedUpdateHover2(hoverCoordinate);
                // if (!secondLocation) debouncedUpdateHover(hoverCoordinate);
                // updateVisuals();
            };

            function replaceStart(routeLocation: RouteLocation) {
                onRouteLocationsChange({
                    ...routeLocations,
                    start: routeLocation,
                });
            }
            function replaceEnd(routeLocation: RouteLocation) {
                onRouteLocationsChange({
                    ...routeLocations,
                    end: routeLocation,
                });
            }

            const handleClick = async (event: PointerEvent) => {
                // if (routeLocations && routeLocations.start && routeLocations.end) {
                //     onRouteLocationsChange({
                //         start: undefined,
                //         end: undefined,
                //     });
                //
                //     // Reset on third click
                //     // route = undefined;
                //     // onRouteFound(undefined);
                //     // resetTempVisuals();
                //     return;
                // }
                const seekDistance = getSeekDistance();
                if (!seekDistance) return;

                const pixel = map.getEventPixel(event);
                const coordinate = map.getCoordinateFromPixel(pixel);

                console.log('resolution');
                console.log('pixel', pixel);

                console.log('seekDistance', seekDistance);
                if (!coordinate) return;

                const closestTrackPoint = await getClosestTrackPoint(
                    layoutContext,
                    coordsToPoint(coordinate),
                    seekDistance,
                );

                if (closestTrackPoint) {
                    const newRouteLocation = {
                        selectedCoordinate: coordinate,
                        closestTrackPoint: closestTrackPoint,
                    };
                    if (!routeLocations.start) {
                        replaceStart(newRouteLocation);
                    } else {
                        replaceEnd(newRouteLocation);
                    }
                    // else {
                    //     replaceStart(newRouteLocation);
                    //     const distanceToStart = distance(
                    //         closestTrackPoint.trackLocation,
                    //         routeLocations.start.closestTrackPoint.trackLocation,
                    //     );
                    //     const distanceToEnd = distance(
                    //         closestTrackPoint.trackLocation,
                    //         routeLocations.end.closestTrackPoint.trackLocation,
                    //     );
                    //     if (distanceToStart < distanceToEnd) {
                    //         replaceStart(newRouteLocation);
                    //     } else {
                    //         replaceEnd(newRouteLocation);
                    //     }
                    // }

                    // if (!firstLocation) {
                    //     firstLocation = {
                    //         selectedCoordinate: coordinate,
                    //         closestTrackPoint: closestPoint,
                    //     };
                    // } else if (!secondLocation) {
                    //     secondLocation = {
                    //         selectedCoordinate: coordinate,
                    //         closestTrackPoint: closestPoint,
                    //     };
                    // }
                } else {
                    onRouteLocationsChange({
                        start: undefined,
                        end: undefined,
                    });
                }
                // Reset the hover attach point when clicking to avoid a connector that lingers due to debounce
                // hoverClosestPoint = undefined;

                // if (firstLocation && secondLocation) {
                //     // Call the backend to get the route
                //     route = await getRoute(
                //         layoutContext,
                //         firstLocation.closestTrackPoint.requestedLocation,
                //         secondLocation.closestTrackPoint.requestedLocation,
                //         MAX_TRACK_SEEK_DISTANCE_IN_PIXELS,
                //     );
                //
                //     if (route) {
                //         // onRouteFound(route);
                //         resetTempVisuals();
                //     }
                // }
                //
                // updateVisuals();
            };

            const mapViewport = map.getViewport();
            mapViewport.addEventListener('pointermove', handlePointerMove);
            mapViewport.addEventListener('click', handleClick);

            return {
                onLayersChanged: () => {
                    // map.removeLayer(vectorLayer);
                    // map.addLayer(vectorLayer);
                },
                deactivate: () => {
                    mapViewport.removeEventListener('pointermove', handlePointerMove);
                    mapViewport.removeEventListener('click', handleClick);
                    // map.removeLayer(vectorLayer);
                },
            };
        },

        component: ({ isActive, setActiveTool }) => {
            return (
                <MapToolButton
                    id={id}
                    isActive={isActive}
                    setActive={setActiveTool}
                    icon={Icons.VectorRight}
                />
            );
        },
    };
    return tool;
}
