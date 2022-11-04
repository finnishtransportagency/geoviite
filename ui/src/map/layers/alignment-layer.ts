import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import OlView from 'ol/View';
import { LayoutAlignmentsLayer, MapTile, OptionalShownItems } from 'map/map-model';
import { Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import {
    LayoutSegmentId,
    LayoutTrackNumber,
    LocationTrackId,
    MapAlignment,
    MapAlignmentType,
    MapSegment,
    ReferenceLineId,
    simplifySegments,
    toLocationTrack,
    toReferenceLine,
} from 'track-layout/track-layout-model';
import { getAlignmentsByTiles, getTrackNumbers } from 'track-layout/track-layout-api';
import {
    addBbox,
    getMatchingSegmentDatas,
    MatchOptions,
    SegmentDataHolder,
} from 'map/layers/layer-utils';
import { OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import { createTrackNumberFeature, DisplayMode, TrackNumberPoint } from 'map/layers/track-number';
import * as Limits from 'map/layers/layer-visibility-limits';
import {
    getTrackNumberDrawDistance,
    MAP_RESOLUTION_MULTIPLIER,
} from 'map/layers/layer-visibility-limits';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { deduplicate, fieldComparator, filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { fromExtent } from 'ol/geom/Polygon';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { PublishType, TimeStamp } from 'common/common-model';
import { getMaxTimestamp } from 'utils/date-utils';

const locationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentColor,
        width: 1,
    }),
    zIndex: 3,
});

const highlightedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentHighlightColor,
        width: 1,
    }),
    zIndex: 4,
});

const selectedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentHighlightColor,
        width: 2,
    }),
    zIndex: 4,
});

const referenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentColor,
        width: 3,
    }),
    zIndex: 1,
});

const highlightedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentHighlightColor,
        width: 3,
    }),
    zIndex: 2,
});

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentHighlightColor,
        width: 4,
    }),
    zIndex: 2,
});

const alignmentBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBackground,
        width: 12,
    }),
    zIndex: 0,
});

function createFeatures(
    dataHolder: SegmentDataHolder,
    selected: boolean,
    highlighted: boolean,
    trackNumberDisplayMode: DisplayMode,
    drawDistance: number,
): Feature<LineString | Point>[] {
    const { trackNumber, alignment, segment } = dataHolder;
    const lineString = new LineString(segment.points.map((point) => [point.x, point.y]));
    const features: Feature<LineString | Point>[] = [];
    const segmentFeature: Feature<LineString> = new Feature<LineString>({
        geometry: lineString,
    });
    addBbox(segmentFeature);
    features.push(segmentFeature);

    segmentFeature.setStyle(() => [
        alignmentBackgroundStyle,
        alignment.alignmentType === 'REFERENCE_LINE'
            ? selected
                ? selectedReferenceLineStyle
                : highlighted
                ? highlightedReferenceLineStyle
                : referenceLineStyle
            : selected
            ? selectedLocationTrackStyle
            : highlighted
            ? highlightedLocationTrackStyle
            : locationTrackStyle,
    ]);

    const numbersBeforeSegment = Math.floor(segment.start / drawDistance);
    const numbersAfterSegment = Math.floor((segment.start + segment.length) / drawDistance);
    const segmentHasTrackNumberLabel =
        segment.start === 0.0 || numbersBeforeSegment !== numbersAfterSegment;
    if (
        trackNumber !== null &&
        trackNumberDisplayMode !== DisplayMode.NONE &&
        segmentHasTrackNumberLabel
    ) {
        const trackNumberPoints: TrackNumberPoint[] = [];
        let length = segment.start % drawDistance;
        const pointLength = segment.length / segment.points.length;
        segment.points.forEach((point, index) => {
            if (
                (length % drawDistance == 0.0 || length >= drawDistance) &&
                index < segment.points.length - 1
            ) {
                trackNumberPoints.push({
                    point: point,
                    nextPoint: segment.points[index + 1],
                });
            }
            if (length >= drawDistance) {
                length -= drawDistance;
            }
            length += pointLength;
        });

        const trackNumberFeatures: Feature<Point>[] = createTrackNumberFeature(
            alignment,
            trackNumberPoints,
            trackNumber,
            selected || highlighted,
            trackNumberDisplayMode,
        );
        features.push(...trackNumberFeatures);
    }

    segmentFeature.set('segment-data', dataHolder);
    return features;
}

function featureKey(
    alignmentType: MapAlignmentType,
    segmentId: LayoutSegmentId,
    segmentStart: number,
    segmentResolution: number,
    selected: boolean,
    highlighted: boolean,
    displayMode: DisplayMode,
    drawDistance: number,
    alignmentId: LocationTrackId,
    alignmentVersion: string | null,
): string {
    return `${alignmentType}_${segmentId}_${segmentStart}_${segmentResolution}_${
        selected ? '1' : '0'
    }_${highlighted ? '1' : '0'}_${displayMode}_${drawDistance}_${alignmentId}_${alignmentVersion}`;
}

