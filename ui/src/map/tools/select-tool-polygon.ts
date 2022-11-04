import { Collection, Feature } from 'ol';
import { Polygon } from 'ol/geom';
import GeometryType from 'ol/geom/GeometryType';
import { Draw } from 'ol/interaction';
import VectorLayer from 'ol/layer/Vector';
import OlMap from 'ol/Map';
import VectorSource from 'ol/source/Vector';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { searchItemsFromLayers } from 'map/tools/tool-utils';
import Geometry from 'ol/geom/Geometry';

/**
 * This tool is used to select items on map by drawing polygons.
 */
export const selectToolPolygon: MapTool = {
    icon: 'fas fa-pencil-alt',
    activate: (map: OlMap, layerAdapters: OlLayerAdapter[], options: MapToolActivateOptions) => {
        // Create OL objects (layer, source etc.) to make it possible to draw polygons
        const drawFeatures = new Collection<Feature<Geometry>>();
        const drawSource = new VectorSource({
            features: drawFeatures,
        });
        const drawLayer = new VectorLayer({
            source: drawSource,
        });
        const draw = new Draw({
            source: drawSource,
            type: GeometryType.POLYGON,
            freehand: true,
            condition: () => true,
        });

        function clearDrawings() {
            setTimeout(() => {
                drawFeatures.clear();
            }, 200);
        }

        // When a polygon is drawn, use that shape to select items
        drawFeatures.on('add', (e) => {
            const shape = e.element?.getGeometry();
            if (!(shape instanceof Polygon)) {
                clearDrawings();
                return;
            }
            const items = searchItemsFromLayers(shape, layerAdapters, {});
            options.onSelect(items);

            clearDrawings();
        });

        map.addLayer(drawLayer);
        map.addInteraction(draw);

        // Return function to clean up this tool
        return () => {
            map.removeLayer(drawLayer);
            map.removeInteraction(draw);
        };
    },
};
