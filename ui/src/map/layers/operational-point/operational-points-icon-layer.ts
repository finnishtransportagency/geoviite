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
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';
import {
    findMatchingOperationalPoints,
    getOperationalPointsFromApi,
    isBeingMoved,
    operationalPointFeatureModeBySelection,
    renderOperationalPointCircleFeature,
    filterByResolution,
    findMatchingOperationalPointCluster,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-icon-layer';

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
        const pointsInStack: OperationalPoint[] = [];

        return points
            .filter(
                (point) =>
                    point.state !== 'DELETED' &&
                    !isBeingMoved(linkingState, point.id) &&
                    filterByResolution(point, resolution),
            )
            .map((point, _, allPoints) => {
                const stackedPoints = allPoints.filter(
                    (point2) =>
                        point.id !== point2.id &&
                        point.location &&
                        point2.location &&
                        getPlanarDistanceUnwrapped(
                            point.location.x,
                            point.location.y,
                            point2.location.x,
                            point2.location.y,
                        ) /
                            resolution <
                            1,
                );

                pointsInStack.push(...stackedPoints);
                const currentPoints = [point, ...stackedPoints];

                return !pointsInStack.includes(point)
                    ? renderOperationalPointCircleFeature(
                          point,
                          operationalPointFeatureModeBySelection(point.id, selection),
                          point.location,
                          currentPoints,
                      )
                    : undefined;
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
