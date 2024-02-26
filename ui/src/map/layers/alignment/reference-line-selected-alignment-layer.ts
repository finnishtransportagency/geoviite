import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import {
    AlignmentDataHolder,
    getSelectedReferenceLineMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

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
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const selectedTrackNumber = selection.selectedItems.trackNumbers[0];
    const dataPromise: Promise<AlignmentDataHolder[]> = selectedTrackNumber
        ? getSelectedReferenceLineMapAlignmentByTiles(
              changeTimes,
              mapTiles,
              publishType,
              selectedTrackNumber,
          )
        : Promise.resolve([]);

    const createFeatures = (referenceLines: AlignmentDataHolder[]) =>
        createAlignmentFeature(referenceLines[0], false, selectedReferenceLineStyle);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer };
}
