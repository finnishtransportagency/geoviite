import mapStyles from 'map/map.module.scss';
import Feature from 'ol/Feature';
import { LineString, Point, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Stroke, Style } from 'ol/style';
import OlView from 'ol/View';
import {
    AlignmentHighlight,
    LayoutAlignmentsLayer,
    MapTile,
    OptionalShownItems,
} from 'map/map-model';
import { ItemCollections, Selection } from 'selection/selection-model';
import { adapterInfoRegister } from './register';
import {
    LayoutPoint,
    LayoutSegmentId,
    LayoutTrackNumber,
    LocationTrackId,
    MapAlignment,
    MapAlignmentType,
    MapSegment,
    ReferenceLineId,
    simplifySegments,
} from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import {
    getAlignmentsByTiles,
    getAlignmentSectionsWithoutProfile,
} from 'track-layout/layout-map-api';
import {
    addBbox,
    getMatchingSegmentDatas,
    MatchOptions,
    SegmentDataHolder,
} from 'map/layers/layer-utils';
import { OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
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
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { GeometryPlanId } from 'geometry/geometry-model';
import { combineBoundingBoxes } from 'model/geometry';

export const FEATURE_PROPERTY_SEGMENT_DATA = 'segment-data';

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

const alignmentBackgroundRed = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedBackground,
        width: 12,
    }),
    zIndex: 1,
});

const alignmentBackgroundBlue = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBlueBackground,
        width: 12,
    }),
    zIndex: 1,
});

export enum DisplayMode {
    NONE,
    NUMBER,
    NAME,
}

export type MapAlignmentBadgePoint = {
    point: LayoutPoint;
    nextPoint: LayoutPoint;
};

