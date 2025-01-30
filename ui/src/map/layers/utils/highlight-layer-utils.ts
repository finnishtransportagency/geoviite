import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import { AlignmentHighlight } from 'map/map-model';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { getPartialPolyLine } from 'utils/math-utils';
import { filterNotEmpty, first, last } from 'utils/array-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

export function createHighlightFeatures(
    alignments: AlignmentDataHolder[],
    linkingInfo: AlignmentHighlight[],
    style: Style,
): Feature<LineString>[] {
    return alignments.flatMap(({ points, header }) => {
        return linkingInfo
            .filter((h) => h.id === header.id && h.type === header.alignmentType)
            .flatMap(({ ranges }) => {
                const [firstPoint, lastPoint] = [first(points), last(points)];
                return ranges
                    .filter(
                        (r) =>
                            firstPoint && lastPoint && r.max > firstPoint.m && r.min < lastPoint.m,
                    )
                    .map((r) => {
                        const pointsWithinRange = getPartialPolyLine(points, r.min, r.max);
                        return pointsWithinRange.length > 1
                            ? new LineString(pointsWithinRange)
                            : undefined;
                    })
                    .filter(filterNotEmpty)
                    .map((ls) => {
                        const highlightFeature = new Feature({ geometry: ls });
                        highlightFeature.setStyle(style);

                        return highlightFeature;
                    });
            });
    });
}

export const blueHighlightStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBlueHighlight,
        width: 12,
    }),
});

export const redHighlightStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedHighlight,
        width: 12,
    }),
});

export const blueSplitSectionStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.splitSectionBlueHighlight,
        width: 12,
    }),
});

export const redSplitSectionStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.splitSectionRedHighlight,
        width: 12,
    }),
});

export const notOverlappingDuplicateSplitSectionStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.splitSectionNotOverlappingDuplicate,
        width: 6,
    }),
});
