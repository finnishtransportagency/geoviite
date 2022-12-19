import { MapTool } from 'map/tools/tool-model';
import { Draw } from 'ol/interaction';
import OlMap from 'ol/Map';
import { Stroke, Style } from 'ol/style';
import { Precision, roundToPrecision } from 'utils/rounding';
import Overlay from 'ol/Overlay';
import { EventsKey } from 'ol/events';
import { unByKey } from 'ol/Observable';
import mapStyles from '../map.module.scss';
import CircleStyle from 'ol/style/Circle';

function formatMeasurement(distance: number) {
    let content;
    if (distance < 1) {
        content = Math.round(distance * 1000) + ' mm';
    } else if (distance > 10000) {
        content = roundToPrecision(distance / 1000, Precision.measurementKmDistance) + ' km';
    } else {
        content = roundToPrecision(distance, Precision.measurementMeterDistance) + ' m';
    }

    return content;
}

export const measurementTool: MapTool = {
    activate: (map: OlMap) => {
        const tooltipElement = document.createElement('div');
        tooltipElement.className = 'ol-tooltip-measure';

        const tooltip = new Overlay({
            element: tooltipElement,
            offset: [0, -10],
            positioning: 'bottom-center',
            stopEvent: false,
            insertFirst: false,
        });

        const tooltipDraw = new Draw({
            type: 'LineString',
            maxPoints: 2,
            style: new Style({
                zIndex: 20,
                stroke: new Stroke({
                    color: mapStyles.measureTooltipStroke,
                    lineDash: [8],
                    width: 2,
                }),
                image: new CircleStyle({
                    radius: 6,
                    stroke: new Stroke({
                        color: mapStyles.measureTooltipCircleStroke,
                    }),
                }),
            }),
        });

        let geometryListener: EventsKey | undefined;
        tooltipDraw.on('drawstart', function (evt) {
            geometryListener = evt.feature.getGeometry()?.on('change', function (evt) {
                const geom = evt.target;
                const distance = geom.getLength();

                tooltipElement.innerHTML = formatMeasurement(distance);
                tooltip.setPosition(geom.getLastCoordinate());
            });
        });

        tooltipDraw.on('drawend', function () {
            tooltip.setPosition(undefined);
            if (geometryListener) {
                unByKey(geometryListener);
            }
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
