import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style, Text } from 'ol/style';
import { MapTile } from 'map/map-model';
import { PlanArea } from 'track-layout/track-layout-model';
import { getPlanAreasByTile } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';

function deduplicatePlanAreas(planAreas: PlanArea[]): PlanArea[] {
    return [...new Map(planAreas.map((area) => [area.id, area])).values()];
}

function createFeature(planArea: PlanArea): Feature<Polygon> {
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
                    color: mapStyles.planAreaTextColor,
                }),
            }),
            stroke: new Stroke({
                color: mapStyles.planAreaBorder,
                width: 3,
            }),
        });
    });

    return feature;
}

let newestLayerId = 0;

export function createPlanAreaLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Polygon>> | undefined,
    changeTimes: ChangeTimes,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<Polygon>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    }

    // Fetch every nth
    const planAreaPromises = mapTiles.map((tile) =>
        getPlanAreasByTile(tile, changeTimes.geometryPlan),
    );

    Promise.all(planAreaPromises)
        .then((planAreas) =>
            deduplicatePlanAreas(planAreas.flat()).flatMap((planArea) => createFeature(planArea)),
        )
        .then((features) => {
            if (layerId === newestLayerId) {
                updateFeatures(features);
            }
        })
        .catch(() => clearFeatures(vectorSource));

    return {
        name: 'plan-area-layer',
        layer: layer,
    };
}
