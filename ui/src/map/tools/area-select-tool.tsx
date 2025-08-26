import * as React from 'react';
import { LineString, Polygon } from 'ol/geom';
import { MapToolWithButton } from 'map/tools/tool-model';
import { Draw } from 'ol/interaction';
import { createBox } from 'ol/interaction/Draw.js';
import { altKeyOnly, noModifierKeys, primaryAction } from 'ol/events/condition';
import OlMap from 'ol/Map';
import { Fill, Stroke, Style } from 'ol/style';
import Overlay from 'ol/Overlay';
import mapStyles from '../map.module.scss';
import RegularShape from 'ol/style/RegularShape';
import MapBrowserEvent from 'ol/MapBrowserEvent';
import { Coordinate } from 'ol/coordinate';
import { LayerItemSearchResult, MapLayer } from 'map/layers/utils/layer-model';
import { BoundingBox, boundingBoxAroundPoints, coordsToPoint } from 'model/geometry';
import { expectDefined } from 'utils/type-utils';
import { searchItemsFromLayers } from 'map/tools/tool-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';

export enum SelectMode {
    Add = 'add',
    Subtract = 'subtract',
}

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

const selectedAreaStyle = new Style({
    stroke: new Stroke({
        color: '#0066cc',
        lineDash: [8, 8],
        width: 1,
    }),
    fill: new Fill({
        color: '#0066cc22',
    }),
});

const subtractCursor = new Style({
    image: new RegularShape({
        stroke: new Stroke({
            color: mapStyles.measurementTooltipCircle,
        }),
        points: 2,
        radius: 4,
        radius2: 0,
        angle: Math.PI / 2,
        displacement: [12, -12],
    }),
});

export function createAreaSelectTool(
    onSelect: (items: LayerItemSearchResult, mode: SelectMode) => void,
): MapToolWithButton {
    return {
        id: 'area-select',
        customCursor: 'crosshair',
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
            let mode: SelectMode = SelectMode.Add;

            const getStyles = () =>
                mode === SelectMode.Add ? [selectedAreaStyle] : [selectedAreaStyle, subtractCursor];

            const tooltipDraw = new Draw({
                type: 'Circle',
                freehandCondition: (e: MapBrowserEvent<PointerEvent>) => {
                    mode = altKeyOnly(e) ? SelectMode.Subtract : SelectMode.Add;
                    return (noModifierKeys(e) || altKeyOnly(e)) && primaryAction(e);
                },
                style: getStyles,
                geometryFunction: function (
                    coordinates: Coordinate[],
                    existingGeom: LineString,
                    projection,
                ) {
                    const boxGeom = f(coordinates, existingGeom, projection);
                    currentBoundingBox =
                        coordinates.length >= 2
                            ? boundingBoxAroundPoints([
                                  coordsToPoint(expectDefined(coordinates[0])),
                                  coordsToPoint(expectDefined(coordinates[1])),
                              ])
                            : undefined;
                    return boxGeom;
                },
            });

            tooltipDraw.on('drawend', function () {
                if (currentBoundingBox !== undefined && onSelect !== undefined) {
                    const items = getItemsFromLayers(currentBoundingBox, layers);
                    onSelect(items, mode);
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
        component: ({ isActive, setActiveTool }) => {
            return (
                <MapToolButton
                    setActive={() => setActiveTool(createAreaSelectTool(onSelect))}
                    isActive={isActive}
                    icon={Icons.SelectArea}
                />
            );
        },
    };
}