type DataCollection = {
    compareString: string;
    dataHolders: SegmentDataHolder[];
};
type AlignmentParts = {
    baseAlignment: MapAlignment;
    segments: MapSegment[];
};

function collectSegmentData(
    alignments: MapAlignment[],
    trackNumbers: LayoutTrackNumber[],
    individualSegments: boolean,
    resolution: number,
): DataCollection {
    const alignmentParts = new Map<string, AlignmentParts>();
    const locationTrackIds: LocationTrackId[] = [];
    const referenceLineIds: ReferenceLineId[] = [];
    alignments.forEach((a) => {
        const key = `${a.alignmentType}_${a.id}`;
        const previous = alignmentParts.get(key);
        if (previous) {
            previous.segments = previous.segments.concat(a.segments);
        } else {
            if (a.alignmentType == 'LOCATION_TRACK') locationTrackIds.push(a.id);
            if (a.alignmentType == 'REFERENCE_LINE') referenceLineIds.push(a.id);
            alignmentParts.set(key, {
                // Separate segments from alignments
                baseAlignment: { ...a, segments: [] },
                segments: a.segments,
            });
        }
    });
    const dataHolders = [...alignmentParts.values()].flatMap((parts) => {
        const trackNumber = trackNumbers.find((tn) => tn.id === parts.baseAlignment.trackNumberId);
        const deduplicatedSegments = [
            ...new Map(parts.segments.map((s) => [s.id, s])).values(),
        ].sort(fieldComparator((s) => s.start));
        if (individualSegments) {
            return deduplicatedSegments.map((segment) => ({
                trackNumber: trackNumber || null,
                alignment: parts.baseAlignment,
                segment: segment,
                planId: null,
            }));
        } else {
            return [
                {
                    trackNumber: trackNumber || null,
                    alignment: parts.baseAlignment,
                    segment: simplifySegments(
                        parts.baseAlignment.id,
                        deduplicatedSegments,
                        resolution,
                    ),
                    planId: null,
                },
            ];
        }
    });
    const compare = `${JSON.stringify(referenceLineIds.sort())}+${JSON.stringify(
        locationTrackIds.sort(),
    )}`;
    return { compareString: compare, dataHolders: dataHolders };
}

const featureCache: Map<string, Feature<LineString | Point>[]> = new Map();

function createFeaturesCached(
    segmentDatas: SegmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    trackNumberDisplayMode: DisplayMode,
    trackNumberDrawDistance: number,
): Feature<LineString | Point>[] {
    const previousFeatures = new Map<string, Feature<LineString | Point>[]>(featureCache);
    featureCache.clear();
    return segmentDatas
        .map((data) => {
            const isReference = data.alignment.alignmentType == 'REFERENCE_LINE';
            const selected = isReference
                ? selection.selectedItems.referenceLines.includes(data.alignment.id)
                : selection.selectedItems.locationTracks.includes(data.alignment.id);
            const highlighted = isReference
                ? selection.highlightedItems.referenceLines.includes(data.alignment.id)
                : selection.highlightedItems.locationTracks.includes(data.alignment.id);
            const isLinking =
                linkingState &&
                (linkingState.type == LinkingType.LinkingGeometryWithAlignment ||
                    linkingState.type == LinkingType.LinkingAlignment) &&
                linkingState.layoutAlignmentType == data.alignment.alignmentType &&
                linkingState.layoutAlignmentInterval.start?.alignmentId === data.alignment.id;
            const key = featureKey(
                data.alignment.alignmentType,
                data.segment.id,
                data.segment.start,
                data.segment.resolution,
                !!(selected || isLinking),
                highlighted,
                trackNumberDisplayMode,
                trackNumberDrawDistance,
                data.alignment.id,
                data.alignment.version,
            );
            const previous = previousFeatures.get(key);
            const features =
                previous !== undefined
                    ? previous
                    : createFeatures(
                          data,
                          !!(selected || isLinking),
                          highlighted,
                          isReference && trackNumberDisplayMode === DisplayMode.NUMBER
                              ? DisplayMode.NONE // No need to repeat the track number on all lines
                              : trackNumberDisplayMode,
                          trackNumberDrawDistance,
                      );
            featureCache.set(key, features);
            return features;
        })
        .flat();
}

let alignmentCompare = '';
let alignmentChangeTimeCompare: TimeStamp | undefined = undefined;

