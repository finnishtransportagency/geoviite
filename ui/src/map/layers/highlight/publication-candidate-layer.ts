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
import {
    draftMainLayoutContext,
    LayoutContext,
    officialLayoutContext,
    Range,
} from 'common/common-model';
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
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    Operation,
    PublicationCandidate,
    PublicationStage,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import { Point, rangesIntersectInclusive, Rectangle } from 'model/geometry';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import {
    createAlignmentFeature,
    LOCATION_TRACK_ALIGNMENT_WIDTH,
    REFERENCE_LINE_ALIGNMENT_WIDTH,
} from 'map/layers/utils/alignment-layer-utils';
import mapStyles from 'map/map.module.scss';

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

type LineStringFeatureChangeType =
    | DraftChangeType.REFERENCE_LINE
    | DraftChangeType.LOCATION_TRACK
    | DraftChangeType.TRACK_NUMBER;
type LineStringFeatureCandidate =
    | ReferenceLinePublicationCandidate
    | LocationTrackPublicationCandidate
    | TrackNumberPublicationCandidate;

type PointFeatureChangeType = DraftChangeType.SWITCH | DraftChangeType.KM_POST;
type PointFeatureCandidate = SwitchPublicationCandidate | KmPostPublicationCandidate;

type PointRange = {
    indexRange: Range<number>;
    changeType: ChangeType;
};

const UNSTAGED_ALIGNMENT_HIGHLIGHT_WIDTH = 23;
const STAGED_ALIGNMENT_HIGHLIGHT_WIDTH = 11;

const UNSTAGED_POINT_FEATURE_RADIUS = 25;
const STAGED_POINT_FEATURE_RADIUS = 18;

// The range for highlight z-indexes is 0-15, deleted alignment lines should go above them.
// Reference lines go under location tracks.
const DELETED_LOCATION_TRACK_Z_INDEX = 17;
const DELETED_REFERENCE_LINE_Z_INDEX = 16;

const getHighlightColor = (
    stage: PublicationStage,
    changeType: ChangeType,
    operation: Operation,
): string => {
    if (stage === PublicationStage.STAGED) {
        return changeType === ChangeType.EXPLICIT
            ? mapStyles.previewHighlightStagedExplicit
            : mapStyles.previewHighlightStagedImplicit;
    } else if (operation === 'DELETE') {
        return changeType === ChangeType.EXPLICIT
            ? mapStyles.previewHighlightDeletedExplicit
            : mapStyles.previewHighlightDeletedImplicit;
    } else {
        return changeType === ChangeType.EXPLICIT
            ? mapStyles.previewHighlightModifiedExplicit
            : mapStyles.previewHighlightModifiedImplicit;
    }
};

const getHighlightZIndex = (
    operation: Operation,
    assetType: DraftChangeType,
    stage: PublicationStage,
    type: ChangeType,
): number => {
    // Deletions go below everything else
    const operationPriority = operation === 'DELETE' ? 0 : 8;
    // Point features go below lines
    const typePriority =
        assetType === DraftChangeType.KM_POST || assetType === DraftChangeType.SWITCH ? 0 : 4;
    // Unstaged changes go below staged changes
    const stagePriority = stage === PublicationStage.UNSTAGED ? 0 : 2;
    // Explicit changes go above implicit changes
    const explicitPriority = type === ChangeType.IMPLICIT ? 0 : 1;

    return operationPriority + typePriority + stagePriority + explicitPriority;
};

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
    candidate: LineStringFeatureCandidate,
    metersPerPixel: number,
    type: LineStringFeatureChangeType,
): Feature<LineString> {
    const changeType = pointRange?.changeType ?? ChangeType.IMPLICIT;

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
            color: getHighlightColor(candidate.stage, changeType, candidate.operation),
            width:
                candidate.stage === PublicationStage.UNSTAGED
                    ? UNSTAGED_ALIGNMENT_HIGHLIGHT_WIDTH
                    : STAGED_ALIGNMENT_HIGHLIGHT_WIDTH,
            lineCap: rangeLengthInPixels < 15 ? 'square' : 'butt',
        }),
        zIndex: getHighlightZIndex(
            candidate.operation,
            candidate.type,
            candidate.stage,
            changeType,
        ),
    });

    const feature = new Feature({
        geometry: new LineString(points.map(pointToCoords)),
    });
    feature.setStyle(style);
    feature.set(dataPropertyByType(type), candidate);

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

