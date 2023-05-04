import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, TrackNumberDiagramLayer } from 'map/map-model';
import { getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy } from 'utils/array-utils';
import * as Limits from 'map/layers/layer-visibility-limits';
import OlView from 'ol/View';
import { Feature } from 'ol';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';

let newestTrackNumberDiagramAdapterId = 0;

const colors = [
    '#858585',

    '#66a3e0',
    '#0066cc',

    '#d9a599',
    '#de3618',

    '#ffc300',

    '#8dcb6d',
    '#27b427',

    '#00b0cc',
    '#afe1e9',

    '#a050a0',
    '#e50083',
];

const getNextColor = (id: LayoutTrackNumberId) => {
    return colors[parseInt(id.replace(/^\D+/g, '')) % colors.length];
};

export function createTrackNumberSchemaLayerAdapter(
    mapLayer: TrackNumberDiagramLayer,
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    olView: OlView,
    changeTimes: ChangeTimes,
    publishType: PublishType,
): OlLayerAdapter {
    const adapterId = ++newestTrackNumberDiagramAdapterId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
            declutter: true,
            opacity: 0.33,
        });

    layer.setVisible(mapLayer.visible);

    const resolution = olView.getResolution() || 0;
    const fetchType = resolution > Limits.ALL_ALIGNMENTS ? 'REFERENCE_LINES' : 'ALL';

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, fetchType).then((alignments) => {
        if (adapterId != newestTrackNumberDiagramAdapterId) return;

        const perTrackNumber = groupBy(
            alignments,
            (a) => a.header.trackNumberId || a.trackNumber?.id || '',
        );

        const features = Object.entries(perTrackNumber).flatMap(([trackNumberId, alignments]) => {
            const style = new Style({
                stroke: new Stroke({
                    color: getNextColor(trackNumberId),
                    width: 20,
                    lineCap: 'butt',
                }),
            });

            return alignments.map((a) => {
                const feature = new Feature({
                    geometry: new LineString(a.points.map((p) => [p.x, p.y])),
                });

                feature.setStyle(style);

                return feature;
            });
        });

        vectorSource.clear();
        vectorSource.addFeatures(features.flat());
    });

    return {
        layer: layer,
    };
}