export function createMapAlignmentBadgeFeature(
    alignment: MapAlignment,
    points: MapAlignmentBadgePoint[],
    trackNumber: LayoutTrackNumber,
    lineHighlighted: boolean,
    displayMode: DisplayMode,
): Feature<Point>[] {
    const badgeStyle = getMapAlignmentBadgeStyle(
        trackNumber,
        alignment,
        displayMode,
        lineHighlighted,
    );

    return points.map((numberPoint) => {
        const coordinate: Coordinate = [numberPoint.point.x, numberPoint.point.y];
        const controlCoordinate: Coordinate = [numberPoint.nextPoint.x, numberPoint.nextPoint.y];
        const badgeRotation = calculateBadgeRotation(coordinate, controlCoordinate);

        const badgeFeature = new Feature<Point>({
            geometry: new Point(coordinate),
        });

        badgeFeature.setStyle(
            () =>
                new Style({
                    zIndex: 5,
                    renderer: (coordinates: Coordinate, state: State) => {
                        const ctx = state.context;
                        ctx.font = `${mapStyles['alignment-badge-font-weight']} ${
                            state.pixelRatio * 12
                        }px ${mapStyles['alignment-badge-font-family']}`;
                        const backgroundWidth =
                            ctx.measureText(badgeStyle.text).width + 16 * state.pixelRatio;
                        const backgroundHeight = 14 * state.pixelRatio;

                        ctx.save();
                        ctx.beginPath();

                        ctx.translate(coordinates[0], coordinates[1]);
                        ctx.rotate(badgeRotation.rotation);
                        ctx.translate(-coordinates[0], -coordinates[1]);

                        ctx.fillStyle = badgeStyle.background;
                        ctx.rect(
                            coordinates[0] - (badgeRotation.drawFromEnd ? backgroundWidth : 0),
                            coordinates[1] - backgroundHeight / 2,
                            backgroundWidth,
                            backgroundHeight,
                        );

                        ctx.fill();
                        if (badgeStyle.backgroundBorder) {
                            ctx.strokeStyle = badgeStyle.backgroundBorder;
                            ctx.lineWidth = 1;
                            ctx.stroke();
                        }

                        ctx.closePath();

                        ctx.fillStyle = badgeStyle.color;
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(
                            badgeStyle.text,
                            coordinates[0] +
                                ((badgeRotation.drawFromEnd ? -1 : 1) * backgroundWidth) / 2,
                            coordinates[1] + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        badgeFeature.set('mapAlignmentBadge', trackNumber);
        return badgeFeature;
    });
}

function createFeatures(
    dataHolder: SegmentDataHolder,
    selected: boolean,
    highlighted: boolean,
    trackNumberDisplayMode: DisplayMode,
    drawDistance: number,
    showReferenceLines: boolean,
    showMissingVerticalGeometry: boolean,
    showMissingLinking: boolean,
    showDuplicateTracks: boolean,
    profileInfo: AlignmentHighlight[] | null,
): Feature<LineString | Point>[] {
    const { trackNumber, alignment, segment } = dataHolder;
    const lineString = new LineString(segment.points.map((point) => [point.x, point.y]));
    const features: Feature<LineString | Point>[] = [];
    const segmentFeature: Feature<LineString> = new Feature<LineString>({
        geometry: lineString,
    });
    addBbox(segmentFeature);
    features.push(segmentFeature);

    const styles = [alignmentBackgroundStyle];
    const isReferenceLine = alignment.alignmentType === 'REFERENCE_LINE';

    if (selected) {
        styles.push(isReferenceLine ? selectedReferenceLineStyle : selectedLocationTrackStyle);
    } else if (highlighted) {
        styles.push(
            isReferenceLine ? highlightedReferenceLineStyle : highlightedLocationTrackStyle,
        );
    } else styles.push(isReferenceLine ? referenceLineStyle : locationTrackStyle);

    if (
        showMissingLinking &&
        dataHolder.segment.source == 'IMPORTED' &&
        !dataHolder.segment.sourceId
    ) {
        styles.push(alignmentBackgroundRed);
    }

    if (showDuplicateTracks && dataHolder.alignment.duplicateOf) {
        styles.push(alignmentBackgroundBlue);
    }

    if (showMissingVerticalGeometry) {
        const profile = profileInfo?.find((prof) => prof.id === alignment.id);
        if (profile) {
            addHighlight(profile, segment, features, alignmentBackgroundRed);
        }
    }

    segmentFeature.setStyle(styles);

    const numbersBeforeSegment = Math.floor(segment.startM / drawDistance);
    const numbersAfterSegment = Math.floor(segment.endM / drawDistance);
    const segmentHasTrackNumberLabel =
        segment.startM === 0.0 || numbersBeforeSegment !== numbersAfterSegment;
    if (
        trackNumber !== null &&
        trackNumberDisplayMode !== DisplayMode.NONE &&
        segmentHasTrackNumberLabel
    ) {
        const badgePoints: MapAlignmentBadgePoint[] = [];
        let length = segment.startM % drawDistance;
        const pointLength = segment.length / segment.points.length;
        segment.points.forEach((point, index) => {
            if (
                (length % drawDistance == 0.0 || length >= drawDistance) &&
                index < segment.points.length - 1
            ) {
                badgePoints.push({
                    point: point,
                    nextPoint: segment.points[index + 1],
                });
            }
            if (length >= drawDistance) {
                length -= drawDistance;
            }
            length += pointLength;
        });

        //When zoomed out enough, show track number alignment badges only
        if (
            trackNumberDisplayMode != DisplayMode.NUMBER ||
            alignment.alignmentType != 'LOCATION_TRACK' ||
            !showReferenceLines
        ) {
            const alignmentBadgeFeatures: Feature<Point>[] = createMapAlignmentBadgeFeature(
                alignment,
                badgePoints,
                trackNumber,
                selected || highlighted,
                trackNumberDisplayMode,
            );
            features.push(...alignmentBadgeFeatures);
        }
    }

    segmentFeature.set(FEATURE_PROPERTY_SEGMENT_DATA, dataHolder);
    return features;
}

function addHighlight(
    highlight: AlignmentHighlight,
    segment: MapSegment,
    features: Feature<Point | LineString>[],
    highlightStyle: Style,
): void {
    const highlightLineStrings = highlight.ranges
        .map((range) => {
            const pointsWithinRange = segment.points.filter(
                (segmentPoint) => segmentPoint.m >= range.start && segmentPoint.m <= range.end,
            );
            return pointsWithinRange.length > 1
                ? new LineString(pointsWithinRange.map((point) => [point.x, point.y]))
                : undefined;
        })
        .filter(filterNotEmpty);

    highlightLineStrings.forEach((lineString) => {
        const highlightFeature = new Feature({
            geometry: lineString,
        });
        addBbox(highlightFeature);
        features.push(highlightFeature);
        highlightFeature.setStyle([highlightStyle]);
    });
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
    missingLinking: boolean,
    missingVerticalGeometry: boolean,
    selectedPlanSegments: boolean,
    selectedPlans: GeometryPlanId[],
    duplicateTracks: boolean,
): string {
    return `${alignmentType}_${segmentId}_${segmentStart}_${segmentResolution}_${
        selected ? '1' : '0'
    }_${
        highlighted ? '1' : '0'
    }_${displayMode}_${drawDistance}_${alignmentId}_${alignmentVersion}_${
        missingLinking ? '1' : '0'
    }_${missingVerticalGeometry ? '1' : '0'}_${duplicateTracks ? '1' : '0'}_${
        selectedPlanSegments ? selectedPlans.join('-') : '-1'
    }`;
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
        ].sort(fieldComparator((s) => s.startM));
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

function isSelected(selection: ItemCollections, alignment: MapAlignment): boolean {
    switch (alignment.alignmentType) {
        case 'REFERENCE_LINE': {
            const tnId = alignment.trackNumberId;
            return tnId != null && selection.trackNumbers.includes(tnId);
        }
        case 'LOCATION_TRACK': {
            return selection.locationTracks.includes(alignment.id);
        }
    }
}

function createFeaturesCached(
    segmentDatas: SegmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    trackNumberDisplayMode: DisplayMode,
    trackNumberDrawDistance: number,
    showReferenceLines: boolean,
    showMissingVerticalGeometry: boolean,
    showSegmentsFromSelectedPlan: boolean,
    showMissingLinking: boolean,
    showDuplicateTracks: boolean,
    profileInfo: AlignmentHighlight[] | null,
): Feature<LineString | Point>[] {
    const previousFeatures = new Map<string, Feature<LineString | Point>[]>(featureCache);
    featureCache.clear();
    return segmentDatas
        .map((data) => {
            const selected = isSelected(selection.selectedItems, data.alignment);
            const highlighted = isSelected(selection.highlightedItems, data.alignment);
            const isLinking =
                linkingState &&
                (linkingState.type == LinkingType.LinkingGeometryWithAlignment ||
                    linkingState.type == LinkingType.LinkingAlignment) &&
                linkingState.layoutAlignmentType == data.alignment.alignmentType &&
                linkingState.layoutAlignmentInterval.start?.alignmentId === data.alignment.id;
            const key = featureKey(
                data.alignment.alignmentType,
                data.segment.id,
                data.segment.startM,
                data.segment.resolution,
                !!(selected || isLinking),
                highlighted,
                trackNumberDisplayMode,
                trackNumberDrawDistance,
                data.alignment.id,
                data.alignment.version,
                showMissingLinking,
                showMissingVerticalGeometry,
                showSegmentsFromSelectedPlan,
                selection.selectedItems.geometryPlans,
                showDuplicateTracks,
            );
            const previous = previousFeatures.get(key);
            const features =
                previous !== undefined
                    ? previous
                    : createFeatures(
                          data,
                          !!(selected || isLinking),
                          highlighted,
                          trackNumberDisplayMode,
                          trackNumberDrawDistance,
                          showReferenceLines,
                          showMissingVerticalGeometry,
                          showMissingLinking,
                          showDuplicateTracks,
                          profileInfo,
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
            const alignments = holders
                .map(({ alignment }) => alignment)
                .filter(filterUniqueById((a) => `${a.alignmentType}_${a.id}`));
            const locationTracks = alignments
                .map((a) => (a.alignmentType === 'LOCATION_TRACK' ? a.id : null))
                .filter(filterNotEmpty);

            const referenceLines = alignments.filter((a) => a.alignmentType === 'REFERENCE_LINE');
            const trackNumberIds = deduplicate(
                referenceLines.map((rl) => rl.trackNumberId).filter(filterNotEmpty),
            );

            return {
                locationTracks: locationTracks.slice(0, options.limit),
                trackNumbers: trackNumberIds,
                referenceLines: referenceLines.slice(0, options.limit).map((a) => a.id),
                segments: holders.map(({ segment }) => segment).slice(0, options.limit),
            };
        };
        const selectionSearchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
            const found = shownItemsSearchFunction(hitArea, options);
            return {
                locationTracks: found.locationTracks,
                referenceLines: found.referenceLines,
                trackNumbers: found.trackNumbers,
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
            resolution > Limits.ALL_ALIGNMENTS
                ? 'reference'
                : mapLayer.showReferenceLines
                ? 'all'
                : 'locationtrack',
            selectedAlignment,
        );
        const trackNumbersFetch = getTrackNumbers(publishType, changeTimes.layoutTrackNumber);
        const alignmentSectionsWithoutProfile = mapLayer.showMissingVerticalGeometry
            ? getAlignmentSectionsWithoutProfile(
                  publishType,
                  combineBoundingBoxes(mapTiles.map((tile) => tile.area)),
              )
            : Promise.resolve<AlignmentHighlight[] | null>(null);
        Promise.all([alignmentsFetch, trackNumbersFetch, alignmentSectionsWithoutProfile])
            .then(([alignmentsPerTile, trackNumbers, alignmentSectionsWithoutProfile]) => [
                collectSegmentData(
                    alignmentsPerTile.flat(),
                    trackNumbers,
                    resolution < Limits.SEPARATE_SEGMENTS,
                    resolution * MAP_RESOLUTION_MULTIPLIER,
                ),
                alignmentSectionsWithoutProfile,
            ])
            .then(
                ([dataCollection, alignmentSectionsWithoutProfile]: [
                    DataCollection,
                    AlignmentHighlight[] | null,
                ]) => {
                    const features = createFeaturesCached(
                        dataCollection.dataHolders,
                        selection,
                        linkingState,
                        trackNumberDisplayMode,
                        trackNumberDrawDistance || 0,
                        mapLayer.showReferenceLines,
                        mapLayer.showMissingVerticalGeometry,
                        mapLayer.showMissingLinking,
                        mapLayer.showSegmentsFromSelectedPlan,
                        mapLayer.showDuplicateTracks,
                        alignmentSectionsWithoutProfile,
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
                },
            );

        return {
            layer: layer,
            searchItems: selectionSearchFunction,
            searchShownItems: shownItemsSearchFunction,
        };
    },
});

export function getMapAlignmentBadgeStyle(
    trackNumber: LayoutTrackNumber,
    alignment: MapAlignment,
    displayMode: DisplayMode,
    lineHighlighted: boolean,
) {
    let text: string;
    let color: string;
    let background: string;
    let backgroundBorder: string | undefined = undefined;

    if (displayMode === DisplayMode.NUMBER) {
        text = trackNumber.number;
        color = mapStyles['alignment-badge-color'];

        background = lineHighlighted
            ? mapStyles['alignment-badge-background-selected']
            : mapStyles['alignment-badge-background'];
    } else {
        text = getName(alignment, trackNumber);

        if (lineHighlighted) {
            color = mapStyles['alignment-badge-color'];
            background = mapStyles['alignment-badge-background-selected'];
        } else {
            backgroundBorder = mapStyles['alignment-badge-background-border'];
            color = mapStyles['alignment-badge-color-near'];
            background = mapStyles['alignment-badge-background-near'];
        }
    }

    return {
        text,
        color,
        background,
        backgroundBorder,
    };
}

function getName(alignment: MapAlignment, trackNumber: LayoutTrackNumber): string {
    switch (alignment.alignmentType) {
        case 'REFERENCE_LINE':
            return trackNumber.number;
        case 'LOCATION_TRACK':
            return `${trackNumber.number} / ${alignment.name}`;
    }
}

export function calculateBadgeRotation(start: Coordinate, end: Coordinate) {
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
    const angle = Math.atan2(dy, dx);
    let rotation: number;
    let drawFromEnd = true;

    if (Math.abs(angle) <= Math.PI / 2) {
        // 1st and 4th quadrant
        rotation = -angle;
        drawFromEnd = false;
    } else if (angle < 0) {
        rotation = -Math.PI - angle; // 3rd quadrant
    } else {
        rotation = Math.PI - angle; // 2nd quadrant
    }

    return {
        drawFromEnd,
        rotation,
    };
}
