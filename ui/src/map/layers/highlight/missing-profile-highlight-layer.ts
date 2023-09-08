import mapStyles from 'map/map.module.scss';
import { LineString } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import { MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    getLocationTrackSectionsWithoutProfileByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { createHighlightFeatures } from 'map/layers/utils/highlight-layer-utils';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';

const highlightBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedHighlight,
        width: 12,
    }),
});

let newestLayerId = 0;

export function createMissingProfileHighlightLayer(
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
        const locationTracksPromise = getLocationTrackMapAlignmentsByTiles(
            changeTimes,
            mapTiles,
            publishType,
        );

        const profilePromise = getLocationTrackSectionsWithoutProfileByTiles(
            changeTimes.layoutLocationTrack,
            publishType,
            mapTiles,
        );

        Promise.all([locationTracksPromise, profilePromise])
            .then(([locationTracks, sections]) => {
                if (layerId !== newestLayerId) return;

                const features = createHighlightFeatures(
                    locationTracks,
                    sections,
                    highlightBackgroundStyle,
                );

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource))
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'missing-profile-highlight-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
