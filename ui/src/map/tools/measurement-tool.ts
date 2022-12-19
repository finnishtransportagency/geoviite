import { MapTool } from 'map/tools/tool-model';
import { Draw } from 'ol/interaction';
import OlMap from 'ol/Map';
import { Stroke, Style } from 'ol/style';
import { Precision, roundToPrecision } from 'utils/rounding';
import Overlay from 'ol/Overlay';
import mapStyles from '../map.module.scss';
import CircleStyle from 'ol/style/Circle';
import { LineString } from 'ol/geom';
import { Coordinate } from 'ol/coordinate';
import { getPlanarDistanceUnwrapped, SegmentDataHolder } from 'map/layers/layer-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutPoint } from 'track-layout/track-layout-model';
import { FEATURE_PROPERTY_SEGMENT_DATA } from 'map/layers/alignment-layer';

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

function getClosestPoints(
    points: LayoutPoint[],
    targetCoordinate: number[],
    maxPoints: number,
): LayoutPoint[] {
    if (points.length <= maxPoints) {
        return points;
    } else {
        const firstPointDistance = getPlanarDistanceUnwrapped(
            targetCoordinate[0],
            targetCoordinate[1],
            points[0].x,
            points[0].y,
        );

        const lastPointDistance = getPlanarDistanceUnwrapped(
            targetCoordinate[0],
            targetCoordinate[1],
            points[points.length - 1].x,
            points[points.length - 1].y,
        );

        const middleIndex = Math.floor(points.length / 2);

        const newPoints =
            firstPointDistance < lastPointDistance
                ? points.slice(0, middleIndex)
                : points.slice(middleIndex);

        return getClosestPoints(newPoints, targetCoordinate, maxPoints);
    }
}

//Tolerance for snapping, in pixels
const hitTolerance = 8;

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
            geometryFunction: function (coordinates: Coordinate[], existingGeom: LineString) {
                const cursorCoordinate = coordinates[coordinates.length - 1];
                const cursorPixel = map.getPixelFromCoordinate(cursorCoordinate);
                const nearbyFeatures = map.getFeaturesAtPixel(cursorPixel, { hitTolerance });

                const nearbyPoints = nearbyFeatures
                    .map((f) => f.get(FEATURE_PROPERTY_SEGMENT_DATA))
                    .filter(filterNotEmpty)
                    .flatMap((f: SegmentDataHolder) =>
                        getClosestPoints(f.segment.points, cursorCoordinate, 8),
                    );

                let closestPoint;
                for (let i = 0; i < nearbyPoints.length; i++) {
                    const nearbyPoint = nearbyPoints[i];
                    const pixelPoint = map.getPixelFromCoordinate([nearbyPoint.x, nearbyPoint.y]);

                    const distance = Math.hypot(
                        cursorPixel[0] - pixelPoint[0],
                        cursorPixel[1] - pixelPoint[1],
                    );

                    if (!closestPoint || distance < closestPoint.distance) {
                        closestPoint = {
                            distance: distance,
                            point: nearbyPoint,
                        };
                    }
                }

                const newCoordinates =
                    closestPoint && closestPoint.distance < hitTolerance
                        ? [closestPoint.point.x, closestPoint.point.y]
                        : cursorCoordinate;

                if (existingGeom) {
                    existingGeom.setCoordinates([
                        existingGeom.getFirstCoordinate(),
                        newCoordinates,
                    ]);
                } else {
                    existingGeom = new LineString([newCoordinates]);
                }

                tooltipElement.innerHTML = formatMeasurement(existingGeom.getLength());
                tooltip.setPosition(existingGeom.getLastCoordinate());

                return existingGeom;
            },
        });

        tooltipDraw.on('drawend', function () {
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
