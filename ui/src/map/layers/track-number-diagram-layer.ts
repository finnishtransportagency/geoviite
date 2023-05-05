import { LineString, Point } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, TrackNumberDiagramLayer } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy } from 'utils/array-utils';
import * as Limits from 'map/layers/layer-visibility-limits';
import { getBadgeDrawDistance } from 'map/layers/layer-visibility-limits';
import OlView from 'ol/View';
import { Feature } from 'ol';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { BadgeColor, createBadgePoints, createMapAlignmentBadgeFeature } from './alignment-layer';

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

const getColorForTrackNumber = (id: LayoutTrackNumberId) => {
    const c = colors[parseInt(id.replace(/^\D+/g, '')) % colors.length];

    return c + '55'; //55 ~ 33 % opacity
};

export function createTrackNumberDiagramLayerAdapter(
    mapLayer: TrackNumberDiagramLayer,
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    olView: OlView,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    referenceLinesVisible: boolean,
): OlLayerAdapter {
    const adapterId = ++newestTrackNumberDiagramAdapterId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer =
        existingOlLayer ||
        new VectorLayer({
            source: vectorSource,
        });

    layer.setVisible(mapLayer.visible);

    const resolution = olView.getResolution() || 0;
    const fetchType = resolution > Limits.ALL_ALIGNMENTS ? 'REFERENCE_LINES' : 'ALL';

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, fetchType).then((alignments) => {
        if (adapterId != newestTrackNumberDiagramAdapterId) return;

        const referenceLineBadges = referenceLinesVisible
            ? []
            : getReferenceLineBadges(alignments, resolution);

        const diagramFeatures = getDiagramFeatures(alignments);

        vectorSource.clear();
        vectorSource.addFeatures([...diagramFeatures, ...referenceLineBadges]);
    });

    return {
        layer: layer,
    };
}

function getReferenceLineBadges(alignments: AlignmentDataHolder[], resolution: number) {
    return alignments
        .filter((a) => a.header.alignmentType === 'REFERENCE_LINE')
        .filter((a) => a.trackNumber)
        .flatMap((a) => {
            const badgePoints = createBadgePoints(a.points, getBadgeDrawDistance(resolution) || 0);

            return createMapAlignmentBadgeFeature(
                a.trackNumber?.number || '', //Ensured by filter
                badgePoints,
                BadgeColor.DARK,
                false,
            );
        });
}

function getDiagramFeatures(alignments: AlignmentDataHolder[]) {
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

        return alignments.map((a) => {
            const feature = new Feature({
                geometry: new LineString(a.points.map((p) => [p.x, p.y])),
            });

            feature.setStyle(style);

            return feature;
        });
    });
}
