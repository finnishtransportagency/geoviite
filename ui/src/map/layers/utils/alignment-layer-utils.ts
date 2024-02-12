import { RegularShape, Style } from 'ol/style';
import { AlignmentDataHolder, AlignmentHeader } from 'track-layout/layout-map-api';
import { ItemCollections, Selection } from 'selection/selection-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import { Coordinate } from 'ol/coordinate';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { interpolateXY } from 'utils/math-utils';
import { filterNotEmpty } from 'utils/array-utils';
import VectorSource from 'ol/source/Vector';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import { Rectangle } from 'model/geometry';
import { cache } from 'cache/cache';
import { exhaustiveMatchingGuard, getCoordsUnsafe, getUnsafe } from 'utils/type-utils';

const tickImageCache = cache<string, RegularShape>();

export const OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING = 0.5;
export const NORMAL_ALIGNMENT_OPACITY = 1;

export function getTickStyle(
    point1: Coordinate,
    point2: Coordinate,
    length: number,
    position: 'start' | 'end',
    style: Style,
): Style {
    const numberOfDifferentAngles = 128;
    const angleStep = (Math.PI * 2) / numberOfDifferentAngles;
    const [x1, y1] = getCoordsUnsafe(point1);
    const [x2, y2] = getCoordsUnsafe(point2);
    const actualAngle = Math.atan2(x1 - x2, y1 - y2) + Math.PI / 2;
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
    points: AlignmentPoint[],
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
            } else if (m >= getUnsafe(points[points.length - 1]).m) {
                const prev = getUnsafe(points[points.length - 2]);
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

function getCoordinate(points: AlignmentPoint[], m: number): number[] | undefined {
    const nextIndex = points.findIndex((p) => p.m >= m);
    const next = points[nextIndex];
    const prev = points[nextIndex - 1];
    if (nextIndex < 0 || nextIndex >= points.length) {
        return undefined;
    } else if (next?.m === m) {
        return pointToCoords(next);
    } else if (nextIndex === 0) {
        return undefined;
    } else {
        return interpolateXY(getUnsafe(prev), getUnsafe(next), m);
    }
}

export function createAlignmentFeature(
    alignment: AlignmentDataHolder,
    showEndTicks: boolean,
    style: Style,
): Feature<LineString | OlPoint>[] {
    const features: Feature<LineString | OlPoint>[] = [];
    const alignmentFeature = new Feature({
        geometry: new LineString(alignment.points.map(pointToCoords)),
    });
    features.push(alignmentFeature);

    alignmentFeature.setStyle(style);

    if (showEndTicks) {
        features.push(...createEndPointTicks(alignment, style));
    }

    setAlignmentFeatureProperty(alignmentFeature, alignment);

    return features;
}

export function createAlignmentFeatures(
    alignments: AlignmentDataHolder[],
    selection: Selection,
    showEndTicks: boolean,
    style: Style,
    hightlightStyle: Style,
): Feature<LineString | OlPoint>[] {
    return alignments.flatMap((alignment) =>
        createAlignmentFeature(
            alignment,
            showEndTicks,
            isHighlighted(selection, alignment.header) ? hightlightStyle : style,
        ),
    );
}

function includes(selection: ItemCollections, alignment: AlignmentHeader): boolean {
    switch (alignment.alignmentType) {
        case 'REFERENCE_LINE': {
            const tnId = alignment.trackNumberId;
            return tnId != undefined && selection.trackNumbers.includes(tnId);
        }
        case 'LOCATION_TRACK': {
            return selection.locationTracks.includes(alignment.id);
        }
        default:
            return exhaustiveMatchingGuard(alignment.alignmentType);
    }
}

export const isHighlighted = (selection: Selection, header: AlignmentHeader) =>
    includes(selection.highlightedItems, header);

export function getAlignmentHeaderStates(
    { header }: AlignmentDataHolder,
    selection: Selection,
    linkingState: LinkingState | undefined,
) {
    const selected = includes(selection.selectedItems, header);
    const highlighted = isHighlighted(selection, header);
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

export function createEndPointTicks(
    alignment: AlignmentDataHolder,
    tickStyle: Style,
): Feature<OlPoint>[] {
    const ticks: Feature<OlPoint>[] = [];
    const points = alignment.points;

    if (points.length >= 2) {
        if (points[0]?.m === 0) {
            const fP = pointToCoords(points[0]);
            const sP = pointToCoords(getUnsafe(points[1]));

            const startF = new Feature({ geometry: new OlPoint(fP) });

            startF.setStyle(getTickStyle(fP, sP, 6, 'start', tickStyle));

            ticks.push(startF);
        }

        const lastIdx = points.length - 1;
        if (points[lastIdx]?.m === alignment.header.length) {
            const lP = pointToCoords(getUnsafe(points[lastIdx]));
            const sLP = pointToCoords(getUnsafe(points[lastIdx - 1]));

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
