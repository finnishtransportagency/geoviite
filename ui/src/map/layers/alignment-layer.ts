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
import { LayoutPoint } from 'track-layout/track-layout-model';
import {
    AlignmentDataHolder,
    AlignmentHeader,
    getAlignmentSectionsWithoutLinkingByTiles,
    getLocationTrackSectionsWithoutProfileByTiles,
    getMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import {
    addBbox,
    alignmentId,
    getMatchingAlignmentDatas,
    MatchOptions,
    setAlignmentData,
} from 'map/layers/layer-utils';
import { OlLayerAdapter, SearchItemsOptions } from 'map/layers/layer-model';
import * as Limits from 'map/layers/layer-visibility-limits';
import { getBadgeDrawDistance } from 'map/layers/layer-visibility-limits';
import { deduplicate, filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { fromExtent } from 'ol/geom/Polygon';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { ChangeTimes } from 'common/common-slice';
import { findOrInterpolateXY, getPartialPolyLine } from 'utils/math-utils';
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

enum DisplayMode {
    REFERENCE_LINES,
    ALL,
}

enum BadgeColor {
    LIGHT,
    DARK,
}

type MapAlignmentBadgePoint = {
    point: number[];
    nextPoint: number[];
};

function createMapAlignmentBadgeFeature(
    name: string,
    points: MapAlignmentBadgePoint[],
    color: BadgeColor,
    lineHighlighted: boolean,
): Feature<Point>[] {
    const badgeStyle = getMapAlignmentBadgeStyle(color, lineHighlighted);

    return points.map((numberPoint) => {
        const badgeRotation = calculateBadgeRotation(numberPoint.point, numberPoint.nextPoint);

        const badgeFeature = new Feature({
            geometry: new Point(numberPoint.point),
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
                        const backgroundWidth = ctx.measureText(name).width + 16 * state.pixelRatio;
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
                            name,
                            coordinates[0] +
                                ((badgeRotation.drawFromEnd ? -1 : 1) * backgroundWidth) / 2,
                            coordinates[1] + 1 * state.pixelRatio,
                        );

                        ctx.restore();
                    },
                }),
        );

        return badgeFeature;
    });
}

function createFeatures(
    alignments: AlignmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    badgeDisplayMode: DisplayMode,
    badgeDrawDistance: number,
    showDuplicateTracks: boolean,
    profileInfo: AlignmentHighlight[],
    linkingInfo: AlignmentHighlight[],
): Feature<LineString | Point>[] {
    return alignments
        .map((alignment) => {
            const selected = isSelected(selection.selectedItems, alignment.header);
            const highlighted = isSelected(selection.highlightedItems, alignment.header);
            const isLinking =
                linkingState &&
                (linkingState.type == LinkingType.LinkingGeometryWithAlignment ||
                    linkingState.type == LinkingType.LinkingAlignment) &&
                linkingState.layoutAlignmentType == alignment.header.alignmentType &&
                linkingState.layoutAlignmentInterval.start?.alignmentId === alignment.header.id;
            const missingProfiles = profileInfo.filter(
                (i) => i.id === alignment.header.id && i.type == alignment.header.alignmentType,
            );
            const missingLinkings = linkingInfo.filter(
                (i) => i.id === alignment.header.id && i.type == alignment.header.alignmentType,
            );
            return createAlignmentFeatures(
                alignment,
                !!(selected || isLinking),
                highlighted,
                badgeDisplayMode,
                badgeDrawDistance,
                showDuplicateTracks,
                missingProfiles,
                missingLinkings,
            );
        })
        .flat();
}

function createBadgePoints(points: LayoutPoint[], drawDistance: number): MapAlignmentBadgePoint[] {
    if (points.length < 2) return [];
    const start = Math.ceil(points[0].m / drawDistance);
    const end = Math.floor(points[points.length - 1].m / drawDistance);
    if (start > end) return [];
    return Array.from({ length: 1 + end - start }, (_, i) => {
        const seek = findOrInterpolateXY(points, drawDistance * (i + start));
        if (!seek) return undefined;
        // Control point is the next one if a match was found,
        // the next real point if it was interpolated
        const controlPoint = points[seek.low === seek.high ? seek.high + 1 : seek.high];
        if (!controlPoint) return undefined;
        return {
            point: seek.point,
            nextPoint: [controlPoint.x, controlPoint.y],
        };
    }).filter(filterNotEmpty);
}

