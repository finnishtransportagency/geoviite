import { MapLayerName } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { OperationalPoint } from 'track-layout/track-layout-model';
import OlView from 'ol/View';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import {
    filterByResolution,
    getOperationalPointsFromApi,
    isBeingMoved,
    operationalPointFeatureModeBySelection,
    renderOperationalPointTextFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-badge-layer';

const compareSelected = (a: OperationalPoint, b: OperationalPoint, selection: Selection) => {
    const aSelected = selection.selectedItems.operationalPoints.includes(a.id);
    const bSelected = selection.selectedItems.operationalPoints.includes(b.id);

    if (aSelected && !bSelected) return -1;
    if (!aSelected && bSelected) return 1;
    return 0;
};

export function createOperationalPointBadgeLayer(
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true, true);
    const resolution = olView.getResolution() || 0;
    const onLoadingChange = () => {};

    const createFeatures = (points: OperationalPoint[]) =>
        points
            .filter(
                (point) =>
                    point.state !== 'DELETED' &&
                    !isBeingMoved(linkingState, point.id) &&
                    filterByResolution(point, resolution),
            )
            .toSorted((a, b) => compareSelected(a, b, selection))
            .map((point) =>
                renderOperationalPointTextFeature(
                    point,
                    operationalPointFeatureModeBySelection(point.id, selection),
                ),
            )
            .filter(filterNotEmpty);
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
    };
}
