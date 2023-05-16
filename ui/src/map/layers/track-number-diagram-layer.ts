import { LineString, Point } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy } from 'utils/array-utils';
import * as Limits from 'map/layers/layer-visibility-limits';
import { getBadgeDrawDistance } from 'map/layers/layer-visibility-limits';
import { Feature } from 'ol';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { BadgeColor, createMapAlignmentBadgeFeature, getBadgePoints } from './alignment-layer';
import { pointToCoords } from 'map/layers/layer-utils';

let newestTrackNumberDiagramLayerId = 0;

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

export function createTrackNumberDiagramLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
    resolution: number,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    referenceLinesVisible: boolean,
): MapLayer {
    const layerId = ++newestTrackNumberDiagramLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const fetchType = resolution > Limits.ALL_ALIGNMENTS ? 'REFERENCE_LINES' : 'ALL';

    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, fetchType)
        .then((alignments) => {
            if (layerId != newestTrackNumberDiagramLayerId) return;

            const referenceLineBadges = referenceLinesVisible
                ? []
                : getReferenceLineBadges(alignments, resolution);

            const diagramFeatures = getDiagramFeatures(alignments);

            vectorSource.clear();
            vectorSource.addFeatures([...diagramFeatures, ...referenceLineBadges]);
        })
        .catch(vectorSource.clear);

    return {
        name: 'track-number-diagram-layer',
        layer: layer,
    };
}

function getReferenceLineBadges(alignments: AlignmentDataHolder[], resolution: number) {
    return alignments
        .filter((a) => a.header.alignmentType === 'REFERENCE_LINE')
        .filter((a) => a.trackNumber)
        .flatMap((a) => {
            const badgePoints = getBadgePoints(a.points, getBadgeDrawDistance(resolution) || 0);

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
                geometry: new LineString(a.points.map(pointToCoords)),
            });

            feature.setStyle(style);

            return feature;
        });
    });
}
