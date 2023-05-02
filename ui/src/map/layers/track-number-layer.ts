import { LineString, Point } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, TrackNumberDiagramLayer } from 'map/map-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';

let newestTrackNumberDiagramAdapterId = 0;

export function createTrackNumberSchemaLayerAdapter(
    mapLayer: TrackNumberDiagramLayer,
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    changeTimes: ChangeTimes,
    publishType: PublishType,
): OlLayerAdapter {
    const adapterId = ++newestTrackNumberDiagramAdapterId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer: VectorLayer<VectorSource<LineString | Point>> =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
            declutter: true,
        });

    layer.setVisible(mapLayer.visible);

    const alignmentsFetch = getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'ALL');
    Promise.all([alignmentsFetch]).then(([_]) => {
        if (adapterId != newestTrackNumberDiagramAdapterId) return;
    });

    return {
        layer: layer,
    };
}
