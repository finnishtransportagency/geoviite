import { MapTile } from 'map/map-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import { redHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getLocationTrackInfoboxExtras } from 'track-layout/layout-location-track-api';

function createFeatures(
    alignments: AlignmentDataHolder[],
    duplicateIds: LocationTrackId[],
): Feature<LineString>[] {
    return alignments
        .filter((alignment) => duplicateIds.includes(alignment.header.id))
        .flatMap(({ points }) => {
            const polyline = points.map(pointToCoords);
            const lineString = new LineString(polyline);
            const feature = new Feature({ geometry: lineString });

            feature.setStyle(redHighlightStyle);

            return feature;
        });
}

let newestLayerId = 0;

export function createDuplicateSplitSectionHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    splittingState: SplittingState | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= HIGHLIGHTS_SHOW && splittingState) {
        inFlight = true;
        getLocationTrackInfoboxExtras(splittingState.originLocationTrack.id, publishType)
            .then((extras) => {
                getMapAlignmentsByTiles(changeTimes, mapTiles, publishType).then((alignments) => {
                    if (layerId === newestLayerId) {
                        const features = createFeatures(
                            alignments,
                            extras?.duplicates.map((dupe) => dupe.id) || [],
                        );

                        clearFeatures(vectorSource);
                        vectorSource.addFeatures(features);
                    }
                });
            })
            .catch(() => {
                if (layerId === newestLayerId) clearFeatures(vectorSource);
            })
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'duplicate-split-section-highlight-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
