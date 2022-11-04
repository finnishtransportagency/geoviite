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
import { GeometryPlanLayout, MapAlignment, simplifySegment } from 'track-layout/track-layout-model';
import { getMatchingSegmentDatas, getTickStyles, MatchOptions } from 'map/layers/layer-utils';
import { LayerItemSearchResult, OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import * as Limits from 'map/layers/layer-visibility-limits';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { LinkingState } from 'linking/linking-model';
import { getLinkedAlignmentIdsInPlan } from 'linking/linking-api';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import { PublishType, TimeStamp } from 'common/common-model';
import { filterUniqueById } from 'utils/array-utils';
import { GeometryPlanId } from 'geometry/geometry-model';
import { toMapAlignmentResolution } from 'track-layout/track-layout-api';
import { getMaxTimestamp } from 'utils/date-utils';

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
        (alignmentToCheck) => alignmentToCheck.geometryItem.id == alignment.id,
    );

    return alignment.segments
        .filter((segment) => segment.points.length >= 2)
        .flatMap((segment) => {
            const lineString = new LineString(segment.points.map((point) => [point.x, point.y]));
            const feature = new Feature<LineString>({
                geometry: lineString,
            });

            const isSelected =
                isAlignmentSelected ||
                selection.selectedItems.geometrySegments.find(
                    (segmentToCheck) => segmentToCheck.geometryItem.id == segment.id,
                );

            feature.setStyle(function (feature: Feature<LineString>) {
                let alignmentStyle = isSelected
                    ? selectedUnlinkedAlignmentStyle
                    : unlinkedAlignmentStyle;

                if (alignment.linked) {
                    alignmentStyle = isSelected
                        ? selectedLinkedAlignmentStyle
                        : linkedAlignmentStyle;
                }

                const styles = [alignmentStyle];

                const geom = feature.getGeometry();
                if (geom instanceof LineString && resolution <= Limits.GEOMETRY_TICKS) {
                    const coordinates = geom.getCoordinates();
                    styles.push(...getTickStyles(coordinates, 10, alignmentStyle));
                }

                return styles;
            });

            feature.set('segment-data', {
                trackNumber: null,
                segment: segment,
                alignment: alignment,
                planId: planLayout.planId,
            });

            return feature;
        });
}

type AlignmentWithLinking = MapAlignment & {
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
            .filter((alignmentWithGeom) =>
                planLayout.alignments.some((alignment2) => alignmentWithGeom.id == alignment2.id),
            )
            .map((alignment) => ({
                ...alignment,
                segments: alignment.segments.map((s) =>
                    simplifySegment(s, toMapAlignmentResolution(resolution)),
                ),
                linked: alignment.sourceId
                    ? linkedAlignmentIds.includes(alignment.sourceId)
                    : false,
            }))
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
                    features && getMatchingSegmentDatas(hitArea, features, matchOptions);
                const alignments = holders
                    .filter(filterUniqueById((data) => data.alignment.id)) // pick unique alignments
                    .slice(0, options.limit)
                    .map((data) => {
                        return {
                            planId: data.planId as GeometryPlanId,
                            geometryItem: { ...data.alignment, segments: [] },
                        };
                    });

                const segments = holders.slice(0, options.limit).map((data) => ({
                    planId: data.planId as GeometryPlanId,
                    geometryItem: data.segment,
                }));

                return {
                    geometryAlignments: alignments,
                    geometrySegments: segments,
                };
            },
        };
    },
});
