import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import Feature from 'ol/Feature';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { filterNotEmpty, filterUnique, first, init, last, lastIndex } from 'utils/array-utils';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';
import {
    DraftChangeType,
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    Operation,
    PublicationStage,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import {
    LocationTrackAlignmentDataHolder,
    ReferenceLineAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import {
    createAlignmentFeature,
    LOCATION_TRACK_ALIGNMENT_WIDTH,
    REFERENCE_LINE_ALIGNMENT_WIDTH,
} from 'map/layers/utils/alignment-layer-utils';
import mapStyles from 'map/map.module.scss';
import { mergeRanges, Point, rangesIntersectInclusive } from 'model/geometry';
import { Range } from 'common/common-model';

export const TRACK_NUMBER_CANDIDATE_DATA_PROPERTY = 'track-number-candidate-data';
export const REFERENCE_LINE_CANDIDATE_DATA_PROPERTY = 'reference-line-candidate-data';
export const LOCATION_TRACK_CANDIDATE_DATA_PROPERTY = 'location-track-candidate-data';
export const SWITCH_CANDIDATE_DATA_PROPERTY = 'switch-candidate-data';
export const KM_POST_CANDIDATE_DATA_PROPERTY = 'km-post-candidate-data';

export enum ChangeExplicitness {
    EXPLICIT = 'EXPLICIT',
    IMPLICIT = 'IMPLICIT',
}

export type PublicationCandidateFeatureType = LineString | OlPoint;

export type LocationTrackCandidateAndAlignment = {
    publishCandidate: LocationTrackPublicationCandidate;
    alignment: LocationTrackAlignmentDataHolder;
};
export type ReferenceLineCandidateAndAlignment = {
    publishCandidate: ReferenceLinePublicationCandidate;
    alignment: ReferenceLineAlignmentDataHolder;
};
export type TrackNumberCandidateAndAlignment = {
    publishCandidate: TrackNumberPublicationCandidate;
    alignment: ReferenceLineAlignmentDataHolder;
};

export type LineStringFeatureChangeType =
    | DraftChangeType.REFERENCE_LINE
    | DraftChangeType.LOCATION_TRACK
    | DraftChangeType.TRACK_NUMBER;
export type LineStringFeatureCandidate =
    | ReferenceLinePublicationCandidate
    | LocationTrackPublicationCandidate
    | TrackNumberPublicationCandidate;

export type PointFeatureChangeType = DraftChangeType.SWITCH | DraftChangeType.KM_POST;
export type PointFeatureCandidate = SwitchPublicationCandidate | KmPostPublicationCandidate;

export type PointRange = {
    indexRange: Range<number>;
    changeType: ChangeExplicitness;
};

const UNSTAGED_ALIGNMENT_HIGHLIGHT_WIDTH = 23;
const STAGED_ALIGNMENT_HIGHLIGHT_WIDTH = 11;

const UNSTAGED_POINT_FEATURE_RADIUS = 25;
const STAGED_POINT_FEATURE_RADIUS = 18;

// The range for highlight z-indexes is 0-15, deleted alignment lines should go above them.
// Reference lines go under location tracks.
const DELETED_LOCATION_TRACK_Z_INDEX = 17;
const DELETED_REFERENCE_LINE_Z_INDEX = 16;

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

const isLineStringType = (type: DraftChangeType): boolean =>
    type === DraftChangeType.REFERENCE_LINE ||
    type === DraftChangeType.LOCATION_TRACK ||
    type === DraftChangeType.TRACK_NUMBER;

const findSubRange = (
    points: AlignmentPoint[],
    mRange: Range<number>,
): Range<number> | undefined => {
    const min = points.findIndex((p) => p.m >= mRange.min);
    const max = points.findLastIndex((p) => p.m <= mRange.max);
    if (min === -1 || max === -1) {
        // m-value range is not in "range"
        return undefined;
    } else if (min > max) {
        // m-value range is between two points, select both points
        return {
            min: max,
            max: min,
        };
    } else if (min === max) {
        // m-value range contains a single point, expand range
        if (min === 0) {
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
};

const mergeOverlappingRanges = (pointIndexRanges: Range<number>[]): Range<number>[] =>
    pointIndexRanges.reduce((mergedRanges: Range<number>[], range) => {
        const prevRange = last(mergedRanges);
        return prevRange !== undefined && rangesIntersectInclusive(prevRange, range)
            ? [...init(mergedRanges), mergeRanges(prevRange, range)]
            : [...mergedRanges, range];
    }, []);

const createPointRange = (
    startIndex: number,
    endIndex: number,
    explicitness: ChangeExplicitness,
): PointRange => ({
    indexRange: {
        min: startIndex,
        max: endIndex,
    },
    changeType: explicitness,
});

const equalsRange = (min: number, max: number, range: Range<number>): boolean =>
    min === range.min && max === range.max;

/**
 * Divides points into groups by given m-value ranges and spaces between ranges.
 * Each group created by m-value range is marked as an explicit change, groups between
 * m-value ranges are marked as implicit changes.
 *
 * Each returned group (or index range) contains at least two points and ranges are inclusive,
 * e.g. 1..3, 3..10, 10..12 etc.
 */
const splitByRanges = (points: AlignmentPoint[], mRanges: Range<number>[]): PointRange[] => {
    const explicitChangeIndexRanges: Range<number>[] = mRanges
        .sort((range1, range2) => range1.min - range2.min)
        .map((mRange) => findSubRange(points, mRange))
        .filter(filterNotEmpty);

    const mergedExplicitIndexRanges = mergeOverlappingRanges(explicitChangeIndexRanges);

    const rangeEndpoints = [
        0,
        ...mergedExplicitIndexRanges.map((range) => [range.min, range.max]).flat(),
        lastIndex(points),
    ].filter(filterUnique);
    return init(rangeEndpoints).map((currentMin, index) => {
        const currentMax = expectDefined(rangeEndpoints[index + 1]);

        return mergedExplicitIndexRanges.some((range) => equalsRange(currentMin, currentMax, range))
            ? createPointRange(currentMin, currentMax, ChangeExplicitness.EXPLICIT)
            : createPointRange(currentMin, currentMax, ChangeExplicitness.IMPLICIT);
    });
};

const getHighlightColor = (
    stage: PublicationStage,
    explicitness: ChangeExplicitness,
    operation: Operation,
): string => {
    if (stage === PublicationStage.STAGED) {
        return explicitness === ChangeExplicitness.EXPLICIT
            ? mapStyles.previewHighlightStagedExplicit
            : mapStyles.previewHighlightStagedImplicit;
    } else if (operation === 'DELETE') {
        return explicitness === ChangeExplicitness.EXPLICIT
            ? mapStyles.previewHighlightDeletedExplicit
            : mapStyles.previewHighlightDeletedImplicit;
    } else {
        return explicitness === ChangeExplicitness.EXPLICIT
            ? mapStyles.previewHighlightModifiedExplicit
            : mapStyles.previewHighlightModifiedImplicit;
    }
};

const getHighlightZIndex = (
    operation: Operation,
    assetType: DraftChangeType,
    stage: PublicationStage,
    explicitness: ChangeExplicitness,
): number => {
    let zIndex = 0;

    // Staged changes go above unstaged changes
    if (stage === PublicationStage.STAGED) zIndex += 8;
    // Explicit changes go above implicit changes
    if (explicitness === ChangeExplicitness.EXPLICIT) zIndex += 4;
    // LineStrings go above points
    if (isLineStringType(assetType)) zIndex += 2;
    // Deletions go below other operations
    if (operation !== 'DELETE') zIndex += 1;

    return zIndex;
};

export const createAlignmentLineStringFeature = (
    pointRange: PointRange | undefined,
    alignmentPoints: AlignmentPoint[],
    candidate: LineStringFeatureCandidate,
    metersPerPixel: number,
    type: LineStringFeatureChangeType,
): Feature<LineString> => {
    const changeType = pointRange?.changeType ?? ChangeExplicitness.IMPLICIT;

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
};

export const createLocationTrackCandidateFeatures = (
    candidates: LocationTrackCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] =>
    candidates.flatMap((candidate) =>
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

export const createReferenceLineCandidateFeatures = (
    candidates: ReferenceLineCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] =>
    candidates.flatMap((candidate) =>
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

export const createTrackNumberCandidateFeatures = (
    candidates: TrackNumberCandidateAndAlignment[],
    metersPerPixel: number,
): Feature<LineString>[] =>
    candidates.flatMap((candidate) =>
        createAlignmentLineStringFeature(
            undefined,
            candidate.alignment.points,
            candidate.publishCandidate,
            metersPerPixel,
            DraftChangeType.TRACK_NUMBER,
        ),
    );

export const createOfficialLocationTrackFeatures = (
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
                        ? ChangeExplicitness.EXPLICIT
                        : ChangeExplicitness.IMPLICIT,
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
                publishCandidate.operation === 'DELETE'
                    ? ChangeExplicitness.EXPLICIT
                    : ChangeExplicitness.IMPLICIT,
            ),
        }),
    );

    highlightFeatures.map((f) => f.set(LOCATION_TRACK_CANDIDATE_DATA_PROPERTY, publishCandidate));
    return [...lineFeatures, ...highlightFeatures];
};

export const createOfficialReferenceLineFeatures = (
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
                        ? ChangeExplicitness.EXPLICIT
                        : ChangeExplicitness.IMPLICIT,
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
                publishCandidate.operation === 'DELETE'
                    ? ChangeExplicitness.EXPLICIT
                    : ChangeExplicitness.IMPLICIT,
            ),
        }),
    );

    highlightFeatures.forEach((f) => {
        f.set(REFERENCE_LINE_CANDIDATE_DATA_PROPERTY, publishCandidate);
        if (trackNumberCandidate) f.set(TRACK_NUMBER_CANDIDATE_DATA_PROPERTY, trackNumberCandidate);
    });
    return [...lineFeatures, ...highlightFeatures];
};

export const createPointCandidateFeature = (
    candidate: PointFeatureCandidate,
    location: Point,
    type: DraftChangeType,
    explicitness: ChangeExplicitness,
): Feature<OlPoint> | undefined => {
    const color = getHighlightColor(
        candidate.stage,
        ChangeExplicitness.EXPLICIT,
        candidate.operation,
    );
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
};

export const createPointCandidateFeatures = (
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
                      ChangeExplicitness.EXPLICIT,
                  )
                : undefined,
        )
        .filter(filterNotEmpty);
