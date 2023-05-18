import { AlignmentDataHolder } from 'track-layout/layout-map-api';
import { AlignmentHighlight } from 'map/map-model';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { getPartialPolyLine } from 'utils/math-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { Style } from 'ol/style';

export function createHighlightFeatures(
    alignments: AlignmentDataHolder[],
    linkingInfo: AlignmentHighlight[],
    style: Style,
): Feature<LineString>[] {
    return alignments.flatMap(({ points, header }) => {
        return linkingInfo
            .filter((h) => h.id === header.id && h.type == header.alignmentType)
            .flatMap(({ ranges }) => {
                return ranges
                    .filter((r) => r.max > points[0].m && r.min < points[points.length - 1].m)
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
