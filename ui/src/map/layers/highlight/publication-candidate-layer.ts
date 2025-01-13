import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    getReferenceLineMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
    ReferenceLineAlignmentDataHolder,
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
    BasePublicationCandidate,
    DraftChangeType,
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    PublicationStage,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
    WithLocation,
} from 'publication/publication-model';
import { Point, rangesIntersectInclusive, Rectangle } from 'model/geometry';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';

export const TRACK_NUMBER_CANDIDATE_DATA_PROPERTY = 'track-number-candidate-data';
export const REFERENCE_LINE_CANDIDATE_DATA_PROPERTY = 'reference-line-candidate-data';
export const LOCATION_TRACK_CANDIDATE_DATA_PROPERTY = 'location-track-candidate-data';
export const SWITCH_CANDIDATE_DATA_PROPERTY = 'switch-candidate-data';
export const KM_POST_CANDIDATE_DATA_PROPERTY = 'km-post-candidate-data';

export enum ChangeType {
    EXPLICIT,
    IMPLICIT,
}

type PublicationCandidateFeatureTypes = LineString | OlPoint;

type LocationTrackCandidateAndAlignment = {
    publishCandidate: LocationTrackPublicationCandidate;
    alignment: LocationTrackAlignmentDataHolder;
};
type ReferenceLineCandidateAndAlignment = {
    publishCandidate: ReferenceLinePublicationCandidate;
    alignment: ReferenceLineAlignmentDataHolder;
};
type TrackNumberCandidateAndAlignment = {
    publishCandidate: TrackNumberPublicationCandidate;
    alignment: ReferenceLineAlignmentDataHolder;
};

