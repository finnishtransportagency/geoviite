import { MapLayerName } from 'map/map-model';
import { Polygon as OlPolygon } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import {
    getOperationalPointsFromApi,
    operationalPointFeatureModeBySelection,
    renderOperationalPointAreaFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState, LinkingType } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-area-layer';

export function createOperationalPointAreaLayer(
    existingOlLayer: GeoviiteMapLayer<OlPolygon> | undefined,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);
    const onLoadingChange = () => {};

    const isBeingEdited = (id: OperationalPointId) =>
        linkingState &&
        linkingState.type === LinkingType.PlacingOperationalPointArea &&
        linkingState.operationalPoint.id === id;

    const createFeatures = (points: OperationalPoint[]) =>
        points
            .filter((point) => point.state !== 'DELETED' && !isBeingEdited(point.id))
            .map((point) => {
                return point.polygon
                    ? renderOperationalPointAreaFeature(
                          point.polygon,
                          operationalPointFeatureModeBySelection(point.id, selection),
                          undefined,
                      )
                    : undefined;
            })
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
