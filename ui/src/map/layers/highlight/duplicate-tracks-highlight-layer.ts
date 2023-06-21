import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { blueHighlightStyle } from 'map/layers/utils/highlight-layer-utils';

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

    if (resolution <= HIGHLIGHTS_SHOW) {
        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'LOCATION_TRACKS')
            .then((locationTracks) => {
                if (layerId === newestLayerId) {
                    const features = createHighlightFeatures(locationTracks);

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                }
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'duplicate-tracks-highlight-layer',
        layer: layer,
    };
}
