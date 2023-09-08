import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { blueHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';

function createHighlightFeatures(locationTracks: AlignmentDataHolder[]): Feature<LineString>[] {
    return locationTracks
        .filter((lt) => lt.header.duplicateOf)
        .flatMap(({ points }) => {
            const feature = new Feature({ geometry: new LineString(points.map(pointToCoords)) });

            feature.setStyle(blueHighlightStyle);

            return feature;
        });
}

let newestLayerId = 0;

export function createDuplicateTracksHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= HIGHLIGHTS_SHOW) {
        inFlight = true;
        getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            .then((locationTracks) => {
                if (layerId === newestLayerId) {
                    const features = createHighlightFeatures(locationTracks);

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                }
            })
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'duplicate-tracks-highlight-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
