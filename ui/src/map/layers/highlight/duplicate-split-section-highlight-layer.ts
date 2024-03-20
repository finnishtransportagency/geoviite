import { MapLayerName, MapTile } from 'map/map-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import {
    blueSplitSectionStyle,
    redSplitSectionStyle,
} from 'map/layers/utils/highlight-layer-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getLocationTrackInfoboxExtras } from 'track-layout/layout-location-track-api';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

function createFeatures(
    alignments: AlignmentDataHolder[],
    duplicateIds: LocationTrackId[],
    linkedDuplicates: LocationTrackId[],
): Feature<LineString>[] {
    return alignments
        .filter((alignment) => duplicateIds.includes(alignment.header.id))
        .flatMap(({ points, header }) => {
            const polyline = points.map(pointToCoords);
            const lineString = new LineString(polyline);
            const feature = new Feature({ geometry: lineString });

            if (linkedDuplicates.includes(header.id)) {
                feature.setStyle(blueSplitSectionStyle);
            } else {
                feature.setStyle(redSplitSectionStyle);
            }

            return feature;
        });
}

type DuplicateSplitSectionData = {
    linkedDuplicates: LocationTrackId[];
    duplicates: LocationTrackId[];
    alignments: AlignmentDataHolder[];
};

async function getDuplicateSplitSectionData(
    splittingState: SplittingState | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    resolution: number,
): Promise<DuplicateSplitSectionData> {
    if (resolution <= HIGHLIGHTS_SHOW && splittingState) {
        const linkedDuplicates = splittingState.splits
            .map((split) => split.duplicateOf)
            .concat(splittingState.firstSplit.duplicateOf)
            .filter(filterNotEmpty);

        const [alignments, extras] = await Promise.all([
            getMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext),
            getLocationTrackInfoboxExtras(
                splittingState.originLocationTrack.id,
                layoutContext,
                changeTimes,
            ),
        ]);
        const duplicates = extras?.duplicates?.map((dupe) => dupe.id) || [];

        return { linkedDuplicates, duplicates, alignments };
    } else {
        return { linkedDuplicates: [], duplicates: [], alignments: [] };
    }
}

const layerName: MapLayerName = 'duplicate-split-section-highlight-layer';

export function createDuplicateSplitSectionHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    splittingState: SplittingState | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<DuplicateSplitSectionData> = getDuplicateSplitSectionData(
        splittingState,
        layoutContext,
        changeTimes,
        mapTiles,
        resolution,
    );

    const createOlFeatures = (data: DuplicateSplitSectionData) =>
        createFeatures(data.alignments, data.duplicates, data.linkedDuplicates);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createOlFeatures);

    return { name: layerName, layer: layer };
}
