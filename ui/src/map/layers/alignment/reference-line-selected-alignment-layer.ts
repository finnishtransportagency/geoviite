import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import {
    AlignmentDataHolder,
    getSelectedReferenceLineMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { first } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 1,
});

const layerName: MapLayerName = 'reference-line-selected-alignment-layer';

export function createSelectedReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    splittingIsActive: boolean, // TODO: This will be removed when layer visibility logic is revised
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const selectedTrackNumber = first(selection.selectedItems.trackNumbers);
    const dataPromise: Promise<AlignmentDataHolder[]> =
        selectedTrackNumber && !splittingIsActive
            ? getSelectedReferenceLineMapAlignmentByTiles(
                  changeTimes,
                  mapTiles,
                  layoutContext,
                  selectedTrackNumber,
              )
            : Promise.resolve([]);

    const createFeatures = (referenceLines: AlignmentDataHolder[]) => {
        const selectedReferenceLine = first(referenceLines);
        if (!selectedReferenceLine) return [];

        return createAlignmentFeature(
            selectedReferenceLine,
            [selectedReferenceLineStyle],
            undefined,
        );
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer };
}