function createAlignmentFeatures(
    dataHolder: AlignmentDataHolder,
    selected: boolean,
    highlighted: boolean,
    badgeDisplayMode: DisplayMode,
    badgeDrawDistance: number,
    showDuplicateTracks: boolean,
    missingProfiles: AlignmentHighlight[],
    missingLinkings: AlignmentHighlight[],
): Feature<LineString | Point>[] {
    const lineString = new LineString(dataHolder.points.map((point) => [point.x, point.y]));
    const features: Feature<LineString | Point>[] = [];
    const alignmentFeature = new Feature<LineString>({
        geometry: lineString,
    });
    addBbox(alignmentFeature);
    features.push(alignmentFeature);

    const styles = [alignmentBackgroundStyle];
    const isReferenceLine = dataHolder.header.alignmentType === 'REFERENCE_LINE';

    if (selected) {
        styles.push(isReferenceLine ? selectedReferenceLineStyle : selectedLocationTrackStyle);
    } else if (highlighted) {
        styles.push(
            isReferenceLine ? highlightedReferenceLineStyle : highlightedLocationTrackStyle,
        );
    } else styles.push(isReferenceLine ? referenceLineStyle : locationTrackStyle);

    if (showDuplicateTracks && dataHolder.header.duplicateOf) {
        styles.push(alignmentBackgroundBlue);
    }

    missingProfiles.forEach((p) =>
        addHighlight(p, dataHolder.points, features, alignmentBackgroundRed),
    );

    missingLinkings.forEach((p) =>
        addHighlight(p, dataHolder.points, features, alignmentBackgroundRed),
    );

    if (dataHolder.header.alignmentType === 'LOCATION_TRACK') {
        const profile = missingProfiles.find((prof) => prof.id === dataHolder.header.id);
        if (profile) {
            addHighlight(profile, dataHolder.points, features, alignmentBackgroundRed);
        }
    }

    alignmentFeature.setStyle(styles);

    const badgePoints = createBadgePoints(dataHolder.points, badgeDrawDistance);
    const badgeColor = badgeDisplayMode === DisplayMode.ALL ? BadgeColor.LIGHT : BadgeColor.DARK;

    if (badgeDisplayMode === DisplayMode.ALL) {
        const alignmentBadgeFeatures = createMapAlignmentBadgeFeature(
            dataHolder.header.name,
            badgePoints,
            badgeColor,
            selected || highlighted,
        );

        features.push(...alignmentBadgeFeatures);
    } else if (isReferenceLine && dataHolder.trackNumber) {
        const referenceLineBadgeFeatures = createMapAlignmentBadgeFeature(
            dataHolder.trackNumber.number,
            badgePoints,
            badgeColor,
            selected || highlighted,
        );
        features.push(...referenceLineBadgeFeatures);
    }

    setAlignmentData(alignmentFeature, dataHolder);
    return features;
}

