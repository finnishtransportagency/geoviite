import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import { Selection } from 'selection/selection-model';
import {
    filterAlignmentPoints,
    GeometryPlanLayout,
    AlignmentPoint,
    PlanLayoutAlignment,
} from 'track-layout/track-layout-model';
import { clearFeatures, getVisiblePlans, pointToCoords } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { getLinkedAlignmentIdsInPlan } from 'linking/linking-api';
import { PublishType } from 'common/common-model';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { AlignmentHeader, toMapAlignmentResolution } from 'track-layout/layout-map-api';
import { ChangeTimes } from 'common/common-slice';
import {
    findMatchingAlignments,
    getTickStyles,
    setAlignmentFeatureProperty,
} from 'map/layers/utils/alignment-layer-utils';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { cache } from 'cache/cache';
import { MapTile } from 'map/map-model';

const alignmentFeatureCache = cache<string, Feature<LineString>>(500);

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
    planId: GeometryPlanId,
    alignment: AlignmentWithLinking,
    selection: Selection,
    resolution: number,
): Feature<LineString> {
    const isAlignmentSelected = selection.selectedItems.geometryAlignmentIds.find(
        ({ geometryId }) => geometryId == alignment.header.id,
    );

    const cacheKey = `${alignment.header.id}-${resolution}-${isAlignmentSelected}-${alignment.linked}`;
    return alignmentFeatureCache.getOrCreate(cacheKey, () => {
        const styles: Style[] = [];

        const feature = new Feature({
            geometry: new LineString(alignment.points.map(pointToCoords)),
        });

        let alignmentStyle = isAlignmentSelected
            ? selectedUnlinkedAlignmentStyle
            : unlinkedAlignmentStyle;

        if (alignment.linked) {
            alignmentStyle = isAlignmentSelected
                ? selectedLinkedAlignmentStyle
                : linkedAlignmentStyle;
        }

        styles.push(alignmentStyle);

        if (resolution <= Limits.GEOMETRY_TICKS) {
            styles.push(
                ...getTickStyles(alignment.points, alignment.segmentMValues, 10, alignmentStyle),
            );
        }

        feature.setStyle(styles);

        setAlignmentFeatureProperty(feature, {
            trackNumber: undefined,
            header: alignment.header,
            points: alignment.points,
            planId: planId,
        });

        return feature;
    });
}

type AlignmentWithLinking = {
    header: AlignmentHeader;
    points: AlignmentPoint[];
    segmentMValues: number[];
    linked: boolean;
};

function getAlignmentsWithLinking(
    alignments: PlanLayoutAlignment[],
    linkedAlignmentIds: GeometryAlignmentId[],
    resolution: number,
): AlignmentWithLinking[] {
    return alignments.map((alignment) => {
        const points = alignment.polyLine?.points || [];
        return {
            header: alignment.header,
            points: filterAlignmentPoints(toMapAlignmentResolution(resolution), points),
            segmentMValues: alignment.segmentMValues,
            linked: alignment.header.id ? linkedAlignmentIds.includes(alignment.header.id) : false,
        };
    });
}

let newestLayerId = 0;

type PlanAlignments = {
    planId: GeometryPlanId;
    alignments: AlignmentWithLinking[];
};
export function createGeometryAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    manuallySetPlan?: GeometryPlanLayout,
): MapLayer {
    const layerId = ++newestLayerId;

    const visibleAlignmentIds = manuallySetPlan
        ? manuallySetPlan.alignments.map((a) => a.header.id)
        : selection.visiblePlans.flatMap((p) => p.alignments);
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const olLayer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = true;

    const plansPromise: Promise<GeometryPlanLayout[]> = manuallySetPlan
        ? Promise.resolve([manuallySetPlan])
        : getVisiblePlans(selection.visiblePlans, mapTiles, changeTimes);

    const planAlignmentsPromise: Promise<PlanAlignments[]> = plansPromise.then((plans) =>
        Promise.all(
            plans.map((plan: GeometryPlanLayout) => {
                const linksPromise: Promise<GeometryAlignmentId[]> =
                    plan.planDataType == 'TEMP'
                        ? Promise.resolve([])
                        : getLinkedAlignmentIdsInPlan(plan.planId, publishType);
                return linksPromise.then((links) => ({
                    planId: plan.planId,
                    alignments: getAlignmentsWithLinking(
                        plan.alignments.filter((a) => visibleAlignmentIds.includes(a.header.id)),
                        links,
                        resolution,
                    ),
                }));
            }),
        ),
    );

    planAlignmentsPromise
        .then((plans) =>
            plans.map((plan) =>
                plan.alignments.map((alignment) =>
                    createAlignmentFeature(plan.planId, alignment, selection, resolution),
                ),
            ),
        )
        .then((f) => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
                vectorSource.addFeatures(f.flat());
            }
        })
        .catch(() => {
            if (layerId === newestLayerId) clearFeatures(vectorSource);
        })
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
