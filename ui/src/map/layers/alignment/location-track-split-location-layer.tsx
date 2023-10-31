import { MapLayer } from 'map/layers/utils/layer-model';
import { Point as OlPoint } from 'ol/geom';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';

let newestLayerId = 0;

const splitPointStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.selectedAlignmentLine }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
});

export const createLocationTrackSplitLocationLayer = (
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    splittingState: SplittingState | undefined,
): MapLayer => {
    const _layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    if (splittingState) {
        const points = splittingState.splits
            .map((split) => split.location)
            .concat([splittingState.initialSplit.location, splittingState.endLocation]);

        const features = points.map((location) => {
            const feature = new Feature({
                geometry: new OlPoint(pointToCoords(location)),
            });
            feature.setStyle(splitPointStyle);
            return feature;
        });

        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-split-location-layer',
        layer: layer,
        requestInFlight: () => false,
    };
};
