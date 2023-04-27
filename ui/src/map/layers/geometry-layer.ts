import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import OlView from 'ol/View';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import { GeometryLayer, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import {
    filterLayoutPoints,
    GeometryPlanLayout,
    LayoutPoint,
} from 'track-layout/track-layout-model';
import {
    getMatchingAlignmentDatas,
    getTickStyles,
    MatchOptions,
    setAlignmentData,
} from 'map/layers/layer-utils';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import * as Limits from 'map/layers/layer-visibility-limits';
import { LinkingState } from 'linking/linking-model';
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
function createFeatures(
    planLayout: GeometryPlanLayout,
    alignment: AlignmentWithLinking,
    selection: Selection,
    resolution: number,
): Feature<LineString>[] {
    const isAlignmentSelected = selection.selectedItems.geometryAlignments.find(
        (alignmentToCheck) => alignmentToCheck.geometryItem.id == alignment.header.id,
    );

    const lineString = new LineString(alignment.points.map((point) => [point.x, point.y]));
    const feature = new Feature<LineString>({
        geometry: lineString,
    });

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

    return [feature];
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

adapterInfoRegister.add('geometry', {
    createAdapter: function (
        _mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
        geometryLayer: GeometryLayer,
        selection: Selection,
        publishType: PublishType,
        _linkingState: LinkingState | undefined,
        changeTimes: ChangeTimes,
        olView: OlView,
    ): OlLayerAdapter {
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();
        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const olLayer: VectorLayer<VectorSource<LineString>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
            });

        olLayer.setVisible(geometryLayer.visible);

        const resolution = olView.getResolution() || 0;

        const features = Promise.all(
            selection.planLayouts.map((planLayout) => {
                const changeTime = getMaxTimestamp(
                    changeTimes.layoutReferenceLine,
                    changeTimes.layoutLocationTrack,
                );
                return getPlanLayoutAlignmentsWithLinking(
                    planLayout,
                    publishType,
                    changeTime,
                    resolution,
                ).then((alignments) =>
                    alignments.flatMap((alignment) =>
                        createFeatures(planLayout, alignment, selection, resolution),
                    ),
                );
            }),
        );

        features.then((f) => {
            vectorSource.clear();
            vectorSource.addFeatures(f.flat());
        });

        return {
            layer: olLayer,
            searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
                const matchOptions: MatchOptions = {
                    strategy: options.limit == 1 ? 'nearest' : 'limit',
                    limit: options.limit,
                };
                const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
                const holders =
                    features && getMatchingAlignmentDatas(hitArea, features, matchOptions);
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
    },
});