adapterInfoRegister.add('alignment', {
    createAdapter: function (
        mapTiles: MapTile[],
        existingOlLayer: VectorLayer<VectorSource<LineString | Point>> | undefined,
        mapLayer: LayoutAlignmentsLayer,
        selection: Selection,
        publishType: PublishType,
        linkingState: LinkingState | undefined,
        changeTimes: ChangeTimes,
        olView: OlView,
        onViewContentChanged?: (items: OptionalShownItems) => void,
    ): OlLayerAdapter {
        const vectorSource = existingOlLayer?.getSource() || new VectorSource();
        // Use an existing layer or create a new one. Old layer is "recycled" to
        // prevent features to disappear while moving the map.
        const layer: VectorLayer<VectorSource<LineString | Point>> =
            existingOlLayer ||
            new VectorLayer({
                source: vectorSource,
                declutter: true,
            });

        layer.setVisible(mapLayer.visible);

        const resolution = olView.getResolution() || 0;
        let trackNumberDisplayMode = DisplayMode.NONE;
        const trackNumberDrawDistance = getTrackNumberDrawDistance(resolution);
        if (mapLayer.showTrackNumbers && trackNumberDrawDistance != null) {
            trackNumberDisplayMode =
                resolution < Limits.TRACK_NUMER_NAMES ? DisplayMode.NAME : DisplayMode.NUMBER;
        }

        const shownItemsSearchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
            const matchOptions: MatchOptions = {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: undefined,
            };
            const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
            const holders = getMatchingSegmentDatas(hitArea, features, matchOptions);
            // Map features are per-segment -> deduplicate for track numbers & alignments
            const trackNumberIds = deduplicate(
                holders.map(({ trackNumber }) => trackNumber?.id).filter(filterNotEmpty),
            );
            const alignments = holders
                .map(({ alignment }) => alignment)
                .filter(filterUniqueById((a) => `${a.alignmentType}_${a.id}`));
            const locationTracks = alignments.map(toLocationTrack).filter(filterNotEmpty);
            const referenceLines = alignments.map(toReferenceLine).filter(filterNotEmpty);
            return {
                trackNumbers: trackNumberIds.slice(0, options.limit),
                locationTracks: locationTracks.slice(0, options.limit),
                referenceLines: referenceLines.slice(0, options.limit),
                segments: holders.map(({ segment }) => segment).slice(0, options.limit),
            };
        };
        const selectionSearchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
            const found = shownItemsSearchFunction(hitArea, options);
            return {
                ...found,
                locationTracks: found.locationTracks.map((t) => t.id),
                referenceLines: found.referenceLines.map((l) => l.id),
            };
        };

        const selectedAlignment = selection.selectedItems?.locationTracks[0]
            ? selection.selectedItems?.locationTracks[0]
            : undefined;
        // Load alignments, track numbers and create features
        const alignmentsFetch = getAlignmentsByTiles(
            getMaxTimestamp(
                changeTimes.layoutLocationTrack,
                changeTimes.layoutReferenceLine,
                changeTimes.layoutTrackNumber,
            ),
            mapTiles,
            publishType,
            resolution > Limits.ALL_ALIGNMENTS,
            selectedAlignment,
        );
        const trackNumbersFetch = getTrackNumbers(publishType, changeTimes.layoutTrackNumber);
        Promise.all([alignmentsFetch, trackNumbersFetch])
            .then(([alignmentsPerTile, trackNumbers]) =>
                collectSegmentData(
                    alignmentsPerTile.flat(),
                    trackNumbers,
                    resolution < Limits.SEPARATE_SEGMENTS,
                    resolution * MAP_RESOLUTION_MULTIPLIER,
                ),
            )
            .then((dataCollection) => {
                const features = createFeaturesCached(
                    dataCollection.dataHolders,
                    selection,
                    linkingState,
                    trackNumberDisplayMode,
                    trackNumberDrawDistance || 0,
                );
                // All features ready, clear old ones and add new ones
                vectorSource.clear();
                vectorSource.addFeatures(features.flat());
                if (onViewContentChanged) {
                    const compare = `${publishType}${dataCollection.compareString}`;
                    const changeTimeCompare = getMaxTimestamp(
                        changeTimes.layoutLocationTrack,
                        changeTimes.layoutReferenceLine,
                    );
                    // Change time comparison is not needed for the alignment layer itself,
                    // but for the alignment list box, as it updates itself based on data
                    // updated here
                    if (
                        compare !== alignmentCompare ||
                        changeTimeCompare !== alignmentChangeTimeCompare
                    ) {
                        alignmentCompare = compare;
                        alignmentChangeTimeCompare = changeTimeCompare;
                        const area = fromExtent(olView.calculateExtent());
                        const result = shownItemsSearchFunction(area, {});
                        onViewContentChanged(result);
                    }
                }
            });

        return {
            layer: layer,
            searchItems: selectionSearchFunction,
            searchShownItems: shownItemsSearchFunction,
        };
    },
});
