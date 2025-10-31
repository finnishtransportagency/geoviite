import { Feature } from 'ol';
import { LineString, MultiPoint, Polygon } from 'ol/geom';
import Style from 'ol/style/Style';
import { Coordinate } from 'ol/coordinate';
import CircleStyle from 'ol/style/Circle.js';
import Fill from 'ol/style/Fill';
import Stroke from 'ol/style/Stroke';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { GvtPolygon, Rectangle } from 'model/geometry';
import { findMatchingOperationalPoints } from 'map/layers/operational-point/operational-points-layer-utils';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { expectDefined } from 'utils/type-utils';
import { PlacingOperationalPointArea } from 'linking/linking-model';
import { getOperationalPoint } from 'track-layout/layout-operational-point-api';
import { LayoutContext } from 'common/common-model';

const LAYER_NAME = 'operational-points-area-placing-layer';

function intersects(
    a: number,
    b: number,
    c: number,
    d: number,
    p: number,
    q: number,
    r: number,
    s: number,
) {
    const det = (c - a) * (s - q) - (r - p) * (d - b);
    if (det === 0) {
        return false;
    } else {
        const lambda = ((s - q) * (r - a) + (p - r) * (s - b)) / det;
        const gamma = ((b - d) * (r - a) + (c - a) * (s - b)) / det;

        return 0 < lambda && lambda < 1 && 0 < gamma && gamma < 1;
    }
}

function linesIntersects(
    line1: [[number, number], [number, number]],
    line2: [[number, number], [number, number]],
) {
    return intersects(
        line1[0][0],
        line1[0][1],
        line1[1][0],
        line1[1][1],
        line2[0][0],
        line2[0][1],
        line2[1][0],
        line2[1][1],
    );
}

export const coordsToPolygon = (coords: Coordinate[]): GvtPolygon => ({
    points: coords.map(([x, y]) => ({ x: x ?? 0, y: y ?? 0 })),
});

export function isValidPolygon(coords: Coordinate[], ignoreLast: boolean) {
    let lines = coords.slice(0, -1).map((c, index, cs) => {
        const endCoordinate = expectDefined(cs[(index + 1) % cs.length]);
        return [c, endCoordinate];
    }) as [[number, number], [number, number]][];
    lines = ignoreLast ? lines.slice(0, -1) : lines;

    return !lines.find((line, index, ls) => {
        return ls.find((line2, index2) => index !== index2 && linesIntersects(line, line2));
    });
}

export function getCoords(feature: Feature<MultiPoint | LineString | Polygon>): Coordinate[] {
    const geom = feature.getGeometry();
    const coords = geom?.getCoordinates()[0];
    const unified: Coordinate[] =
        Array.isArray(coords) && Array.isArray(coords[0])
            ? (coords as Coordinate[])
            : Array.isArray(coords) && !Array.isArray(coords[0])
              ? ([coords] as Coordinate[])
              : [];
    return unified.map((c) => (Array.isArray(c) ? c.map((v: number) => Math.round(v)) : c));
}

export const operationalPointAreaPolygonStyle = function (isNew: boolean) {
    return function (feature: Feature<Polygon>) {
        console.log('stylerooni');
        const coords = getCoords(feature);
        const isValid = isValidPolygon(coords, isNew);
        const lineColor = isValid ? '#009BFF' : 'red';
        const fillColor = isValid ? '#009BFF35' : 'rgba(255, 150, 0, 0.1)';
        return coords.length
            ? [
                  new Style({
                      stroke: new Stroke({
                          color: lineColor,
                          width: 2,
                      }),
                      geometry: function (feature: Feature<LineString>) {
                          const coordinates = getCoords(feature);
                          const refined = isNew ? coordinates.slice(0, -1) : coordinates;
                          return new LineString(refined);
                      },
                  }),
                  new Style({
                      fill: new Fill({
                          color: fillColor,
                      }),
                      geometry: function (feature: Feature<Polygon>) {
                          const coordinates = getCoords(feature);
                          const refined = coordinates;
                          return new Polygon([refined]);
                      },
                  }),
                  new Style({
                      image: new CircleStyle({
                          radius: 5,
                          fill: new Fill({
                              color: lineColor,
                          }),
                      }),
                      geometry: function (feature: Feature<MultiPoint>) {
                          // return the coordinates of the first ring of the polygon
                          const coordinates = getCoords(feature).slice(0, -1);
                          return new MultiPoint(coordinates);
                      },
                  }),
              ]
            : [];
    };
};

export const createOperationalPointsAreaPlacingLayer = (
    existingOlLayer: GeoviiteMapLayer<Polygon>,
    linkingState: PlacingOperationalPointArea | undefined,
    layoutContext: LayoutContext,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);
    const selectedOperationalPointId = linkingState?.operationalPoint?.id;

    loadLayerData(
        source,
        isLatest,
        onLoadingData,
        selectedOperationalPointId
            ? getOperationalPoint(selectedOperationalPointId, layoutContext)
            : Promise.resolve(undefined),
        () => {
            const coords = linkingState?.polygon?.points?.map(pointToCoords);
            if (!coords) return [];

            const feature = new Feature({
                geometry: new Polygon([coords]),
            });
            feature.setStyle(operationalPointAreaPolygonStyle(false));
            return [feature];
        },
    );

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                (operationalPoint) => operationalPoint.id,
            ),
        }),
    };
};
