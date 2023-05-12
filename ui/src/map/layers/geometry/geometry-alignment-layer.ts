import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import { Selection } from 'selection/selection-model';
import {
    filterLayoutPoints,
    GeometryPlanLayout,
    LayoutPoint,
} from 'track-layout/track-layout-model';
import {
    getMatchingAlignmentData,
    getTickStyles,
    MatchOptions,
    pointToCoords,
    setAlignmentData,
} from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { getLinkedAlignmentIdsInPlan } from 'linking/linking-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { PublishType, TimeStamp } from 'common/common-model';
import { filterUniqueById } from 'utils/array-utils';
import { GeometryPlanId } from 'geometry/geometry-model';
import { AlignmentHeader, toMapAlignmentResolution } from 'track-layout/layout-map-api';
import { getMaxTimestamp } from 'utils/date-utils';
import { ChangeTimes } from 'common/common-slice';

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

/**
 * Creates OL feature objects by alignments.
 *
 * @param planLayout
 * @param alignment
 * @param selection
 * @param resolution
 */
function createFeature(
    planLayout: GeometryPlanLayout,
    alignment: AlignmentWithLinking,
    selection: Selection,
    resolution: number,
): Feature<LineString> {
    const isAlignmentSelected = selection.selectedItems.geometryAlignments.find(
        (alignmentToCheck) => alignmentToCheck.geometryItem.id == alignment.header.id,
    );

    const lineString = new LineString(alignment.points.map(pointToCoords));
    const feature = new Feature({ geometry: lineString });

    feature.setStyle(function (feature: Feature<LineString>) {
        let alignmentStyle = isAlignmentSelected
            ? selectedUnlinkedAlignmentStyle
            : unlinkedAlignmentStyle;

        if (alignment.linked) {
            alignmentStyle = isAlignmentSelected
                ? selectedLinkedAlignmentStyle
                : linkedAlignmentStyle;
        }

        const styles = [alignmentStyle];

        const geom = feature.getGeometry();
        if (geom instanceof LineString && resolution <= Limits.GEOMETRY_TICKS) {
            styles.push(
                ...getTickStyles(alignment.points, alignment.segmentMValues, 10, alignmentStyle),
            );
        }

        return styles;
    });

    setAlignmentData(feature, {
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
    layoutAlignmentChangeTime: TimeStamp,
    resolution: number,
): Promise<AlignmentWithLinking[]> {
    const planLayoutWithGeometry =
        planLayout.planDataType == 'STORED'
            ? await getTrackLayoutPlan(planLayout.planId, layoutAlignmentChangeTime)
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
            .filter((alignment) =>
                planLayout.alignments.some(
                    (alignment2) => alignment.header.id == alignment2.header.id,
                ),
            )
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

let newestGeometryLayerId = 0;

export function createGeometryAlignmentLayer(
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestGeometryLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const olLayer = existingOlLayer || new VectorLayer({ source: vectorSource });

    const changeTime = getMaxTimestamp(
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
    );

    const features = Promise.all(
        selection.planLayouts.map((planLayout) => {
            return getPlanLayoutAlignmentsWithLinking(
                planLayout,
                publishType,
                changeTime,
                resolution,
            ).then((alignments) =>
                alignments.map((alignment) =>
                    createFeature(planLayout, alignment, selection, resolution),
                ),
            );
        }),
    );

    features.then((f) => {
        if (layerId == newestGeometryLayerId) {
            vectorSource.clear();
            vectorSource.addFeatures(f.flat());
        }
    });

    return {
        name: 'geometry-alignment-layer',
        layer: olLayer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const matchOptions: MatchOptions = {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: options.limit,
            };
            const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
            const holders = features && getMatchingAlignmentData(hitArea, features, matchOptions);
            const alignments = holders
                .filter(filterUniqueById((data) => data.header.id)) // pick unique alignments
                .slice(0, options.limit)
                .map((data) => {
                    return {
                        planId: data.planId as GeometryPlanId,
                        geometryItem: { ...data.header },
                    };
                });

            return {
                geometryAlignments: alignments,
            };
        },
    };
}
