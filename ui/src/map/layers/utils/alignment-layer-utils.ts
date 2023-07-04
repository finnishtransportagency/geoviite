import { RegularShape, Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { AlignmentDataHolder, AlignmentHeader } from 'track-layout/layout-map-api';
import { ItemCollections, Selection } from 'selection/selection-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import { Coordinate } from 'ol/coordinate';
import { LayoutPoint } from 'track-layout/track-layout-model';
import { interpolateXY } from 'utils/math-utils';
import { filterNotEmpty } from 'utils/array-utils';
import VectorSource from 'ol/source/Vector';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { Rectangle } from 'model/geometry';
import { cache } from 'cache/cache';

const tickImageCache = cache<string, RegularShape>();

const locationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 0,
});

const highlightedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 2,
});

const selectedLocationTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 2,
    }),
    zIndex: 2,
});

const referenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 3,
    }),
    zIndex: 0,
});

const highlightedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 3,
    }),
    zIndex: 1,
});

const selectedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 4,
    }),
    zIndex: 1,
});

const endPointTickStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 1,
    }),
    zIndex: 1,
});

const highlightedEndPointTickStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 1,
    }),
    zIndex: 1,
});

export function getTickStyle(
    point1: Coordinate,
    point2: Coordinate,
    length: number,
    position: 'start' | 'end',
    style: Style,
): Style {
    const angleVersionCount = 128;
    const angleStep = (Math.PI * 2) / angleVersionCount;
    const actualAngle = Math.atan2(point1[0] - point2[0], point1[1] - point2[1]) + Math.PI / 2;
    const roundAngle = Math.round(actualAngle / angleStep) * angleStep;

    const cacheKey = `${roundAngle}-${JSON.stringify(style.getStroke())}`;
    const image = tickImageCache.getOrCreate(
        cacheKey,
        () =>
            new RegularShape({
                stroke: style.getStroke(),
                points: 2,
                radius: length,
                radius2: 0,
                angle: roundAngle,
            }),
    );

    return new Style({
        geometry: new OlPoint(position == 'start' ? point1 : point2),
        image: image,
        zIndex: style.getZIndex(),
    });
}

export function getTickStyles(
    points: LayoutPoint[],
    mValues: number[],
    length: number,
    style: Style,
): Style[] {
    if (points.length < 2) {
        return [];
    }
    return mValues
        .map((m) => {
            const coordinate = getCoordinate(points, m);
            if (!coordinate) {
                return undefined;
            } else if (m >= points[points.length - 1].m) {
                const prev = points[points.length - 2];
                return getTickStyle(pointToCoords(prev), coordinate, length, 'end', style);
            } else {
                const next = points.find((p) => p.m > m);
                return next
                    ? getTickStyle(coordinate, pointToCoords(next), length, 'start', style)
                    : undefined;
            }
        })
        .filter(filterNotEmpty);
}

function getCoordinate(points: LayoutPoint[], m: number): number[] | undefined {
    const nextIndex = points.findIndex((p) => p.m >= m);
    if (nextIndex < 0 || nextIndex >= points.length) {
        return undefined;
    } else if (points[nextIndex].m === m) {
        return pointToCoords(points[nextIndex]);
    } else if (nextIndex === 0) {
        return undefined;
    } else {
        return interpolateXY(points[nextIndex - 1], points[nextIndex], m);
    }
}

export function createAlignmentFeatures(
    alignments: AlignmentDataHolder[],
    selection: Selection,
    linkingState: LinkingState | undefined,
    showEndTicks: boolean,
): Feature<LineString | OlPoint>[] {
    return alignments.flatMap((alignment) => {
        const { selected, isLinking, highlighted } = getAlignmentHeaderStates(
            alignment,
            selection,
            linkingState,
        );

        const features: Feature<LineString | OlPoint>[] = [];
        const alignmentFeature = new Feature({
            geometry: new LineString(alignment.points.map(pointToCoords)),
        });
        features.push(alignmentFeature);

        const isReferenceLine = alignment.header.alignmentType === 'REFERENCE_LINE';

        if (selected || isLinking) {
            alignmentFeature.setStyle(
                isReferenceLine ? selectedReferenceLineStyle : selectedLocationTrackStyle,
            );
        } else if (highlighted) {
            alignmentFeature.setStyle(
                isReferenceLine ? highlightedReferenceLineStyle : highlightedLocationTrackStyle,
            );
        } else alignmentFeature.setStyle(isReferenceLine ? referenceLineStyle : locationTrackStyle);

        if (showEndTicks) {
            features.push(...createEndPointTicks(alignment, selected || isLinking || highlighted));
        }

        setAlignmentFeatureProperty(alignmentFeature, alignment);

        return features;
    });
}

function includes(selection: ItemCollections, alignment: AlignmentHeader): boolean {
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

export function getAlignmentHeaderStates(
    { header }: AlignmentDataHolder,
    selection: Selection,
    linkingState: LinkingState | undefined,
) {
    const selected = includes(selection.selectedItems, header);
    const highlighted = includes(selection.highlightedItems, header);
    const isLinking = linkingState
        ? (linkingState.type == LinkingType.LinkingGeometryWithAlignment ||
              linkingState.type == LinkingType.LinkingAlignment) &&
          linkingState.layoutAlignmentType == header.alignmentType &&
          linkingState.layoutAlignmentInterval.start?.alignmentId === header.id
        : false;

    return {
        selected,
        highlighted,
        isLinking,
    };
}

function createEndPointTicks(
    alignment: AlignmentDataHolder,
    contrast: boolean,
): Feature<OlPoint>[] {
    const ticks: Feature<OlPoint>[] = [];
    const points = alignment.points;

    if (points.length >= 2) {
        const tickStyle = contrast ? highlightedEndPointTickStyle : endPointTickStyle;

        if (points[0].m === 0) {
            const fP = pointToCoords(points[0]);
            const sP = pointToCoords(points[1]);

            const startF = new Feature({ geometry: new OlPoint(fP) });

            startF.setStyle(getTickStyle(fP, sP, 6, 'start', tickStyle));

            ticks.push(startF);
        }

        const lastIdx = points.length - 1;
        if (points[lastIdx].m === alignment.header.length) {
            const lP = pointToCoords(points[lastIdx]);
            const sLP = pointToCoords(points[lastIdx - 1]);

            const endF = new Feature({ geometry: new OlPoint(lP) });

            endF.setStyle(getTickStyle(sLP, lP, 6, 'end', tickStyle));

            ticks.push(endF);
        }
    }

    return ticks;
}

export const ALIGNMENT_FEATURE_DATA_PROPERTY = 'alignment-data';

export function findMatchingAlignments(
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
): AlignmentDataHolder[] {
    return findMatchingEntities<AlignmentDataHolder>(
        hitArea,
        source,
        ALIGNMENT_FEATURE_DATA_PROPERTY,
        options,
    );
}

export function setAlignmentFeatureProperty(
    feature: Feature<LineString>,
    data: AlignmentDataHolder,
) {
    feature.set(ALIGNMENT_FEATURE_DATA_PROPERTY, data);
}
