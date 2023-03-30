import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Fill, Stroke, Style, Text } from 'ol/style';
import { MapTile, PlanAreaLayer } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import { PlanArea } from 'track-layout/track-layout-model';
import { getPlanAreasByTile } from 'geometry/geometry-api';
import { LayerItemSearchResult, OlLayerAdapter } from 'map/layers/layer-model';
import { calculateTileSize } from 'map/map-utils';
import { ChangeTimes } from 'store/track-layout-store';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';

function deduplicatePlanAreas(planAreas: PlanArea[]): PlanArea[] {
    return [...new Map(planAreas.map((area) => [area.id, area])).values()];
}

function createFeatures(planArea: PlanArea): Feature<Polygon> {
    const coordinates = planArea.polygon.map((point) => {
        return [point.x, point.y];
    });
    const feature = new Feature<Polygon>({
        geometry: new Polygon([coordinates]),
    });

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

const areaFeatureCache: Map<string, Feature<Polygon>> = new Map();
adapterInfoRegister.add('planAreas', {
    createAdapter: function (
        mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<Polygon>> | undefined,
        mapLayer: PlanAreaLayer,
        _selection: Selection,
        _publishType: PublishType,
        _linkingState: LinkingState,
        changeTimes: ChangeTimes,
    ): OlLayerAdapter {
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();
        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<Polygon>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
            });

        function clearFeatures() {
            vectorSource.clear();
        }

        function updateFeatures(features: Feature<Polygon>[]) {
            clearFeatures();
            vectorSource.addFeatures(features);
        }

        layer.setVisible(mapLayer.visible);

        // Fetch every nth
        const planAreaPromises = mapTiles.map((tile) =>
            getPlanAreasByTile(tile, changeTimes.geometryPlan),
        );
        const updateFeaturesPromise = Promise.all(planAreaPromises)
            .then((planAreas) =>
                deduplicatePlanAreas(planAreas.flat()).flatMap((planArea) => {
                    const previous = areaFeatureCache.get(planArea.id);
                    return previous ? previous : createFeatures(planArea);
                }),
            )
            .then((features) => {
                // Handle the latest fetch only
                if (layer.get('updateFeaturesPromise') === updateFeaturesPromise) {
                    areaFeatureCache.clear();
                    features.forEach((f) => areaFeatureCache.set(f.get('planArea').id, f));
                    return updateFeatures(features);
                }
            });
        layer.set('updateFeaturesPromise', updateFeaturesPromise);

        return {
            layer: layer,
            searchItems: (): LayerItemSearchResult => {
                return {
                    // TODO: Select plan area posts
                };
            },
        };
    },

    // Use larger map tile size for km posts as each tile contains quite a small amount of data
    mapTileSizePx: calculateTileSize(2),
});
