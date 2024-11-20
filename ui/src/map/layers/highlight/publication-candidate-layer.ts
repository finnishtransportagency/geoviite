import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LayoutContext, Range } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty, first, last, lastIndex } from 'utils/array-utils';
import Feature from 'ol/Feature';
import {
    createLayer,
    findMatchingEntities,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import {
    DraftChangeType,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    PublicationStage,
    SwitchPublicationCandidate,
} from 'publication/publication-model';
import { rangesIntersectInclusive, Rectangle } from 'model/geometry';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { expectDefined } from 'utils/type-utils';

export const LOCATION_TRACK_CANDIDATE_DATA_PROPERTY = 'location-track-candidate-data';
export const SWITCH_CANDIDATE_DATA_PROPERTY = 'switch-candidate-data';

enum ChangeType {
    EXPLICIT,
    IMPLICIT,
}

type PublicationCandidateFeatureTypes = LineString | OlPoint;

type LocationTrackCandidateWithAlignment = {
    publishCandidate: LocationTrackPublicationCandidate;
    alignment: LocationTrackAlignmentDataHolder;
};

function colorByStage(stage: PublicationStage, changeType: ChangeType): string {
    return stage === PublicationStage.STAGED
        ? changeType === ChangeType.EXPLICIT
            ? '#0066cc'
            : '#99c2ea'
        : changeType === ChangeType.EXPLICIT
          ? '#ffc300'
          : '#cdc8b9';
}

const zIndexOrder = [
    {
        stage: PublicationStage.UNSTAGED,
        changeType: ChangeType.IMPLICIT,
    },
    {
        stage: PublicationStage.STAGED,
        changeType: ChangeType.IMPLICIT,
    },
    {
        stage: PublicationStage.UNSTAGED,
        changeType: ChangeType.EXPLICIT,
    },
    {
        stage: PublicationStage.STAGED,
        changeType: ChangeType.EXPLICIT,
    },
];

function getZIndexByStage(stage: PublicationStage, changeType: ChangeType): number {
    return zIndexOrder.findIndex(
        (sample) => sample.stage == stage && sample.changeType == changeType,
    );
}

type PointRange = {
    indexRange: Range<number>;
    changeType: ChangeType;
};

/**
 * Divides points into groups by given m-value ranges and spaces between ranges.
 * Each group created by m-value range is marked as an explicit change, groups between
 * m-value ranges are marked as implicit changes.
 *
 * Each returned group (or index range) contains at least two points and ranges are inclusive,
 * e.g. 1..3, 3..10, 10..12 etc.
 */
function splitByRanges(points: AlignmentPoint[], mRanges: Range<number>[]): PointRange[] {
    const pointIndexRanges: Range<number>[] = mRanges
        .sort((range1, range2) => range1.min - range2.min)
        .map((mRange) => {
            const min = points.findIndex((p) => p.m >= mRange.min);
            const max = points.findLastIndex((p) => p.m <= mRange.max);
            if (min == -1 || max == -1) {
                // m-value range is not in "range"
                return undefined;
            } else if (min > max) {
                // m-value range is between two points, select both points
                return {
                    min: max,
                    max: min,
                };
            } else if (min == max) {
                // m-value range contains a single point, expand range
                if (min == 0) {
                    return {
                        min: 0,
                        max: 1,
                    };
                } else {
                    return {
                        min: min - 1,
                        max: max,
                    };
                }
            } else {
                return {
                    min: min,
                    max: max,
                };
            }
        })
        .filter(filterNotEmpty);

    const pointIndexRangesWithoutOverlapping = pointIndexRanges.reduce(
        (combinedRanges: Range<number>[], range) => {
            const prevRange = last(combinedRanges);
            if (prevRange !== undefined && rangesIntersectInclusive(prevRange, range)) {
                return [
                    ...combinedRanges.slice(0, -1),
                    {
                        min: Math.min(prevRange.min, range.min),
                        max: Math.max(prevRange.max, range.max),
                    },
                ];
            } else {
                return [...combinedRanges, range];
            }
        },
        [],
    );

    if (pointIndexRangesWithoutOverlapping.length == 0) {
        return [
            {
                indexRange: {
                    min: 0,
                    max: lastIndex(points),
                },
                changeType: ChangeType.IMPLICIT,
            },
        ];
    } else {
        return pointIndexRangesWithoutOverlapping.reduce(
            (pointRanges: PointRange[], indexRange, index, indexRanges) => {
                const prevPointRange = last(pointRanges);
                const prevIndexMax = prevPointRange?.indexRange.max || 0;

                const rangeBefore =
                    indexRange.min > 0
                        ? {
                              indexRange: {
                                  min: prevIndexMax,
                                  max: indexRange.min,
                              },
                              changeType: ChangeType.IMPLICIT,
                          }
                        : undefined;

                const changedRange = {
                    indexRange: indexRange,
                    changeType: ChangeType.EXPLICIT,
                };

                const rangeAfter =
                    index == lastIndex(indexRanges) && indexRange.max < lastIndex(points)
                        ? {
                              indexRange: {
                                  min: indexRange.max,
                                  max: lastIndex(points),
                              },
                              changeType: ChangeType.IMPLICIT,
                          }
                        : undefined;

                return [
                    ...pointRanges,
                    ...[rangeBefore, changedRange, rangeAfter].filter(filterNotEmpty),
                ];
            },
            [],
        );
    }
}

function createLocationTrackCandidateFeatures(
    candidates: LocationTrackCandidateWithAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] {
    return candidates.flatMap((candidate) => {
        const pointRanges = splitByRanges(
            candidate.alignment.points,
            candidate.publishCandidate.geometryChanges,
        );
        return pointRanges.map((pointRange) => {
            const start = pointRange.indexRange.min;
            const end = pointRange.indexRange.max;
            const points = candidate.alignment.points.slice(
                start,
                end + 1, // +1 for inclusive slicing
            );
            const rangeLengthInMeters =
                expectDefined(last(points)).m - expectDefined(first(points)).m;
            const rangeLengthInPixels = rangeLengthInMeters / metersPerPixel;
            const style = new Style({
                stroke: new Stroke({
                    color: colorByStage(candidate.publishCandidate.stage, pointRange.changeType),
                    width: 15,
                    lineCap: rangeLengthInPixels < 15 ? 'square' : 'butt',
                }),
                zIndex: getZIndexByStage(candidate.publishCandidate.stage, pointRange.changeType),
            });
            const feature = new Feature({
                geometry: new LineString(points.map(pointToCoords)),
            });
            feature.setStyle(style);
            //if (pointRange.isChanged) {
            feature.set(LOCATION_TRACK_CANDIDATE_DATA_PROPERTY, candidate.publishCandidate);
            //}
            return feature;
        });
    });
}

function createSwitchCandidateFeatures(
    switchCandidates: SwitchPublicationCandidate[],
): Feature<OlPoint>[] {
    const features = switchCandidates
        .map((candidate) => {
            if (!candidate.location) {
                return undefined;
            }

            const color = colorByStage(candidate.stage, ChangeType.EXPLICIT);
            const style = new Style({
                image: new Circle({
                    radius: candidate.stage == PublicationStage.STAGED ? 18 : 22,
                    stroke: new Stroke({ color: color }),
                    fill: new Fill({ color: color }),
                }),
                zIndex: getZIndexByStage(candidate.stage, ChangeType.EXPLICIT),
            });

            const feature = new Feature({
                geometry: new OlPoint(pointToCoords(candidate.location)),
            });
            feature.setStyle(style);
            feature.set(SWITCH_CANDIDATE_DATA_PROPERTY, candidate);
            return feature;
        })
        .filter(filterNotEmpty);
    return features;
}

const layerName: MapLayerName = 'publication-candidate-layer';

export function createPublicationCandidateLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<PublicationCandidateFeatureTypes>> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    metersPerPixel: number,
    onLoadingData: (loading: boolean) => void,
    publicationCandidates: PublicationCandidate[],
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const locationTrackCandidates = publicationCandidates.filter(
        (c) => c.type == DraftChangeType.LOCATION_TRACK,
    );
    const locationTrackIds = locationTrackCandidates
        .map((c) => (c.type == DraftChangeType.LOCATION_TRACK ? c.id : undefined))
        .filter(filterNotEmpty);

    const locationTrackAlignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((locationTrackAlignments) => {
        return locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateWithAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty);
    });

    const switchCandidates = publicationCandidates
        .map((c) =>
            c.type == DraftChangeType.SWITCH ? (c as SwitchPublicationCandidate) : undefined,
        )
        .filter(filterNotEmpty);

    const createFeatures = (data: {
        locationTrackCandidates: LocationTrackCandidateWithAlignment[];
    }) => {
        // // const showAll = Object.values(layerSettings).every((s) => !s.selected);
        const filteredAlignments = data.locationTrackCandidates.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
            // const trackNumberId = a.trackNumber?.id;
            // return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
        });
        //
        // const alignmentsWithColor = filteredAlignments.filter((a) => {
        //     return true;
        // });
        const locationTrackAlignmentFeatures = createLocationTrackCandidateFeatures(
            filteredAlignments,
            metersPerPixel,
        );
        const switchFeatures = createSwitchCandidateFeatures(switchCandidates);

        return [...locationTrackAlignmentFeatures, ...switchFeatures];
    };

    const allData = Promise.all([locationTrackAlignmentPromise]).then((result) => ({
        locationTrackCandidates: result[0],
    }));

    loadLayerData(source, isLatest, onLoadingData, allData, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const locationTrackPublicationCandidates =
                findMatchingEntities<LocationTrackPublicationCandidate>(
                    hitArea,
                    source,
                    LOCATION_TRACK_CANDIDATE_DATA_PROPERTY,
                    options,
                );

            const switchPublicationCandidates = findMatchingEntities<SwitchPublicationCandidate>(
                hitArea,
                source,
                SWITCH_CANDIDATE_DATA_PROPERTY,
                options,
            );

            return {
                locationTrackPublicationCandidates: locationTrackPublicationCandidates,
                switchPublicationCandidates: switchPublicationCandidates,
            };
        },
    };
}
