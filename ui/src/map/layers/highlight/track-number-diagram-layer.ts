import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { Feature } from 'ol';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';

let newestLayerId = 0;

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

const getColorForTrackNumber = (id: LayoutTrackNumberId) => {
    const c = colors[parseInt(id.replace(/^\D+/g, '')) % colors.length];

    return c + '55'; //55 ~ 33 % opacity
};

function createDiagramFeatures(alignments: AlignmentDataHolder[]): Feature<LineString>[] {
    const perTrackNumber = groupBy(
        alignments,
        (a) => a.header.trackNumberId || a.trackNumber?.id || '',
    );

    return Object.entries(perTrackNumber).flatMap(([trackNumberId, alignments]) => {
        const style = new Style({
            stroke: new Stroke({
                color: getColorForTrackNumber(trackNumberId),
                width: 15,
                lineCap: 'butt',
            }),
        });

        return alignments.map(({ points }) => {
            const feature = new Feature({
                geometry: new LineString(points.map(pointToCoords)),
            });

            feature.setStyle(style);

            return feature;
        });
    });
}

export function createTrackNumberDiagramLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    const fetchType = resolution > Limits.ALL_ALIGNMENTS ? 'REFERENCE_LINES' : 'ALL';

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, fetchType)
        .then((alignments) => {
            if (layerId !== newestLayerId) return;

            const features = createDiagramFeatures(alignments);

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
        })
        .catch(() => clearFeatures(vectorSource));

    return {
        name: 'track-number-diagram-layer',
        layer: layer,
    };
}
