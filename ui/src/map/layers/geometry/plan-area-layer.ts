import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import { Stroke, Style, Text } from 'ol/style';
import { MapLayerName, MapTile } from 'map/map-model';
import { PlanArea } from 'track-layout/track-layout-model';
import { getPlanAreasByTile } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import VectorLayer from 'ol/layer/Vector';

function deduplicatePlanAreas(planAreas: PlanArea[]): PlanArea[] {
    return [...new Map(planAreas.map((area) => [area.id, area])).values()];
}

function createPlanFeature(planArea: PlanArea): Feature<Polygon> {
    const coordinates = planArea.polygon.map(pointToCoords);
    const feature = new Feature({ geometry: new Polygon([coordinates]) });

    feature.setStyle(
        new Style({
            text: new Text({
                text: planArea.name,
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
        }),
    );

    return feature;
}

const layerName: MapLayerName = 'plan-area-layer';

export function createPlanAreaLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<Polygon>> | undefined,
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const planAreaPromises = mapTiles.map((tile) =>
        getPlanAreasByTile(tile, changeTimes.geometryPlan),
    );
    const dataPromise: Promise<PlanArea[]> = Promise.all(planAreaPromises).then((planAreas) =>
        deduplicatePlanAreas(planAreas.flat()),
    );

    const createFeatures = (planAreas: PlanArea[]) =>
        planAreas.flatMap((planArea) => createPlanFeature(planArea));

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
