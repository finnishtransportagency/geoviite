import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { pointToCoords } from 'map/layers/utils/layer-utils';

const alignmentBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBackground,
        width: 12,
        lineCap: 'butt',
    }),
});

export function createAlignmentBackgroundFeatures(
    alignments: AlignmentDataHolder[],
): Feature<LineString>[] {
    return alignments.map(({ points }) => {
        const alignmentFeature = new Feature({
            geometry: new LineString(points.map(pointToCoords)),
        });

        alignmentFeature.setStyle(alignmentBackgroundStyle);

        return alignmentFeature;
    });
}
