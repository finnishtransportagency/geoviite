import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import { Selection } from 'selection/selection-model';
import {
    filterLayoutPoints,
    GeometryPlanLayout,
    LayoutPoint,
} from 'track-layout/track-layout-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { getLinkedAlignmentIdsInPlan } from 'linking/linking-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { PublishType, TimeStamp } from 'common/common-model';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { AlignmentHeader, toMapAlignmentResolution } from 'track-layout/layout-map-api';
import { getMaxTimestamp } from 'utils/date-utils';
import { ChangeTimes } from 'common/common-slice';
import {
    findMatchingAlignments,
    getTickStyles,
    setAlignmentFeatureProperty,
} from 'map/layers/utils/alignment-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

const unlinkedAlignmentStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['unlinkedGeometryAlignment'],
        width: 2,
    }),
    zIndex: 1,
});

const selectedUnlinkedAlignmentStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['selectedUnlinkedGeometryAlignment'],
        width: 2,
    }),
    zIndex: 2,
});

const linkedAlignmentStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['linkedGeometryAlignment'],
        width: 2,
    }),
    zIndex: 0,
});

const selectedLinkedAlignmentStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['selectedLinkedGeometryAlignment'],
        width: 2,
    }),
    zIndex: 2,
});

function createAlignmentFeature(
    planLayout: GeometryPlanLayout,
    alignment: AlignmentWithLinking,
    selection: Selection,
    resolution: number,
): Feature<LineString> {
    const isAlignmentSelected = selection.selectedItems.geometryAlignmentIds.find(
        ({ geometryId }) => geometryId == alignment.header.id,
    );

    const styles: Style[] = [];

    const feature = new Feature({ geometry: new LineString(alignment.points.map(pointToCoords)) });

    let alignmentStyle = isAlignmentSelected
        ? selectedUnlinkedAlignmentStyle
        : unlinkedAlignmentStyle;

    if (alignment.linked) {
        alignmentStyle = isAlignmentSelected ? selectedLinkedAlignmentStyle : linkedAlignmentStyle;
    }

    styles.push(alignmentStyle);

    if (resolution <= Limits.GEOMETRY_TICKS) {
        styles.push(
            ...getTickStyles(alignment.points, alignment.segmentMValues, 10, alignmentStyle),
        );
    }

    feature.setStyle(styles);

    setAlignmentFeatureProperty(feature, {
        trackNumber: null,
        header: alignment.header,
        points: alignment.points,
        planId: planLayout.planId,
    });

    return feature;
}

type AlignmentWithLinking = {
    header: AlignmentHeader;
    points: LayoutPoint[];
    segmentMValues: number[];
    linked: boolean;
};

async function getPlanLayoutAlignmentsWithLinking(
    planLayout: GeometryPlanLayout,
    publishType: PublishType,
    changeTime: TimeStamp,
    resolution: number,
): Promise<AlignmentWithLinking[]> {
    const planLayoutWithGeometry =
        planLayout.planDataType == 'STORED'
            ? await getTrackLayoutPlan(planLayout.planId, changeTime)
            : planLayout;

    if (!planLayoutWithGeometry) {
        return [];
    }

    const linkedAlignmentIds =
        planLayout.planDataType === 'STORED'
            ? await getLinkedAlignmentIdsInPlan(planLayout.planId, publishType)
            : [];

    return (
        planLayoutWithGeometry.alignments
            // Include alignments from original layout only
            .filter((a1) => planLayout.alignments.some((a2) => a1.header.id === a2.header.id))
            .map((alignment) => {
                const points = alignment.polyLine?.points || [];
                return {
                    header: alignment.header,
                    points: filterLayoutPoints(toMapAlignmentResolution(resolution), points),
                    segmentMValues: alignment.segmentMValues,
                    linked: alignment.header.id
                        ? linkedAlignmentIds.includes(alignment.header.id)
                        : false,
                };
            })
    );
}

let newestLayerId = 0;

export function createGeometryAlignmentLayer(
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const olLayer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
    );

    let inFlight = true;
    Promise.all(
        selection.planLayouts.map((planLayout) =>
            getPlanLayoutAlignmentsWithLinking(
                planLayout,
                publishType,
                changeTime,
                resolution,
            ).then((alignments) =>
                alignments.map((alignment) =>
                    createAlignmentFeature(planLayout, alignment, selection, resolution),
                ),
            ),
        ),
    )
        .then((f) => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
                vectorSource.addFeatures(f.flat());
            }
        })
        .catch(() => clearFeatures(vectorSource))
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'geometry-alignment-layer',
        layer: olLayer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const features = findMatchingAlignments(hitArea, vectorSource, options);

            const geometryAlignmentIds = features
                .filter(filterUniqueById(({ header }) => header.id)) // pick unique alignments
                .map((data) =>
                    data.planId
                        ? {
                              planId: data.planId,
                              geometryId: data.header.id,
                          }
                        : undefined,
                )
                .filter(filterNotEmpty);

            return { geometryAlignmentIds };
        },
        requestInFlight: () => inFlight,
    };
}
