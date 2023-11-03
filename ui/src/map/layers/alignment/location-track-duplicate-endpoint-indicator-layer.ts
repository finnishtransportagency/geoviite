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
import { getRotation } from 'map/layers/utils/dashed-line-indicator-utils';
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

function createDuplicateTrackEndpointAddressFeature(
    point: Point,
    controlPoint: Point,
    name: string,
    trackMeter: TrackMeter | undefined,
    endpointType: EndpointType,
): Feature<OlPoint> {
    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(point)),
    });

    const { rotation, positiveXOffset } =
        endpointType === 'START'
            ? getRotation(pointToCoords(point), pointToCoords(controlPoint))
            : getRotation(pointToCoords(controlPoint), pointToCoords(point));

    const renderer = ([x, y]: Coordinate, { pixelRatio, context }: State) => {
        const fontSize = 12;
        const lineWidth = 120 * pixelRatio;
        const textPadding = 3 * pixelRatio;
        const lineDash = [12, 6];
        const textBackgroundHeight = (fontSize + 4) * pixelRatio;
        const dashedLineWidth = 1 * pixelRatio;

        const ctx = context;

        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${pixelRatio * fontSize}px ${
            mapStyles['alignmentBadge-font-family']
        }`;

        ctx.save();
        const textPositionOffset = endpointType === 'START' ? 0 : 2 * textBackgroundHeight;

        const nameText = name;
        const nameTextWidth = ctx.measureText(nameText).width;
        const nameTextXEndPosition = x - lineWidth * (positiveXOffset ? 1 : -1);
        const lineHeight = (fontSize + 3) * pixelRatio;
        const nameTextYEndPosition = y - lineHeight + textPositionOffset;

        const trackMeterText = trackMeter ? formatTrackMeter(trackMeter) : '';
        const trackMeterTextWidth = ctx.measureText(trackMeterText).width;
        const trackMeterTextXEndPosition = x - lineWidth * (positiveXOffset ? 1 : -1);
        const trackMeterTextYEndPosition =
            y - lineHeight + textBackgroundHeight + textPositionOffset;

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
        ctx.fillRect(
            nameTextXEndPosition,
            endpointType === 'START' ? y - textBackgroundHeight * 2 : y,
            nameTextWidth + textPadding * 2,
            textBackgroundHeight,
        );
        ctx.fillRect(
            trackMeterTextXEndPosition,
            endpointType === 'START' ? y - textBackgroundHeight : y + textBackgroundHeight,
            trackMeterTextWidth + textPadding * 2,
            textBackgroundHeight,
        );

        //Dashed line
        ctx.beginPath();
        ctx.lineWidth = dashedLineWidth;
        ctx.strokeStyle = '#000000';
        ctx.setLineDash(lineDash);
        ctx.moveTo(x, y);
        ctx.lineTo(nameTextXEndPosition, y);
        ctx.stroke();

        ctx.fillStyle = '#000000';
        ctx.textAlign = positiveXOffset ? 'left' : 'right';
        ctx.textBaseline = 'bottom';
        ctx.fillText(
            nameText,
            nameTextXEndPosition - textPadding * (positiveXOffset ? -1 : 1),
            nameTextYEndPosition,
        );
        ctx.fillText(
            trackMeterText,
            trackMeterTextXEndPosition - textPadding * (positiveXOffset ? -1 : 1),
            trackMeterTextYEndPosition,
        );

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer }));
    return feature;
}

function createFeatures(
    alignments: AlignmentDataHolder[],
    duplicates: LocationTrackDuplicate[],
    startsAndEnds: AlignmentStartAndEnd[],
): Feature<OlPoint>[] {
    return alignments
        .filter((alignment) => duplicates.map((d) => d.id).includes(alignment.header.id))
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
            );
            const endFeature = createDuplicateTrackEndpointAddressFeature(
                points[points.length - 1],
                points[points.length - 2],
                header.name,
                startAndEnd?.end?.address,
                'END',
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
