import { MapLayerName, MapTile } from 'map/map-model';
import { Polygon as OlPolygon } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { getOperationalPointsByPolygon } from 'track-layout/layout-operational-point-api';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import {
    operationalPointFeatureModeBySelection,
    renderOperationalPointAreaFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState, LinkingType } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-area-layer';

export function createOperationalPointAreaLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPolygon> | undefined,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);

    const getOperationalPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) =>
                    getOperationalPointsByPolygon(
                        tile,
                        layoutContext,
                        changeTimes.operationalPoints,
                    ),
                ),
            )
        )
            .flat()
            .filter(filterUniqueById((point) => point.id));
    };
    const onLoadingChange = () => {};

    const isBeingEdited = (id: OperationalPointId) =>
        linkingState &&
        linkingState.type === LinkingType.PlacingOperationalPointArea &&
        linkingState.operationalPoint.id === id &&
        !!linkingState.area;

    const createFeatures = (points: OperationalPoint[]) =>
        points
            .filter((point) => !isBeingEdited(point.id))
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
    loadLayerData(source, isLatest, onLoadingChange, getOperationalPointsFromApi(), createFeatures);

    return {
        name: LAYER_NAME,
        layer: layer,
    };
}