const createOfficialLocationTrackFeatures = (
    publishCandidate: LocationTrackPublicationCandidate,
    alignment: LocationTrackAlignmentDataHolder,
    showEndPointTicks: boolean,
): Feature<LineString | OlPoint>[] => {
    const lineFeatures = createAlignmentFeature(
        alignment,
        showEndPointTicks,
        new Style({
            stroke: new Stroke({
                color: mapStyles.alignmentPreviewOfficialLine,
                width: LOCATION_TRACK_ALIGNMENT_WIDTH,
            }),
            zIndex: DELETED_LOCATION_TRACK_Z_INDEX,
        }),
    );
    const highlightFeatures = createAlignmentFeature(
        alignment,
        false,
        new Style({
            stroke: new Stroke({
                color: getHighlightColor(
                    publishCandidate.stage,
                    publishCandidate.operation === 'DELETE'
                        ? ChangeType.EXPLICIT
                        : ChangeType.IMPLICIT,
                    'DELETE',
                ),
                width:
                    publishCandidate.stage === PublicationStage.UNSTAGED
                        ? UNSTAGED_ALIGNMENT_HIGHLIGHT_WIDTH
                        : STAGED_ALIGNMENT_HIGHLIGHT_WIDTH,
                lineCap: 'butt',
            }),
            zIndex: getHighlightZIndex(
                'DELETE',
                publishCandidate.type,
                publishCandidate.stage,
                ChangeType.EXPLICIT,
            ),
        }),
    );

    highlightFeatures.map((f) => f.set(LOCATION_TRACK_CANDIDATE_DATA_PROPERTY, publishCandidate));
    return [...lineFeatures, ...highlightFeatures];
};

const createOfficialReferenceLineFeatures = (
    publishCandidate: ReferenceLinePublicationCandidate,
    alignment: ReferenceLineAlignmentDataHolder,
    trackNumberCandidate: TrackNumberPublicationCandidate | undefined,
    showEndPointTicks: boolean,
): Feature<LineString | OlPoint>[] => {
    const lineFeatures = createAlignmentFeature(
        alignment,
        showEndPointTicks,
        new Style({
            stroke: new Stroke({
                color: mapStyles.alignmentPreviewOfficialLine,
                width: REFERENCE_LINE_ALIGNMENT_WIDTH,
            }),
            zIndex: DELETED_REFERENCE_LINE_Z_INDEX,
        }),
    );
    const highlightFeatures = createAlignmentFeature(
        alignment,
        false,
        new Style({
            stroke: new Stroke({
                color: getHighlightColor(
                    publishCandidate.stage,
                    publishCandidate.operation === 'DELETE'
                        ? ChangeType.EXPLICIT
                        : ChangeType.IMPLICIT,
                    'DELETE',
                ),
                width:
                    publishCandidate.stage === PublicationStage.UNSTAGED
                        ? UNSTAGED_ALIGNMENT_HIGHLIGHT_WIDTH
                        : STAGED_ALIGNMENT_HIGHLIGHT_WIDTH,
                lineCap: 'butt',
            }),
            zIndex: getHighlightZIndex(
                'DELETE',
                publishCandidate.type,
                publishCandidate.stage,
                ChangeType.EXPLICIT,
            ),
        }),
    );

    highlightFeatures.forEach((f) => {
        f.set(REFERENCE_LINE_CANDIDATE_DATA_PROPERTY, publishCandidate);
        if (trackNumberCandidate) f.set(TRACK_NUMBER_CANDIDATE_DATA_PROPERTY, trackNumberCandidate);
    });
    return [...lineFeatures, ...highlightFeatures];
};

export function createPointCandidateFeature(
    candidate: PointFeatureCandidate,
    location: Point,
    type: DraftChangeType,
    explicitness: ChangeType,
): Feature<OlPoint> | undefined {
    const color = getHighlightColor(candidate.stage, ChangeType.EXPLICIT, candidate.operation);
    const style = new Style({
        image: new Circle({
            radius:
                candidate.stage == PublicationStage.STAGED
                    ? STAGED_POINT_FEATURE_RADIUS
                    : UNSTAGED_POINT_FEATURE_RADIUS,
            stroke: new Stroke({ color: color }),
            fill: new Fill({ color: color }),
        }),
        zIndex: getHighlightZIndex(candidate.operation, type, candidate.stage, explicitness),
    });

    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(location)),
    });
    feature.setStyle(style);
    feature.set(dataPropertyByType(type), candidate);

    return feature;
}

const createPointCandidateFeatures = (
    candidates: PointFeatureCandidate[],
    type: PointFeatureChangeType,
): Feature<OlPoint>[] =>
    candidates
        .map((candidate) =>
            candidate.location
                ? createPointCandidateFeature(
                      candidate,
                      candidate.location,
                      type,
                      ChangeType.EXPLICIT,
                  )
                : undefined,
        )
        .filter(filterNotEmpty);

