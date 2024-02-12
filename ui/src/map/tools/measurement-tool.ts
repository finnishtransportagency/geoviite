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
import { getPlanarDistanceUnwrapped, pointToCoords } from 'map/layers/utils/layer-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { ALIGNMENT_FEATURE_DATA_PROPERTY } from 'map/layers/utils/alignment-layer-utils';
import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import { getCoordsUnsafe, getUnsafe } from 'utils/type-utils';

function formatMeasurement(distance: number): string {
    if (distance < 1) {
        return Math.round(distance * 1000) + ' mm';
    } else if (distance > 10000) {
        return roundToPrecision(distance / 1000, Precision.measurementKmDistance) + ' km';
    } else {
        return roundToPrecision(distance, Precision.measurementMeterDistance) + ' m';
    }
}

function findClosestPoints(
    points: AlignmentPoint[],
    targetCoordinate: Coordinate,
    maxPoints: number,
): AlignmentPoint[] {
    if (points.length <= maxPoints) {
        return points;
    } else {
        const [targetX, targetY] = getCoordsUnsafe(targetCoordinate);
        const firstPointDistance = getPlanarDistanceUnwrapped(
            targetX,
            targetY,
            getUnsafe(points[0]).x,
            getUnsafe(points[0]).y,
        );

        const lastPointDistance = getPlanarDistanceUnwrapped(
            targetX,
            targetY,
            getUnsafe(points[points.length - 1]).x,
            getUnsafe(points[points.length - 1]).y,
        );

        const middleIndex = Math.floor(points.length / 2);

        const newPoints =
            firstPointDistance < lastPointDistance
                ? points.slice(0, middleIndex)
                : points.slice(middleIndex);

        return findClosestPoints(newPoints, targetCoordinate, maxPoints);
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
                stroke: new Stroke({
                    color: mapStyles.measurementTooltipLine,
                    lineDash: [8],
                    width: 2,
                }),
                image: new CircleStyle({
                    radius: 6,
                    stroke: new Stroke({
                        color: mapStyles.measurementTooltipCircle,
                    }),
                }),
            }),
            geometryFunction: function (coordinates: Coordinate[], existingGeom: LineString) {
                const cursorCoordinate = getUnsafe(coordinates[coordinates.length - 1]);
                const cursorPixel = map.getPixelFromCoordinate(cursorCoordinate) as [
                    number,
                    number,
                ];
                const nearbyFeatures = map.getFeaturesAtPixel(cursorPixel, { hitTolerance });

                const nearbyAlignmentPoints = nearbyFeatures
                    .map((f) => f.get(ALIGNMENT_FEATURE_DATA_PROPERTY))
                    .filter(filterNotEmpty)
                    .flatMap(({ points }: AlignmentDataHolder) =>
                        findClosestPoints(points, cursorCoordinate, 8),
                    );

                let closestPoint: { distance: number; point: AlignmentPoint } | undefined;
                for (let i = 0; i < nearbyAlignmentPoints.length; i++) {
                    const nearbyPoint = getUnsafe(nearbyAlignmentPoints[i]);
                    const pixelPoint = map.getPixelFromCoordinate(pointToCoords(nearbyPoint)) as [
                        number,
                        number,
                    ];

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
                        ? pointToCoords(closestPoint.point)
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
