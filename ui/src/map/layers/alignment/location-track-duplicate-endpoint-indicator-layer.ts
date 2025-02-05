import { MapLayerName, MapTile } from 'map/map-model';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import Feature from 'ol/Feature';
import { AlignmentStartAndEnd, LocationTrackDuplicate } from 'track-layout/track-layout-model';
import { SplittingState } from 'tool-panel/location-track/split-store';
import {
    DASHED_LINE_INDICATOR_FONT_SIZE,
    getRotation,
    indicatorDashedLineWidth,
    indicatorLineDash,
    indicatorLineHeight,
    indicatorLineWidth,
    indicatorTextBackgroundHeight,
    indicatorTextPadding,
} from 'map/layers/utils/dashed-line-indicator-utils';
import mapStyles from 'map/map.module.scss';
import { Point as OlPoint } from 'ol/geom';
import { Coordinate } from 'ol/coordinate';
import { State } from 'ol/render';
import { Style } from 'ol/style';
import { formatTrackMeter } from 'utils/geography-utils';
import {
    getLocationTrackInfoboxExtras,
    getManyStartsAndEnds,
} from 'track-layout/layout-location-track-api';
import { Point } from 'model/geometry';
import { expectCoordinate } from 'utils/type-utils';
import { brand } from 'common/brand';

type EndpointType = 'START' | 'END';
const BLACK = '#000000';

function trackMetersAreApproximatelySame(
    m1: number | undefined,
    m2: number | undefined,
): boolean | undefined {
    if (m1 === undefined || m2 === undefined) {
        return undefined;
    }

    return Math.abs(m1 - m2) <= 0.0001;
}

