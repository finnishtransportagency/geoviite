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
import MapBrowserEvent from 'ol/MapBrowserEvent';

const HOVER_DEBOUNCE_MS = 20;
const MAX_TRACK_SEEK_DISTANCE_IN_PIXELS = 40.0;
const id = 'route-finding';

export function createRouteFindingTool(
    layoutContext: LayoutContext,
    routeLocations: RouteLocations,
    onHoveredLocationChange: (hoveredLocation: RouteLocation | undefined) => void,
    onRouteLocationsChange: (locations: RouteLocations) => void,
): MapToolWithButton {
    const tool: MapToolWithButton = {
        id,
        activate: (map: OlMap) => {
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

            function getSeekDistance(): number | undefined {
                const resolution = map.getView().getResolution();
                if (!resolution) return undefined;
                return pixelsToMeters(resolution, MAX_TRACK_SEEK_DISTANCE_IN_PIXELS);
            }

            const getClosest = async (coordinate: Coordinate, seekDistance: number) => {
                const point = coordsToPoint(coordinate);
                return await getClosestTrackPoint(layoutContext, point, seekDistance);
            };

            const debouncedUpdateHover = debounce(
                async (coordinate: Coordinate) => {
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
                },
                HOVER_DEBOUNCE_MS,
                { isImmediate: true, maxWait: 100 },
            );

            const handlePointerMove = ({ coordinate }: MapBrowserEvent) => {
                debouncedUpdateHover(coordinate);
            };

            const handleClick = async ({ coordinate }: MapBrowserEvent) => {
                const seekDistance = getSeekDistance();
                if (!seekDistance) return;

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
                        // First click sets start
                        replaceStart(newRouteLocation);
                    } else {
                        // Following clicks set end
                        replaceEnd(newRouteLocation);
                    }
                } else {
                    // Clicking "off" track clears route markings
                    onRouteLocationsChange({
                        start: undefined,
                        end: undefined,
                    });
                }
            };

            const pointerMoveEventsKey = map.on('pointermove', handlePointerMove);
            const clickEventsKey = map.on('click', handleClick);

            return {
                onLayersChanged: () => {},
                deactivate: () => {
                    map.un('click', clickEventsKey.listener);
                    map.un('pointermove', pointerMoveEventsKey.listener);
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
