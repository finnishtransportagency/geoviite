import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { AlignmentDataHolder, AlignmentHeader } from 'track-layout/layout-map-api';
import { ItemCollections, Selection } from 'selection/selection-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import Feature from 'ol/Feature';
import { LineString, Point } from 'ol/geom';
import { getTickStyle, pointToCoords, setAlignmentData } from 'map/layers/utils/layer-utils';

const locationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 0,
});

const highlightedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 2,
});

const selectedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 2,
    }),
    zIndex: 2,
});

const referenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 3,
    }),
    zIndex: 0,
});

const highlightedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 3,
    }),
    zIndex: 1,
});

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 1,
});

const endPointTickStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 1,
});

const highlightedEndPointTickStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 1,
});

export function createAlignmentFeatures(
    alignments: AlignmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    showEndTicks: boolean,
): Feature<LineString | Point>[] {
    return alignments.flatMap((alignment) => {
        const { selected, isLinking, highlighted } = getAlignmentHeaderStates(
            alignment,
            selection,
            linkingState,
        );

        const lineString = new LineString(alignment.points.map(pointToCoords));
        const features: Feature<LineString | Point>[] = [];
        const alignmentFeature = new Feature({ geometry: lineString });
        features.push(alignmentFeature);

        const styles = [];
        const isReferenceLine = alignment.header.alignmentType === 'REFERENCE_LINE';

        if (selected || isLinking) {
            styles.push(isReferenceLine ? selectedReferenceLineStyle : selectedLocationTrackStyle);
        } else if (highlighted) {
            styles.push(
                isReferenceLine ? highlightedReferenceLineStyle : highlightedLocationTrackStyle,
            );
        } else styles.push(isReferenceLine ? referenceLineStyle : locationTrackStyle);

        alignmentFeature.setStyle(styles);

        if (showEndTicks) {
            features.push(...getEndPointTicks(alignment, selected || isLinking || highlighted));
        }

        setAlignmentData(alignmentFeature, alignment);

        return features;
    });
}

function includes(selection: ItemCollections, alignment: AlignmentHeader): boolean {
    switch (alignment.alignmentType) {
        case 'REFERENCE_LINE': {
            const tnId = alignment.trackNumberId;
            return tnId != null && selection.trackNumbers.includes(tnId);
        }
        case 'LOCATION_TRACK': {
            return selection.locationTracks.includes(alignment.id);
        }
    }
}

export function getAlignmentHeaderStates(
    { header }: AlignmentDataHolder,
    selection: Selection,
    linkingState: LinkingState | undefined,
) {
    const selected = includes(selection.selectedItems, header);
    const highlighted = includes(selection.highlightedItems, header);
    const isLinking = linkingState
        ? (linkingState.type == LinkingType.LinkingGeometryWithAlignment ||
              linkingState.type == LinkingType.LinkingAlignment) &&
          linkingState.layoutAlignmentType == header.alignmentType &&
          linkingState.layoutAlignmentInterval.start?.alignmentId === header.id
        : false;

    return {
        selected,
        highlighted,
        isLinking,
    };
}

function getEndPointTicks(alignment: AlignmentDataHolder, contrast: boolean) {
    const ticks: Feature<Point>[] = [];
    const points = alignment.points;

    if (points.length >= 2) {
        if (points[0].m === 0) {
            const fP = pointToCoords(points[0]);
            const sP = pointToCoords(points[1]);

            const startF = new Feature({ geometry: new Point(fP) });

            startF.setStyle(
                getTickStyle(
                    fP,
                    sP,
                    6,
                    'start',
                    contrast ? highlightedEndPointTickStyle : endPointTickStyle,
                ),
            );

            ticks.push(startF);
        }

        const lastIdx = points.length - 1;
        if (points[lastIdx].m === alignment.header.length) {
            const lP = pointToCoords(points[lastIdx]);
            const sLP = pointToCoords(points[lastIdx - 1]);

            const endF = new Feature({ geometry: new Point(lP) });
            endF.setStyle(
                getTickStyle(
                    sLP,
                    lP,
                    6,
                    'end',
                    contrast ? highlightedEndPointTickStyle : endPointTickStyle,
                ),
            );

            ticks.push(endF);
        }
    }

    return ticks;
}