export function colorByStage(
    stage: PublicationStage,
    changeType: ChangeType,
    isDeletion: boolean,
): string {
    return stage === PublicationStage.STAGED
        ? changeType === ChangeType.EXPLICIT && !isDeletion
            ? '#0066cc'
            : '#99c2ea'
        : isDeletion
          ? changeType === ChangeType.EXPLICIT
              ? '#ff6903'
              : '#f0b6b3'
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

function createAlignmentLineStringFeature(
    pointRange: PointRange | undefined,
    alignmentPoints: AlignmentPoint[],
    candidate: PublicationCandidate,
    metersPerPixel: number,
    type:
        | DraftChangeType.REFERENCE_LINE
        | DraftChangeType.LOCATION_TRACK
        | DraftChangeType.TRACK_NUMBER,
): Feature<LineString> {
    const start = pointRange?.indexRange.min || 0;
    const end = pointRange?.indexRange.max || lastIndex(alignmentPoints);
    const points = alignmentPoints.slice(
        start,
        end + 1, // +1 for inclusive slicing
    );
    const rangeLengthInMeters = expectDefined(last(points)).m - expectDefined(first(points)).m;
    const rangeLengthInPixels = rangeLengthInMeters / metersPerPixel;
    const style = new Style({
        stroke: new Stroke({
            color: colorByStage(
                candidate.stage,
                pointRange?.changeType ?? ChangeType.IMPLICIT,
                false,
            ),
            width: 15,
            lineCap: rangeLengthInPixels < 15 ? 'square' : 'butt',
        }),
        zIndex: getZIndexByStage(candidate.stage, pointRange?.changeType ?? ChangeType.IMPLICIT),
    });
    const feature = new Feature({
        geometry: new LineString(points.map(pointToCoords)),
    });
    feature.setStyle(style);
    //if (pointRange.isChanged) {
    feature.set(dataPropertyByType(type), candidate);
    //}
    return feature;
}

function createLocationTrackCandidateFeatures(
    candidates: LocationTrackCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] {
    return candidates.flatMap((candidate) =>
        splitByRanges(candidate.alignment.points, candidate.publishCandidate.geometryChanges).map(
            (pointRange) =>
                createAlignmentLineStringFeature(
                    pointRange,
                    candidate.alignment.points,
                    candidate.publishCandidate,
                    metersPerPixel,
                    DraftChangeType.LOCATION_TRACK,
                ),
        ),
    );
}

function createReferenceLineCandidateFeatures(
    candidates: ReferenceLineCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] {
    return candidates.flatMap((candidate) =>
        splitByRanges(candidate.alignment.points, candidate.publishCandidate.geometryChanges).map(
            (pointRange) =>
                createAlignmentLineStringFeature(
                    pointRange,
                    candidate.alignment.points,
                    candidate.publishCandidate,
                    metersPerPixel,
                    DraftChangeType.REFERENCE_LINE,
                ),
        ),
    );
}

function createTrackNumberCandidateFeatures(
    candidates: TrackNumberCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] {
    return candidates.flatMap((candidate) =>
        createAlignmentLineStringFeature(
            undefined,
            candidate.alignment.points,
            candidate.publishCandidate,
            metersPerPixel,
            DraftChangeType.TRACK_NUMBER,
        ),
    );
}

export function createPointCandidateFeature(
    candidate: BasePublicationCandidate & WithLocation,
    location: Point,
    type: DraftChangeType,
    isDeletion: boolean,
    zIndex: number,
): Feature<OlPoint> | undefined {
    const color = colorByStage(candidate.stage, ChangeType.EXPLICIT, isDeletion);
    const style = new Style({
        image: new Circle({
            radius: candidate.stage == PublicationStage.STAGED ? 18 : 22,
            stroke: new Stroke({ color: color }),
            fill: new Fill({ color: color }),
        }),
        zIndex,
    });

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    feature.setStyle(style);
    feature.set(dataPropertyByType(type), candidate);

    return feature;
}

const createPointCandidateFeatures = (
    switchCandidates: (BasePublicationCandidate & WithLocation)[],
    type: DraftChangeType,
): Feature<OlPoint>[] =>
    switchCandidates
        .map((candidate) =>
            candidate.location
                ? createPointCandidateFeature(
                      candidate,
                      candidate.location,
                      type,
                      false,
                      getZIndexByStage(candidate.stage, ChangeType.EXPLICIT),
                  )
                : undefined,
        )
        .filter(filterNotEmpty);

const dataPropertyByType = (type: DraftChangeType) => {
    switch (type) {
        case DraftChangeType.TRACK_NUMBER:
            return TRACK_NUMBER_CANDIDATE_DATA_PROPERTY;
        case DraftChangeType.REFERENCE_LINE:
            return REFERENCE_LINE_CANDIDATE_DATA_PROPERTY;
        case DraftChangeType.LOCATION_TRACK:
            return LOCATION_TRACK_CANDIDATE_DATA_PROPERTY;
        case DraftChangeType.SWITCH:
            return SWITCH_CANDIDATE_DATA_PROPERTY;
        case DraftChangeType.KM_POST:
            return KM_POST_CANDIDATE_DATA_PROPERTY;
        default:
            return exhaustiveMatchingGuard(type);
    }
};

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
    const locationTrackIds = locationTrackCandidates.map((c) => c.id);

    const locationTrackAlignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((locationTrackAlignments) =>
        locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty),
    );

    const trackNumberCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.TRACK_NUMBER,
    );
    const trackNumberIds = trackNumberCandidates.map((c) => c.id);

    const referenceLineCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.REFERENCE_LINE,
    );
    const referenceLineIds = referenceLineCandidates.map((c) => c.id);
    const referenceLineAlignmentPromise = getReferenceLineMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((rlAlignments) => {
        const rlCandidates = rlAlignments
            .map((alignment) => {
                const candidate = referenceLineCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as ReferenceLineCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty);
        const tnCandidates = rlAlignments
            .map((alignment) => {
                const candidate = trackNumberCandidates.find(
                    (c) => c.id == alignment.header.trackNumberId,
                );
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as TrackNumberCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty);

        return { trackNumberCandidates: tnCandidates, referenceLineCandidates: rlCandidates };
    });

    const switchCandidates = publicationCandidates
        .map((c) =>
            c.type == DraftChangeType.SWITCH ? (c as SwitchPublicationCandidate) : undefined,
        )
        .filter(filterNotEmpty);

    const kmPostCandidates = publicationCandidates
        .map((c) =>
            c.type === DraftChangeType.KM_POST ? (c as KmPostPublicationCandidate) : undefined,
        )
        .filter(filterNotEmpty);

    const createFeatures = (data: {
        locationTrackCandidates: LocationTrackCandidateAndAlignment[];
        referenceLineCandidates: ReferenceLineCandidateAndAlignment[];
        trackNumberCandidates: TrackNumberCandidateAndAlignment[];
    }) => {
        // // const showAll = Object.values(layerSettings).every((s) => !s.selected);
        const filteredLocationTracks = data.locationTrackCandidates.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
            // const trackNumberId = a.trackNumber?.id;
            // return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
        });
        const filteredReferenceLines = data.referenceLineCandidates.filter((c) => {
            return referenceLineIds.includes(c.alignment.header.id);
        });
        const filteredTrackNumbers = data.trackNumberCandidates.filter(
            (c) =>
                c.alignment.header.trackNumberId !== undefined &&
                trackNumberIds.includes(c.alignment.header.trackNumberId),
        );

        const locationTrackAlignmentFeatures = createLocationTrackCandidateFeatures(
            filteredLocationTracks,
            metersPerPixel,
        );
        const referenceLineAlignmentFeatures = createReferenceLineCandidateFeatures(
            filteredReferenceLines,
            metersPerPixel,
        );
        const trackNumberAlignmentFeatures = createTrackNumberCandidateFeatures(
            filteredTrackNumbers,
            metersPerPixel,
        );
        const switchFeatures = createPointCandidateFeatures(
            switchCandidates,
            DraftChangeType.SWITCH,
        );
        const kmPostFeatures = createPointCandidateFeatures(
            kmPostCandidates,
            DraftChangeType.KM_POST,
        );

        return [
            ...locationTrackAlignmentFeatures,
            ...referenceLineAlignmentFeatures,
            ...trackNumberAlignmentFeatures,
            ...switchFeatures,
            ...kmPostFeatures,
        ];
    };

    const allData = Promise.all([
        locationTrackAlignmentPromise,
        referenceLineAlignmentPromise,
    ]).then(([locationTrackCandidates, trackNumberAndReferenceLineCandidates]) => ({
        locationTrackCandidates,
        ...trackNumberAndReferenceLineCandidates,
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

            const referenceLinePublicationCandidates =
                findMatchingEntities<ReferenceLinePublicationCandidate>(
                    hitArea,
                    source,
                    REFERENCE_LINE_CANDIDATE_DATA_PROPERTY,
                    options,
                );

            const trackNumberPublicationCandidates =
                findMatchingEntities<TrackNumberPublicationCandidate>(
                    hitArea,
                    source,
                    TRACK_NUMBER_CANDIDATE_DATA_PROPERTY,
                    options,
                );

            const switchPublicationCandidates = findMatchingEntities<SwitchPublicationCandidate>(
                hitArea,
                source,
                SWITCH_CANDIDATE_DATA_PROPERTY,
                options,
            );

            const kmPostPublicationCandidates = findMatchingEntities<KmPostPublicationCandidate>(
                hitArea,
                source,
                KM_POST_CANDIDATE_DATA_PROPERTY,
                options,
            );

            console.log(locationTrackPublicationCandidates, referenceLinePublicationCandidates);

            return {
                locationTrackPublicationCandidates,
                referenceLinePublicationCandidates,
                trackNumberPublicationCandidates,
                switchPublicationCandidates,
                kmPostPublicationCandidates,
            };
        },
    };
}
