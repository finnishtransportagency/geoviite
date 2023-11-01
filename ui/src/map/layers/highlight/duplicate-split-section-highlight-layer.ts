import { MapTile } from 'map/map-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import { redHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import { getPartialPolyLine } from 'utils/math-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { SplittingState } from 'tool-panel/location-track/split-store';

function createFeatures(
    alignments: AlignmentDataHolder[],
    originLocationTrackId: LocationTrackId,
    splitBounds: SplitBounds[],
): Feature<LineString>[] {
    return alignments
        .filter((alignment) => alignment.header.id === originLocationTrackId)
        .flatMap(({ points }) =>
            splitBounds.map(({ startM, endM }) => {
                const polyline = getPartialPolyLine(points, startM, endM);
                const lineString = new LineString(polyline);
                const feature = new Feature({ geometry: lineString });

                feature.setStyle(redHighlightStyle);

                return feature;
            }),
        );
}

let newestLayerId = 0;

type SplitBounds = {
    startM: number;
    endM: number;
    duplicateOf: LocationTrackId | undefined;
};

const splitExtents = (splittingState: SplittingState): SplitBounds[] => {
    const initialSplit = {
        duplicateOf: splittingState.initialSplit.duplicateOf,
        startM: 0,
        endM:
            splittingState.splits.length > 0
                ? splittingState.splits[0].distance
                : splittingState.endLocation.m,
    };

    const splitsSorted = [...splittingState.splits].sort((a, b) => a.distance - b.distance);
    const rest = splittingState.splits.map((split) => {
        const startM = split.distance;
        const endM =
            splitsSorted.find((split2) => split2.distance > split.distance)?.distance ||
            splittingState.endLocation.m;
        return { duplicateOf: split.duplicateOf, startM, endM };
    });

    return [initialSplit, ...rest];
};

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
    const splitBounds = splittingState
        ? splitExtents(splittingState).filter((split) => split.duplicateOf)
        : [];

    let inFlight = false;
    if (resolution <= HIGHLIGHTS_SHOW && splittingState) {
        inFlight = true;
        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            .then((alignments) => {
                if (layerId === newestLayerId) {
                    const features = createFeatures(
                        alignments,
                        splittingState.originLocationTrack.id,
                        splitBounds,
                    );

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                }
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
        name: 'plan-section-highlight-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