function createDuplicateTrackEndpointAddressFeature(
    point: Point,
    controlPoint: Point,
    name: string,
    trackMeter: TrackMeter | undefined,
    endpointType: EndpointType,
    zIndex: number,
): Feature<OlPoint> {
    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point)),
        zIndex,
    });

    const { rotation, positiveXOffset } =
        endpointType === 'START'
            ? getRotation(pointToCoords(point), pointToCoords(controlPoint))
            : getRotation(pointToCoords(controlPoint), pointToCoords(point));
    const textBelowLine =
        (endpointType === 'START' && positiveXOffset) ||
        (endpointType === 'END' && !positiveXOffset);

    const renderer = (coord: Coordinate, { pixelRatio, context }: State) => {
        const [x, y] = expectCoordinate(coord);
        const ctx = context;

        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${
            pixelRatio * DASHED_LINE_INDICATOR_FONT_SIZE
        }px ${mapStyles['alignmentBadge-font-family']}`;

        ctx.save();
        const textPositionOffset = textBelowLine
            ? 0
            : 2 * indicatorTextBackgroundHeight(pixelRatio);

        const nameText = name;
        const nameTextWidth = ctx.measureText(nameText).width;
        const nameTextXEndPosition =
            x - indicatorLineWidth(pixelRatio) * (positiveXOffset ? 1 : -1);
        const nameTextYEndPosition = y - indicatorLineHeight(pixelRatio) + textPositionOffset;

        const trackMeterText = trackMeter ? formatTrackMeter(trackMeter) : '';
        const trackMeterTextWidth = ctx.measureText(trackMeterText).width;
        const trackMeterTextXEndPosition =
            x - indicatorLineWidth(pixelRatio) * (positiveXOffset ? 1 : -1);
        const trackMeterTextYEndPosition =
            y -
            indicatorLineHeight(pixelRatio) +
            indicatorTextBackgroundHeight(pixelRatio) +
            textPositionOffset;

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
        ctx.fillRect(
            nameTextXEndPosition -
                (positiveXOffset ? 0 : nameTextWidth + indicatorTextPadding(pixelRatio) * 2),
            textBelowLine ? y - indicatorTextBackgroundHeight(pixelRatio) * 2 : y,
            nameTextWidth + indicatorTextPadding(pixelRatio) * 2,
            indicatorTextBackgroundHeight(pixelRatio),
        );
        ctx.fillRect(
            trackMeterTextXEndPosition -
                (positiveXOffset ? 0 : trackMeterTextWidth + indicatorTextPadding(pixelRatio) * 2),
            textBelowLine
                ? y - indicatorTextBackgroundHeight(pixelRatio)
                : y + indicatorTextBackgroundHeight(pixelRatio),
            trackMeterTextWidth + indicatorTextPadding(pixelRatio) * 2,
            indicatorTextBackgroundHeight(pixelRatio),
        );

        //Dashed line
        ctx.beginPath();
        ctx.lineWidth = indicatorDashedLineWidth(pixelRatio);
        ctx.strokeStyle = BLACK;
        ctx.setLineDash(indicatorLineDash);
        ctx.moveTo(x, y);
        ctx.lineTo(nameTextXEndPosition, y);
        ctx.stroke();

        ctx.fillStyle = BLACK;
        ctx.textAlign = positiveXOffset ? 'left' : 'right';
        ctx.textBaseline = 'bottom';
        ctx.fillText(
            nameText,
            nameTextXEndPosition - indicatorTextPadding(pixelRatio) * (positiveXOffset ? -1 : 1),
            nameTextYEndPosition,
        );
        ctx.fillText(
            trackMeterText,
            trackMeterTextXEndPosition -
                indicatorTextPadding(pixelRatio) * (positiveXOffset ? -1 : 1),
            trackMeterTextYEndPosition,
        );

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer }));
    return feature;
}

const calculateZIndexForTrackMeter = (trackMeter: TrackMeter): number =>
    Math.floor(parseInt(trackMeter.kmNumber.substring(0, 4)) * 10000 + trackMeter.meters);

function createFeatures(
    alignments: AlignmentDataHolder[],
    duplicates: LocationTrackDuplicate[],
    startsAndEnds: AlignmentStartAndEnd[],
): Feature<OlPoint>[] {
    return alignments
        .filter(
            (alignment) =>
                duplicates.map((d) => d.id).includes(brand(alignment.header.id)) &&
                alignment.points.length >= 2,
        )
        .flatMap(({ points, header }) => {
            const startAndEnd = startsAndEnds.find(
                (idToStartAndEnd) => idToStartAndEnd.id === header.id,
            );

            // The 'points'-array may only include a subset of the actual points of the alignment.
            // Features should only be rendered to the real ends of a given duplicate.
            const [startPoint, startControlPoint] = [points[0], points[1]];
            const startFeature =
                startPoint &&
                startControlPoint &&
                trackMetersAreApproximatelySame(startPoint.m, startAndEnd?.start?.point.m)
                    ? createDuplicateTrackEndpointAddressFeature(
                          startPoint,
                          startControlPoint,
                          header.name,
                          startAndEnd?.start?.address,
                          'START',
                          startAndEnd?.start?.address
                              ? calculateZIndexForTrackMeter(startAndEnd.start.address)
                              : 0,
                      )
                    : undefined;

            const [endPoint, endControlPoint] = [
                points[points.length - 1],
                points[points.length - 2],
            ];
            const endFeature =
                endPoint &&
                endControlPoint &&
                trackMetersAreApproximatelySame(endPoint.m, startAndEnd?.end?.point.m)
                    ? createDuplicateTrackEndpointAddressFeature(
                          endPoint,
                          endControlPoint,
                          header.name,
                          startAndEnd?.end?.address,
                          'END',
                          startAndEnd?.end?.address
                              ? calculateZIndexForTrackMeter(startAndEnd.end.address)
                              : 0,
                      )
                    : undefined;

            return [startFeature, endFeature];
        })
        .filter((feature): feature is Feature<OlPoint> => feature !== undefined)
        .flat();
}

type DuplicateTrackEndpointAddressData = {
    duplicates: LocationTrackDuplicate[];
    startsAndEnds: AlignmentStartAndEnd[];
    alignments: AlignmentDataHolder[];
};

async function getData(
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    splittingState: SplittingState | undefined,
): Promise<DuplicateTrackEndpointAddressData> {
    if (resolution <= HIGHLIGHTS_SHOW && splittingState) {
        const [alignments, extras] = await Promise.all([
            getMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext),
            getLocationTrackInfoboxExtras(
                splittingState.originLocationTrack.id,
                layoutContext,
                changeTimes,
            ),
        ]);
        const duplicates = extras?.duplicates || [];
        const startsAndEnds = await getManyStartsAndEnds(
            duplicates.map((duplicate) => duplicate.id),
            layoutContext,
            changeTimes.layoutLocationTrack,
        );
        return { duplicates, startsAndEnds, alignments };
    } else {
        return { duplicates: [], startsAndEnds: [], alignments: [] };
    }
}

const layerName: MapLayerName = 'location-track-duplicate-endpoint-address-layer';

export function createDuplicateTrackEndpointAddressLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    splittingState: SplittingState | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise = getData(mapTiles, layoutContext, changeTimes, resolution, splittingState);

    const createOlFeatures = (data: DuplicateTrackEndpointAddressData) =>
        createFeatures(data.alignments, data.duplicates, data.startsAndEnds);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createOlFeatures);

    return { name: layerName, layer: layer };
}
