import { Point as OlPoint } from 'ol/geom';
import { MapTile, TrackNumberDiagramLayerSetting } from 'map/map-model';
import {
    AlignmentDataHolder,
    getReferenceLineMapAlignmentsByTiles,
    getTrackMeter,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { LayoutPoint, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import {
    getColor,
    getDefaultColorKey,
    TrackNumberColor,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import { formatTrackMeter } from 'utils/geography-utils';
import { Coordinate } from 'ol/coordinate';
import mapStyles from 'map/map.module.scss';
import { State } from 'ol/render';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import {
    DASHED_LINE_INDICATOR_FONT_SIZE,
    getRotation,
    indicatorDashedLineWidth,
    indicatorLineDash,
    indicatorLineWidth,
    indicatorTextBackgroundHeight,
    indicatorTextPadding,
} from 'map/layers/utils/dashed-line-indicator-utils';

let newestLayerId = 0;

type AlignmentDataHolderWithAddresses = {
    data: AlignmentDataHolder;
    startAddress?: TrackMeter;
    endAddress?: TrackMeter;
};

const getColorForTrackNumber = (
    id: LayoutTrackNumberId,
    layerSettings: TrackNumberDiagramLayerSetting,
) => {
    //Track numbers with transparent color are already filtered out
    const selectedColor = layerSettings[id]?.color ?? getDefaultColorKey(id);
    return getColor(selectedColor);
};

function createAddressFeature(
    point: LayoutPoint,
    controlPoint: LayoutPoint,
    address: TrackMeter,
    pointAtEnd: boolean,
    color: TrackNumberColor,
): Feature<OlPoint> {
    const feature = new Feature({
        geometry: new OlPoint(pointToCoords(pointAtEnd ? controlPoint : point)),
    });

    const { rotation, positiveXOffset, positiveYOffset } = getRotation(
        pointToCoords(point),
        pointToCoords(controlPoint),
    );

    const renderer = ([x, y]: Coordinate, { pixelRatio, context }: State) => {
        const ctx = context;

        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${
            pixelRatio * DASHED_LINE_INDICATOR_FONT_SIZE
        }px ${mapStyles['alignmentBadge-font-family']}`;

        ctx.save();

        const text = formatTrackMeter(address);
        const textWidth = ctx.measureText(text).width;
        const xEndPosition = x + indicatorLineWidth(pixelRatio) * (positiveXOffset ? 1 : -1);
        const yEndPosition =
            y +
            (positiveYOffset === pointAtEnd ? -1 : DASHED_LINE_INDICATOR_FONT_SIZE + 3) *
                pixelRatio;

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
        ctx.fillRect(
            xEndPosition - (positiveXOffset ? textWidth + indicatorTextPadding(pixelRatio) * 2 : 0),
            positiveYOffset === pointAtEnd ? y - indicatorTextBackgroundHeight(pixelRatio) : y,
            textWidth + indicatorTextPadding(pixelRatio) * 2,
            indicatorTextBackgroundHeight(pixelRatio),
        );

        //Dashed line
        ctx.beginPath();
        ctx.lineWidth = indicatorDashedLineWidth(pixelRatio);
        ctx.strokeStyle = color;
        ctx.setLineDash(indicatorLineDash);
        ctx.moveTo(x, y);
        ctx.lineTo(xEndPosition, y);
        ctx.stroke();

        ctx.fillStyle = color;
        ctx.textAlign = positiveXOffset ? 'right' : 'left';
        ctx.textBaseline = 'bottom';
        ctx.fillText(
            text,
            xEndPosition + indicatorTextPadding(pixelRatio) * (positiveXOffset ? -1 : 1),
            yEndPosition,
        );

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer }));

    return feature;
}

function createAddressFeatures(
    referenceLines: AlignmentDataHolderWithAddresses[],
    layerSettings: TrackNumberDiagramLayerSetting,
): Feature<OlPoint>[] {
    return referenceLines.flatMap(({ data: referenceLine, startAddress, endAddress }) => {
        const trackNumberId = referenceLine.header.trackNumberId as LayoutTrackNumberId;
        const color = getColorForTrackNumber(trackNumberId, layerSettings);

        const features: Feature<OlPoint>[] = [];
        const points = referenceLine.points;

        if (startAddress && color) {
            features.push(createAddressFeature(points[0], points[1], startAddress, false, color));
        }

        if (endAddress && color) {
            const lastIndex = points.length - 1;
            const f = createAddressFeature(
                points[lastIndex - 1],
                points[lastIndex],
                endAddress,
                true,
                color,
            );

            features.push(f);
        }

        return features;
    });
}

export function createTrackNumberEndPointAddressesLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    resolution: number,
    layerSettings: TrackNumberDiagramLayerSetting,
): MapLayer {
    const layerId = ++newestLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    let inFlight = false;
    if (resolution <= Limits.ALL_ALIGNMENTS) {
        inFlight = true;
        getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            .then((referenceLines) => {
                const showAll = Object.values(layerSettings).every((s) => !s.selected);
                const filteredReferenceLines = showAll
                    ? referenceLines
                    : referenceLines.filter((r) => {
                          const trackNumberId = r.trackNumber?.id;
                          return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
                      });

                return filteredReferenceLines.filter((r) => {
                    const trackNumberId = r.trackNumber?.id;
                    return trackNumberId
                        ? layerSettings[trackNumberId]?.color !== TrackNumberColor.TRANSPARENT
                        : false;
                });
            })
            .then((referenceLines) =>
                getEndPointAddresses(referenceLines, publishType, changeTimes.layoutTrackNumber),
            )
            .then((referenceLines) => {
                if (layerId === newestLayerId) {
                    const features = createAddressFeatures(referenceLines, layerSettings);

                    clearFeatures(vectorSource);
                    vectorSource.addFeatures(features);
                }
            })
            .catch(() => {
                if (layerId === newestLayerId) clearFeatures(vectorSource);
            })
            .finally(() => {
                inFlight = false;
            });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'track-number-addresses-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}

const getEndPointAddresses = (
    referenceLines: AlignmentDataHolder[],
    publishType: PublishType,
    changeTime: TimeStamp,
): Promise<AlignmentDataHolderWithAddresses[]> => {
    return Promise.all(
        referenceLines
            .filter((referenceLine) => !!referenceLine.header.trackNumberId)
            .flatMap((referenceLine) => {
                const trackNumberId = referenceLine.header.trackNumberId as LayoutTrackNumberId;

                const firstPoint = referenceLine.points[0];
                const lastPoint = referenceLine.points[referenceLine.points.length - 1];

                return Promise.all([
                    firstPoint?.m === 0
                        ? getTrackMeter(trackNumberId, publishType, changeTime, firstPoint)
                        : undefined,
                    lastPoint?.m === referenceLine.header.length
                        ? getTrackMeter(trackNumberId, publishType, changeTime, lastPoint)
                        : undefined,
                ]).then(([startTrackMeter, endTrackMeter]) => {
                    return {
                        data: referenceLine,
                        startAddress: startTrackMeter,
                        endAddress: endTrackMeter,
                    } as AlignmentDataHolderWithAddresses;
                });
            }),
    );
};
