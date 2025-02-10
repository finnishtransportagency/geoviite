import { MapLayerName, MapTile } from 'map/map-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import {
    getLocationTrackMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import {
    blueSplitSectionStyle,
    notOverlappingDuplicateSplitSectionStyle,
    redSplitSectionStyle,
} from 'map/layers/utils/highlight-layer-utils';
import { LocationTrackId, splitPointsAreSame } from 'track-layout/track-layout-model';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { SplitDuplicateTrack } from 'track-layout/layout-location-track-api';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';

function createFeatures(
    alignments: LocationTrackAlignmentDataHolder[],
    duplicates: SplitDuplicateTrack[],
    linkedDuplicates: LocationTrackId[],
): Feature<LineString>[] {
    return alignments
        .flatMap((alignment) => {
            const duplicate = duplicates.find((duplicate) => duplicate.id === alignment.header.id);
            const overlappingStartPoint = duplicate?.status.startSplitPoint?.location;
            const overlappingEndPoint = duplicate?.status.endSplitPoint?.location;
            if (
                duplicate !== undefined &&
                overlappingStartPoint !== undefined &&
                overlappingEndPoint !== undefined
            ) {
                const pointCollections = [
                    {
                        type: 'before',
                        points: alignment.points.filter((p) => p.m <= overlappingStartPoint.m),
                    },
                    {
                        type: 'duplicate',
                        points: alignment.points.filter(
                            (p) => p.m >= overlappingStartPoint.m && p.m <= overlappingEndPoint.m,
                        ),
                    },
                    {
                        type: 'after',
                        points: alignment.points.filter((p) => p.m >= overlappingEndPoint.m),
                    },
                ].filter(({ points }) => points.length >= 2);

                const features = pointCollections.map(({ type, points }) => {
                    const polyline = points.map(pointToCoords);
                    const lineString = new LineString(polyline);
                    const feature = new Feature({ geometry: lineString });

                    if (type === 'duplicate') {
                        if (linkedDuplicates.includes(alignment.header.id)) {
                            feature.setStyle(blueSplitSectionStyle);
                        } else {
                            feature.setStyle(redSplitSectionStyle);
                        }
                    } else {
                        feature.setStyle(notOverlappingDuplicateSplitSectionStyle);
                    }

                    return feature;
                });
                return features;
            } else {
                return [];
            }
        })
        .filter(filterNotEmpty);
}

type DuplicateSplitSectionData = {
    linkedDuplicates: LocationTrackId[];
    duplicates: SplitDuplicateTrack[];
    alignments: LocationTrackAlignmentDataHolder[];
};

function getValidDuplicateIds(splittingState: SplittingState): LocationTrackId[] {
    const allSplits = [splittingState.firstSplit, ...splittingState.splits];
    const validDuplicateIds = allSplits
        .map((split, index, allSplits) => {
            const startSplitPoint = split.splitPoint;
            const nextSplit = index + 1 < allSplits.length ? allSplits[index + 1] : undefined;
            const endSplitPoint =
                nextSplit !== undefined ? nextSplit.splitPoint : splittingState.endSplitPoint;
            if (
                split.duplicateTrackId &&
                split.duplicateStatus?.startSplitPoint !== undefined &&
                splitPointsAreSame(startSplitPoint, split.duplicateStatus?.startSplitPoint) &&
                split.duplicateStatus?.endSplitPoint !== undefined &&
                splitPointsAreSame(endSplitPoint, split.duplicateStatus?.endSplitPoint)
            ) {
                return split.duplicateTrackId;
            }
            return undefined;
        })
        .filter(filterNotEmpty);
    return validDuplicateIds;
}

async function getDuplicateSplitSectionData(
    splittingState: SplittingState | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    resolution: number,
): Promise<DuplicateSplitSectionData> {
    if (resolution <= HIGHLIGHTS_SHOW && splittingState && !splittingState.disabled) {
        const linkedDuplicates = getValidDuplicateIds(splittingState);
        const alignments = await getLocationTrackMapAlignmentsByTiles(
            changeTimes,
            mapTiles,
            layoutContext,
        );
        return { linkedDuplicates, duplicates: splittingState.duplicateTracks, alignments };
    } else {
        return { linkedDuplicates: [], duplicates: [], alignments: [] };
    }
}

const layerName: MapLayerName = 'duplicate-split-section-highlight-layer';

export function createDuplicateSplitSectionHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
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
