import { MapTile } from 'map/map-model';
import { PublishType, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
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

type EndpointType = 'START' | 'END';
const BLACK = '#000000';

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

    const renderer = ([x, y]: Coordinate, { pixelRatio, context }: State) => {
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
                duplicates.map((d) => d.id).includes(alignment.header.id) &&
                alignment.points.length >= 2,
        )
        .flatMap(({ points, header }) => {
            const startAndEnd = startsAndEnds.find(
                (idToStartAndEnd) => idToStartAndEnd.id === header.id,
            );
            const startFeature = createDuplicateTrackEndpointAddressFeature(
                points[0],
                points[1],
                header.name,
                startAndEnd?.start?.address,
                'START',
                startAndEnd?.start?.address
                    ? calculateZIndexForTrackMeter(startAndEnd.start.address)
                    : 0,
            );
            const endFeature = createDuplicateTrackEndpointAddressFeature(
                points[points.length - 1],
                points[points.length - 2],
                header.name,
                startAndEnd?.end?.address,
                'END',
                startAndEnd?.end?.address
                    ? calculateZIndexForTrackMeter(startAndEnd.end.address)
                    : 0,
            );

            return [startFeature, endFeature];
        })
        .flat();
}

let newestLayerId = 0;

export function createDuplicateTrackEndpointAddressLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    splittingState: SplittingState | undefined,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= HIGHLIGHTS_SHOW && splittingState) {
        inFlight = true;

        getLocationTrackInfoboxExtras(splittingState.originLocationTrack.id, publishType).then(
            (extras) =>
                getManyStartsAndEnds(
                    extras?.duplicates.map((duplicate) => duplicate.id) || [],
                    publishType,
                    changeTimes.layoutLocationTrack,
                ).then((startsAndEnds) =>
                    getMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
                        .then((alignments) => {
                            if (layerId === newestLayerId) {
                                const features = createFeatures(
                                    alignments,
                                    extras?.duplicates || [],
                                    startsAndEnds,
                                );

                                clearFeatures(vectorSource);
                                vectorSource.addFeatures(features);
                            }
                        })
                        .catch(() => {
                            if (layerId === newestLayerId) clearFeatures(vectorSource);
                        })
                        .finally(() => {
                            inFlight = false;
                        }),
                ),
        );
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-duplicate-endpoint-address-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
