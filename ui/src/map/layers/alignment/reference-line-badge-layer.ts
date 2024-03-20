import { Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import {
    AlignmentDataHolder,
    getReferenceLineMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LinkingState } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentBadgeFeatures,
    getBadgeDrawDistance,
} from 'map/layers/utils/badge-layer-utils';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';

const layerName: MapLayerName = 'reference-line-badge-layer';

export function createReferenceLineBadgeLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentDataHolder[]> = getReferenceLineMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const createFeatures = (referenceLines: AlignmentDataHolder[]) => {
        const badgeDrawDistance = getBadgeDrawDistance(resolution) || 0;
        return createAlignmentBadgeFeatures(
            referenceLines,
            selection,
            linkingState,
            badgeDrawDistance,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