const layerName: MapLayerName = 'publication-candidate-layer';

export function createPublicationCandidateLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<PublicationCandidateFeatureTypes>> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    metersPerPixel: number,
    onLoadingData: (loading: boolean) => void,
    publicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const targetLayoutContext =
        designPublicationMode === 'PUBLISH_CHANGES'
            ? officialLayoutContext(layoutContext)
            : draftMainLayoutContext();

    const locationTrackCandidates = publicationCandidates.filter(
        (c) => c.type == DraftChangeType.LOCATION_TRACK,
    );
    const locationTrackIds = locationTrackCandidates.map((c) => c.id);

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
                    ? {
                          alignment: alignment,
                          publishCandidate: candidate,
                      }
                    : undefined;
            })
            .filter(filterNotEmpty);
        const tnCandidates = rlAlignments
            .map((alignment) => {
                const candidate = trackNumberCandidates.find(
                    (c) => c.id == alignment.header.trackNumberId,
                );
                return candidate
                    ? {
                          alignment: alignment,
                          publishCandidate: candidate,
                      }
                    : undefined;
            })
            .filter(filterNotEmpty);

        return { trackNumberCandidates: tnCandidates, referenceLineCandidates: rlCandidates };
    });

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

    const officialLocationTrackAlignmentsPromise =
        locationTrackCandidates.length > 0
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, targetLayoutContext).then(
                  (alignments) =>
                      alignments
                          .map((alignment) => {
                              const publishCandidate = locationTrackCandidates.find(
                                  (c) => c.id === alignment.header.id,
                              );

                              return publishCandidate
                                  ? {
                                        alignment,
                                        publishCandidate,
                                    }
                                  : undefined;
                          })
                          .filter(filterNotEmpty),
              )
            : Promise.resolve([]);

    const officialReferenceLineLineAlignmentsPromise =
        referenceLineCandidates.length > 0
            ? getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, targetLayoutContext).then(
                  (alignments) =>
                      alignments
                          .map((alignment) => {
                              const publishCandidate = referenceLineCandidates.find(
                                  (c) => c.id === alignment.header.id,
                              );
                              return publishCandidate
                                  ? {
                                        alignment,
                                        publishCandidate,
                                    }
                                  : undefined;
                          })
                          .filter(filterNotEmpty),
              )
            : Promise.resolve([]);
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
        officialLocationTracks: LocationTrackCandidateAndAlignment[];
        officialReferenceLines: ReferenceLineCandidateAndAlignment[];
    }) => {
        const filteredLocationTracks = data.locationTrackCandidates.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
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

        const showEndPointTicks = metersPerPixel <= Limits.SHOW_LOCATION_TRACK_BADGES;

        const officialLocationTrackFeatures = data.officialLocationTracks
            .flatMap(({ alignment, publishCandidate }) =>
                createOfficialLocationTrackFeatures(publishCandidate, alignment, showEndPointTicks),
            )
            .flat()
            .filter(filterNotEmpty);
        const officialReferenceLineFeatures: Feature<LineString | OlPoint>[] =
            data.officialReferenceLines
                .flatMap(({ alignment, publishCandidate }) => {
                    const tnCandidate = trackNumberCandidates.find(
                        (tn) => tn.id === publishCandidate.trackNumberId,
                    );

                    return createOfficialReferenceLineFeatures(
                        publishCandidate,
                        alignment,
                        tnCandidate,
                        showEndPointTicks,
                    );
                })
                .flat()
                .filter(filterNotEmpty);

        return [
            ...locationTrackAlignmentFeatures,
            ...referenceLineAlignmentFeatures,
            ...trackNumberAlignmentFeatures,
            ...switchFeatures,
            ...kmPostFeatures,
            ...officialLocationTrackFeatures,
            ...officialReferenceLineFeatures,
        ];
    };

    const allData = Promise.all([
        locationTrackAlignmentPromise,
        referenceLineAlignmentPromise,
        officialLocationTrackAlignmentsPromise,
        officialReferenceLineLineAlignmentsPromise,
    ]).then(
        ([
            locationTrackCandidates,
            trackNumberAndReferenceLineCandidates,
            officialLocationTracks,
            officialReferenceLines,
        ]) => ({
            locationTrackCandidates,
            ...trackNumberAndReferenceLineCandidates,
            officialLocationTracks,
            officialReferenceLines,
        }),
    );

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