function addHighlight(
    highlight: AlignmentHighlight,
    points: LayoutPoint[],
    features: Feature<Point | LineString>[],
    highlightStyle: Style,
) {
    const highlightLineStrings = highlight.ranges
        .filter((range) => range.max > points[0].m && range.min < points[points.length - 1].m)
        .map((range) => {
            const pointsWithinRange = getPartialPolyLine(points, range.min, range.max);
            return pointsWithinRange.length > 1 ? new LineString(pointsWithinRange) : undefined;
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

function isSelected(selection: ItemCollections, alignment: AlignmentHeader): boolean {
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

let alignmentCompare = '';
let newestAlignmentAdapterId = 0;

export function createAlignmentLayerAdapter(
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
    const adapterId = ++newestAlignmentAdapterId;
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
    const badgeDrawDistance = getBadgeDrawDistance(resolution);

    const badgeDisplayMode =
        resolution < Limits.SHOW_LOCATION_TRACK_BADGES
            ? DisplayMode.ALL
            : DisplayMode.REFERENCE_LINES;

    const shownItemsSearchFunction = (hitArea: Polygon, options: SearchItemsOptions) => {
        const matchOptions: MatchOptions = {
            strategy: options.limit == 1 ? 'nearest' : 'limit',
            limit: undefined,
        };
        const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
        const holders = getMatchingAlignmentDatas(hitArea, features, matchOptions);
        const alignmentHeaders = holders
            .map(({ header }) => header)
            .filter(filterUniqueById((a) => `${a.alignmentType}_${a.id}`));
        const locationTracks = alignmentHeaders
            .map((a) => (a.alignmentType === 'LOCATION_TRACK' ? a.id : null))
            .filter(filterNotEmpty);

        const referenceLines = alignmentHeaders
            .filter((a) => a.alignmentType === 'REFERENCE_LINE')
            .slice(0, options.limit);
        const trackNumberIds = deduplicate(
            referenceLines.map((rl) => rl.trackNumberId).filter(filterNotEmpty),
        );

        return {
            locationTracks: locationTracks.slice(0, options.limit),
            trackNumbers: trackNumberIds,
            referenceLines: referenceLines.map((a) => a.id),
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

    const fetchType =
        resolution > Limits.ALL_ALIGNMENTS
            ? 'REFERENCE_LINES'
            : mapLayer.showReferenceLines
            ? 'ALL'
            : 'LOCATION_TRACKS';

    // Load alignments, track numbers and create features
    const alignmentsFetch = getMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        publishType,
        fetchType,
        selectedAlignment,
    );

    const sectionsWithoutProfile = mapLayer.showMissingVerticalGeometry
        ? getLocationTrackSectionsWithoutProfileByTiles(
              changeTimes.layoutLocationTrack,
              publishType,
              mapTiles,
          )
        : Promise.resolve([]);

    const sectionsWithoutLinking = mapLayer.showMissingLinking
        ? getAlignmentSectionsWithoutLinkingByTiles(
              getMaxTimestamp(changeTimes.layoutLocationTrack, changeTimes.layoutReferenceLine),
              publishType,
              fetchType,
              mapTiles,
          )
        : Promise.resolve([]);

    Promise.all([alignmentsFetch, sectionsWithoutProfile, sectionsWithoutLinking]).then(
        ([alignments, sectionsWithoutProfile, sectionsWithoutLinking]) => {
            if (adapterId != newestAlignmentAdapterId) return;

            const features = createFeatures(
                alignments,
                selection,
                linkingState,
                badgeDisplayMode,
                badgeDrawDistance || 0,
                mapLayer.showDuplicateTracks,
                sectionsWithoutProfile,
                sectionsWithoutLinking,
            );

            // All features ready, clear old ones and add new ones
            vectorSource.clear();
            vectorSource.addFeatures(features.flat());

            if (onViewContentChanged) {
                const compare = `${JSON.stringify(
                    alignments.map((a) => alignmentId(a.header)).sort(),
                )}`;

                if (compare !== alignmentCompare) {
                    alignmentCompare = compare;
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
}

function getMapAlignmentBadgeStyle(badgeColor: BadgeColor, lineHighlighted: boolean) {
    const isLight = badgeColor === BadgeColor.LIGHT;

    let color = mapStyles['alignment-badge-color-white'];
    let background = mapStyles['alignment-badge-background'];
    let backgroundBorder: string | undefined;

    if (lineHighlighted) {
        background = mapStyles['alignment-badge-background-selected'];
    } else if (isLight) {
        color = mapStyles['alignment-badge-color'];
        background = mapStyles['alignment-badge-background-white'];
        backgroundBorder = mapStyles['alignment-badge-background-border'];
    }

    return {
        color,
        background,
        backgroundBorder,
    };
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
