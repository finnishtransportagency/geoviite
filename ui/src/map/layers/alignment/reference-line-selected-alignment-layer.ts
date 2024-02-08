import { LineString, Point as OlPoint } from 'ol/geom';
import { MapTile } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { getSelectedReferenceLineMapAlignmentByTiles } from 'track-layout/layout-map-api';
import { createAlignmentFeature } from '../utils/alignment-layer-utils';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

let newestLayerId = 0;

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 1,
});

export function createSelectedReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString | OlPoint>> | undefined,
    selection: Selection,
    publishType: PublishType,
    changeTimes: ChangeTimes,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = true;
    const selectedTrackNumber = selection.selectedItems.trackNumbers[0];
    const alignmentPromise = selectedTrackNumber
        ? getSelectedReferenceLineMapAlignmentByTiles(
              changeTimes,
              mapTiles,
              publishType,
              selectedTrackNumber,
          )
        : Promise.resolve([]);

    alignmentPromise
        .then((referenceLines) => {
            if (layerId !== newestLayerId) return;

            const alignmentFeatures = createAlignmentFeature(
                referenceLines[0],
                false,
                selectedReferenceLineStyle,
            );

            clearFeatures(vectorSource);
            vectorSource.addFeatures(alignmentFeatures);
        })
        .catch(() => {
            if (layerId === newestLayerId) {
                clearFeatures(vectorSource);
            }
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'reference-line-selected-alignment-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
