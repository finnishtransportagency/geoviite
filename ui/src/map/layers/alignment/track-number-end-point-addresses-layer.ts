import { Point } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile, TrackNumberDiagramLayerSetting } from 'map/map-model';
import {
    AlignmentDataHolder,
    getMapAlignmentsByTiles,
    getTrackMeter,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType, TimeStamp, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { Feature } from 'ol';
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

function getRotation(start: Coordinate, end: Coordinate) {
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
    const angle = Math.atan2(dy, dx);
    const halfPi = Math.PI / 2;

    let positiveXOffset = true;
    let positiveYOffset = true;
    let rotation: number;

    if (angle >= 0) {
        positiveYOffset = false;

        if (angle >= halfPi) {
            //2nd quadrant
            rotation = -angle + halfPi;
        } else {
            //1st quadrant
            rotation = Math.abs(angle - halfPi);
        }
    } else {
        positiveXOffset = false;

        if (angle <= -halfPi) {
            //3rd quadrant
            rotation = Math.abs(angle + halfPi);
        } else {
            //4th quadrant
            rotation = -angle - halfPi;
        }
    }

    return {
        rotation,
        positiveXOffset,
        positiveYOffset,
    };
}

function createFeature(
    point: LayoutPoint,
    controlPoint: LayoutPoint,
    address: TrackMeter,
    pointAtEnd: boolean,
    color: TrackNumberColor,
): Feature<Point> {
    const feature = new Feature({
        geometry: new Point(pointToCoords(pointAtEnd ? controlPoint : point)),
    });

    const { rotation, positiveXOffset, positiveYOffset } = getRotation(
        pointToCoords(point),
        pointToCoords(controlPoint),
    );

    const renderer = ([x, y]: Coordinate, { pixelRatio, context }: State) => {
        const fontSize = 12;
        const lineWidth = 120;
        const textPadding = 3 * pixelRatio;
        const lineDash = [12, 6];
        const textBackgroundHeight = (fontSize + 4) * pixelRatio;
        const dashedLineWidth = 1 * pixelRatio;

        const ctx = context;

        ctx.font = `${mapStyles['alignmentBadge-font-weight']} ${pixelRatio * fontSize}px ${
            mapStyles['alignmentBadge-font-family']
        }`;

        ctx.save();

        const text = formatTrackMeter(address);
        const textWidth = ctx.measureText(text).width;
        const xEndPosition = x + lineWidth * pixelRatio * (positiveXOffset ? 1 : -1);
        const yEndPosition = y + (positiveYOffset === pointAtEnd ? -1 : fontSize + 3) * pixelRatio;

        ctx.translate(x, y);
        ctx.rotate(rotation);
        ctx.translate(-x, -y);

        ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
        ctx.fillRect(
            xEndPosition - (positiveXOffset ? textWidth + textPadding * 2 : 0),
            positiveYOffset === pointAtEnd ? y - textBackgroundHeight : y,
            textWidth + textPadding * 2,
            textBackgroundHeight,
        );

        //Dashed line
        ctx.beginPath();
        ctx.lineWidth = dashedLineWidth;
        ctx.strokeStyle = color;
        ctx.setLineDash(lineDash);
        ctx.moveTo(x, y);
        ctx.lineTo(xEndPosition, y);
        ctx.stroke();

        ctx.fillStyle = color;
        ctx.textAlign = positiveXOffset ? 'right' : 'left';
        ctx.textBaseline = 'bottom';
        ctx.fillText(text, xEndPosition + textPadding * (positiveXOffset ? -1 : 1), yEndPosition);

        ctx.restore();
    };

    feature.setStyle(() => new Style({ renderer }));

    return feature;
}

function createFeatures(
    referenceLines: AlignmentDataHolderWithAddresses[],
    layerSettings: TrackNumberDiagramLayerSetting,
): Feature<Point>[] {
    return referenceLines.flatMap(({ data: referenceLine, startAddress, endAddress }) => {
        const trackNumberId = referenceLine.header.trackNumberId as LayoutTrackNumberId;
        const color = getColorForTrackNumber(trackNumberId, layerSettings);

        const features = [];
        const points = referenceLine.points;

        if (startAddress && color) {
            features.push(createFeature(points[0], points[1], startAddress, false, color));
        }

        if (endAddress && color) {
            const lastIndex = points.length - 1;

            features.push(
                createFeature(points[lastIndex - 1], points[lastIndex], endAddress, true, color),
            );
        }

        return features;
    });
}

export function createTrackNumberEndPointAddressesLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<Point>> | undefined,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    resolution: number,
    layerSettings: TrackNumberDiagramLayerSetting,
): MapLayer {
    const layerId = ++newestLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    if (resolution <= Limits.ALL_ALIGNMENTS) {
        getMapAlignmentsByTiles(changeTimes, mapTiles, publishType, 'REFERENCE_LINES')
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
            .then((alignments) => {
                if (layerId !== newestLayerId) return;

                const features = createFeatures(alignments, layerSettings);

                clearFeatures(vectorSource);
                vectorSource.addFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'track-number-addresses-layer',
        layer: layer,
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
