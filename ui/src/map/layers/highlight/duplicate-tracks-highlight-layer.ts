import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import { MapTile } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';

const duplicateTrackHighlightStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBlueHighlight,
        width: 12,
    }),
});

function createFeatures(locationTracks: AlignmentDataHolder[]): Feature<LineString>[] {
    return locationTracks
        .filter((lt) => lt.header.duplicateOf)
        .flatMap(({ points }) => {
            const lineString = new LineString(points.map(pointToCoords));
            const feature = new Feature({ geometry: lineString });

            feature.setStyle(duplicateTrackHighlightStyle);

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
                if (layerId !== newestLayerId) return;

                const features = createFeatures(locationTracks);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
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
