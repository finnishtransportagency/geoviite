import { LineString, Polygon } from 'ol/geom';
import { MapTool } from 'map/tools/tool-model';
import { Draw } from 'ol/interaction';
import { createBox } from 'ol/interaction/Draw.js';
import { noModifierKeys, primaryAction } from 'ol/events/condition';
import OlMap from 'ol/Map';
import { Fill, Stroke, Style } from 'ol/style';
import Overlay from 'ol/Overlay';
import mapStyles from '../map.module.scss';
import CircleStyle from 'ol/style/Circle';
import MapBrowserEvent from 'ol/MapBrowserEvent';
import { Coordinate } from 'ol/coordinate';
import { LayerItemSearchResult, MapLayer } from 'map/layers/utils/layer-model';
import { BoundingBox, boundingBoxAroundPoints, coordsToPoint } from 'model/geometry';
import { expectDefined } from 'utils/type-utils';
import { searchItemsFromLayers } from 'map/tools/tool-utils';

function getPolygon(bbox: BoundingBox): Polygon {
    return new Polygon([
        [
            [bbox.x.min, bbox.y.min],
            [bbox.x.max, bbox.y.min],
            [bbox.x.max, bbox.y.max],
            [bbox.x.min, bbox.y.max],
            [bbox.x.min, bbox.y.min],
        ],
    ]);
}

function getItemsFromLayers(bbox: BoundingBox, layers: MapLayer[]): LayerItemSearchResult {
    const polygon = getPolygon(bbox);
    return searchItemsFromLayers(polygon, layers, {});
}

export function createAreaSelectTool(onSelect?: (items: LayerItemSearchResult) => void) {
    return {
        activate: (map: OlMap, layers: MapLayer[]) => {
            const tooltipElement = document.createElement('div');
            tooltipElement.className = 'ol-tooltip-measure';

            const tooltip = new Overlay({
                element: tooltipElement,
                offset: [0, -10],
                positioning: 'bottom-center',
                stopEvent: false,
                insertFirst: false,
            });

            const f = createBox();
            let currentBoundingBox: BoundingBox | undefined = undefined;

            const tooltipDraw = new Draw({
                type: 'Circle',
                //freehand: true,
                // condition: (e: MapBrowserEvent<UIEvent>) => {
                //     console.log('zzzz');
                //     return noModifierKeys(e) && primaryAction(e);
                // },
                freehandCondition: (e: MapBrowserEvent<UIEvent>) => {
                    console.log('yyyy');
                    return noModifierKeys(e) && primaryAction(e);
                },
                //maxPoints: 2,
                style: new Style({
                    stroke: new Stroke({
                        color: '#0066cc', //mapStyles.measurementTooltipLine,
                        lineDash: [8, 8],
                        width: 1,
                    }),
                    fill: new Fill({
                        color: '#0066cc22',
                    }),
                    image: new CircleStyle({
                        radius: 6,
                        stroke: new Stroke({
                            color: mapStyles.measurementTooltipCircle,
                        }),
                    }),
                }),
                geometryFunction: function (
                    coordinates: Coordinate[],
                    existingGeom: LineString,
                    projection,
                ) {
                    // tooltipElement.innerHTML = 'xxx';
                    // tooltip.setPosition(existingGeom.getLastCoordinate());
                    const boxGeom = f(coordinates, existingGeom, projection);
                    currentBoundingBox =
                        coordinates.length >= 2
                            ? boundingBoxAroundPoints([
                                  coordsToPoint(expectDefined(coordinates[0])),
                                  coordsToPoint(expectDefined(coordinates[1])),
                              ])
                            : undefined;
                    //console.log(currentBoundingBox);
                    return boxGeom;
                    // const cursorCoordinate = last(coordinates);
                    // if (!cursorCoordinate) return existingGeom;
                    //
                    // const _cursorPixel = map.getPixelFromCoordinate(cursorCoordinate) as [
                    //     number,
                    //     number,
                    // ];
                    //
                    // const newCoordinates = cursorCoordinate;
                    //
                    // if (existingGeom) {
                    //     existingGeom.setCoordinates([
                    //         existingGeom.getFirstCoordinate(),
                    //         newCoordinates,
                    //     ]);
                    // } else {
                    //     existingGeom = new LineString([newCoordinates]);
                    // }
                    //
                    //
                    // return existingGeom;
                },
            });

            tooltipDraw.on('drawend', function () {
                if (currentBoundingBox !== undefined && onSelect != undefined) {
                    const items = getItemsFromLayers(currentBoundingBox, layers);
                    console.log(items);
                    onSelect(items);
                }
                tooltip.setPosition(undefined);
            });

            map.addInteraction(tooltipDraw);
            map.addOverlay(tooltip);

            // Return function to clean up this tool
            return () => {
                map.removeInteraction(tooltipDraw);
                map.removeOverlay(tooltip);
            };
        },
    };
}

export const areaSelectTool: MapTool = createAreaSelectTool();
