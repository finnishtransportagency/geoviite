import { RegularShape, Style } from 'ol/style';
import {
    AlignmentDataHolder,
    LayoutAlignmentDataHolder,
    LayoutAlignmentHeader,
} from 'track-layout/layout-map-api';
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
import { exhaustiveMatchingGuard, expectCoordinate } from 'utils/type-utils';

const tickImageCache = cache<string, RegularShape>();

export const OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING = 0.5;
export const NORMAL_ALIGNMENT_OPACITY = 1;

export const REFERENCE_LINE_ALIGNMENT_WIDTH = 3;
export const LOCATION_TRACK_ALIGNMENT_WIDTH = 1;

export function getTickStyle(
    point1: Coordinate,
    point2: Coordinate,
    length: number,
    position: 'start' | 'end',
    style: Style,
): Style {
    const numberOfDifferentAngles = 128;
    const angleStep = (Math.PI * 2) / numberOfDifferentAngles;
    const [x1, y1] = expectCoordinate(point1);
    const [x2, y2] = expectCoordinate(point2);
    const actualAngle = Math.atan2(x1 - x2, y1 - y2) + Math.PI / 2;
    const roundAngle = Math.round(actualAngle / angleStep) * angleStep;

    const cacheKey = `${roundAngle}-${JSON.stringify(style.getStroke())}`;
    const image = tickImageCache.getOrCreate(
        cacheKey,
        () =>
            new RegularShape({
                stroke: style.getStroke() || undefined,
                points: 2,
                radius: length,
                radius2: 0,
                angle: roundAngle,
            }),
    );

    return new Style({
        geometry: new OlPoint(position === 'start' ? point1 : point2),
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
    const last = points[points.length - 1];
    const secondToLast = points[points.length - 2];
    if (!last || !secondToLast) {
        return [];
    }
    return mValues
        .map((m) => {
            const coordinate = getCoordinate(points, m);
            if (!coordinate) {
                return undefined;
            } else if (m >= last.m) {
                return getTickStyle(pointToCoords(secondToLast), coordinate, length, 'end', style);
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
    if (!next) {
        return undefined;
    } else if (next?.m === m) {
        return pointToCoords(next);
    } else if (!prev) {
        return undefined;
    } else {
        return interpolateXY(prev, next, m);
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
    alignments: LayoutAlignmentDataHolder[],
    selection: Selection,
    showEndTicks: boolean,
    style: Style,
    hightlightStyle?: Style,
): Feature<LineString | OlPoint>[] {
    return alignments.flatMap((alignment) =>
        createAlignmentFeature(
            alignment,
            showEndTicks,
            isHighlighted(selection, alignment.header) && hightlightStyle ? hightlightStyle : style,
        ),
    );
}

function includes(selection: ItemCollections, alignment: LayoutAlignmentHeader): boolean {
    const type = alignment.alignmentType;
    switch (type) {
        case 'REFERENCE_LINE': {
            const tnId = alignment.trackNumberId;
            return tnId !== undefined && selection.trackNumbers.includes(tnId);
        }
        case 'LOCATION_TRACK': {
            return selection.locationTracks.includes(alignment.id);
        }
        default:
            return exhaustiveMatchingGuard(type);
    }
}

export const isHighlighted = (selection: Selection, header: LayoutAlignmentHeader) =>
    includes(selection.highlightedItems, header);

export function getAlignmentHeaderStates(
    { header }: LayoutAlignmentDataHolder,
    selection: Selection,
    linkingState: LinkingState | undefined,
) {
    const selected = includes(selection.selectedItems, header);
    const highlighted = isHighlighted(selection, header);
    const isLinking = linkingState
        ? (linkingState.type === LinkingType.LinkingGeometryWithAlignment ||
              linkingState.type === LinkingType.LinkingAlignment) &&
          linkingState.layoutAlignment.type === header.alignmentType &&
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
    const first = alignment.points[0];
    const second = alignment.points[1];

    if (first && second) {
        if (first.m === 0) {
            const fP = pointToCoords(first);
            const sP = pointToCoords(second);

            const startF = new Feature({ geometry: new OlPoint(fP) });

            startF.setStyle(getTickStyle(fP, sP, 6, 'start', tickStyle));

            ticks.push(startF);
        }

        const last = alignment.points[alignment.points.length - 1];
        const secondToLast = alignment.points[alignment.points.length - 2];
        if (last?.m === alignment.header.length && secondToLast) {
            const lP = pointToCoords(last);
            const sLP = pointToCoords(secondToLast);

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
