import { MapLayerName } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    createLayer,
    GeoviiteMapLayer,
    getPlanarDistanceUnwrapped,
    loadLayerData,
} from 'map/layers/utils/layer-utils';
import { OperationalPoint } from 'track-layout/track-layout-model';
import OlView from 'ol/View';
import { filterNotEmpty, first } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import { Point, Rectangle } from 'model/geometry';
import {
    findMatchingOperationalPoints,
    getOperationalPointsFromApi,
    isBeingMoved,
    operationalPointFeatureModeBySelection,
    renderOperationalPointCircleFeature,
    filterByResolution,
    findMatchingOperationalPointCluster,
    renderClusteredOperationalPointCircleFeature,
    operationalPointClusterFeatureModeBySelection,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState } from 'linking/linking-model';
import { expectDefined } from 'utils/type-utils';

const LAYER_NAME: MapLayerName = 'operational-points-icon-layer';
const OPERATIONAL_POINT_CLUSTERING_DISTANCE = 1;

const inTheSameLocation = (point: Point, point2: Point, resolution: number): boolean =>
    getPlanarDistanceUnwrapped(point.x, point.y, point2.x, point2.y) / resolution <
    OPERATIONAL_POINT_CLUSTERING_DISTANCE;

type OperationalPointCluster = {
    location: Point;
    operationalPoints: OperationalPoint[];
};

const clusterNearbyOperationalPoints = (
    prefilteredPoints: OperationalPoint[],
    resolution: number,
): OperationalPointCluster[] =>
    prefilteredPoints.reduce((clusters, point) => {
        if (point.location) {
            const location = point.location;
            const cluster = clusters.find(({ location: clusterLocation }) =>
                inTheSameLocation(location, clusterLocation, resolution),
            );

            if (cluster) {
                cluster.operationalPoints.push(point);
            } else {
                clusters.push({
                    location: point.location,
                    operationalPoints: [point],
                });
            }
        }
        return clusters;
    }, [] as OperationalPointCluster[]);

export function createOperationalPointIconLayer(
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);
    const resolution = olView.getResolution() || 0;
    const onLoadingChange = () => {};

    const createFeatures = (points: OperationalPoint[]) => {
        const prefilteredPoints = points.filter(
            (point) =>
                point.state !== 'DELETED' &&
                !isBeingMoved(linkingState, point.id) &&
                filterByResolution(point, resolution),
        );

        return clusterNearbyOperationalPoints(prefilteredPoints, resolution)
            .map((cluster) => {
                if (cluster.operationalPoints.length === 0) {
                    return undefined;
                } else if (cluster.operationalPoints.length === 1) {
                    const point = expectDefined(first(cluster.operationalPoints));
                    return renderOperationalPointCircleFeature(
                        point,
                        operationalPointFeatureModeBySelection(point.id, selection),
                        cluster.location,
                    );
                } else {
                    return renderClusteredOperationalPointCircleFeature(
                        operationalPointClusterFeatureModeBySelection(
                            cluster.operationalPoints.map((point) => point.id),
                            selection,
                        ),
                        cluster.operationalPoints,
                    );
                }
            })
            .filter(filterNotEmpty);
    };

    loadLayerData(
        source,
        isLatest,
        onLoadingChange,
        getOperationalPointsFromApi(layoutContext),
        createFeatures,
    );

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const clusters = findMatchingOperationalPointCluster(hitArea, source, options);

            if (!clusters.length) {
                return {
                    operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                        (operationalPoint) => operationalPoint.id,
                    ),
                    operationalPointClusters: [],
                };
            } else {
                return {
                    operationalPoints: [],
                    operationalPointClusters: clusters,
                };
            }
        },
    };
}
