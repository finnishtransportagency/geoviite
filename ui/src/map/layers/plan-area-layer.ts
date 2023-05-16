import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Fill, Stroke, Style, Text } from 'ol/style';
import { MapTile } from 'map/map-model';
import { PlanArea } from 'track-layout/track-layout-model';
import { getPlanAreasByTile } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import { pointToCoords } from 'map/layers/layer-utils';
import { MapLayer } from 'map/layers/layer-model';

function deduplicatePlanAreas(planAreas: PlanArea[]): PlanArea[] {
    return [...new Map(planAreas.map((area) => [area.id, area])).values()];
}

function createFeatures(planArea: PlanArea): Feature<Polygon> {
    const coordinates = planArea.polygon.map(pointToCoords);
    const feature = new Feature({ geometry: new Polygon([coordinates]) });

    feature.setStyle(() => {
        return new Style({
            text: new Text({
                text: planArea.fileName.replace(/\s/g, '_').split('/').join(' '),
                font: mapStyles.fontFamily,
                scale: 2,
                offsetX: 30,
                stroke: new Stroke({
                    color: mapStyles.boundingBoxText,
                }),
                backgroundFill: new Fill({
                    color: mapStyles.boundingBoxBackground,
                }),
            }),
            stroke: new Stroke({
                color: mapStyles.boundingBoxColor,
                width: 3,
            }),
            fill: new Fill({
                color: mapStyles.boundingBoxBackground,
            }),
        });
    });
    feature.set('planArea', planArea);
    return feature;
}
let newestPlanLayerId = 0;

export function createPlanAreaLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Polygon>> | undefined,
    changeTimes: ChangeTimes,
): MapLayer {
    const layerId = ++newestPlanLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<Polygon>[]) {
        vectorSource.clear();
        vectorSource.addFeatures(features);
    }

    // Fetch every nth
    const planAreaPromises = mapTiles.map((tile) =>
        getPlanAreasByTile(tile, changeTimes.geometryPlan),
    );

    Promise.all(planAreaPromises)
        .then((planAreas) =>
            deduplicatePlanAreas(planAreas.flat()).flatMap((planArea) => createFeatures(planArea)),
        )
        .then((features) => {
            // Handle the latest fetch only
            if (layerId === newestPlanLayerId) {
                updateFeatures(features);
            }
        })
        .catch(vectorSource.clear);

    return {
        name: 'plan-area-layer',
        layer: layer,
    };
}
