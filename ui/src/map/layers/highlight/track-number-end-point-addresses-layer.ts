import { Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile, TrackNumberDiagramLayerSetting } from 'map/map-model';
import {
    AlignmentDataHolder,
    getReferenceLineMapAlignmentsByTiles,
    getTrackMeter,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LayoutContext, TimeStamp, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import Feature from 'ol/Feature';
import { Style } from 'ol/style';
import { AlignmentPoint, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import {
    getColor,
    getDefaultColorKey,
    TrackNumberColor,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import { formatTrackMeter } from 'utils/geography-utils';
import { Coordinate } from 'ol/coordinate';
import mapStyles from 'map/map.module.scss';
import { State } from 'ol/render';
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
import { expectCoordinate } from 'utils/type-utils';
import { first, last } from 'utils/array-utils';

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
    point: AlignmentPoint,
    controlPoint: AlignmentPoint,
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

    const renderer = (coord: Coordinate, { pixelRatio, context }: State) => {
        const [x, y] = expectCoordinate(coord);
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
        const [startPoint, startControlPoint] = [points[0], points[1]];

        if (startAddress && startPoint && startControlPoint && color) {
            features.push(
                createAddressFeature(startPoint, startControlPoint, startAddress, false, color),
            );
        }

        const [endPoint, endControlPoint] = [points[points.length - 1], points[points.length - 2]];
        if (endAddress && endPoint && endControlPoint && color) {
            const f = createAddressFeature(endPoint, endControlPoint, endAddress, true, color);

            features.push(f);
        }

        return features;
    });
}

const layerName: MapLayerName = 'track-number-addresses-layer';

export function createTrackNumberEndPointAddressesLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<OlPoint>> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    resolution: number,
    layerSettings: TrackNumberDiagramLayerSetting,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentDataHolderWithAddresses[]> =
        resolution <= Limits.TRACK_NUMBER_DIAGRAM_ENDPOINT_MAX_RESOLUTION
            ? getTrackNumberEndPointData(mapTiles, changeTimes, layoutContext, layerSettings)
            : Promise.resolve([]);

    const createFeatures = (referenceLines: AlignmentDataHolderWithAddresses[]) =>
        createAddressFeatures(referenceLines, layerSettings);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}

async function getTrackNumberEndPointData(
    mapTiles: MapTile[],
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    layerSettings: TrackNumberDiagramLayerSetting,
): Promise<AlignmentDataHolderWithAddresses[]> {
    const referenceLines = await getReferenceLineMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const showAll = Object.values(layerSettings).every((s) => !s.selected);
    const shownReferenceLines = showAll
        ? referenceLines
        : referenceLines.filter((r) => {
              const trackNumberId = r.trackNumber?.id;
              return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
          });

    const filteredReferenceLines = shownReferenceLines.filter((r) => {
        const trackNumberId = r.trackNumber?.id;
        return trackNumberId
            ? layerSettings[trackNumberId]?.color !== TrackNumberColor.TRANSPARENT
            : false;
    });

    return getEndPointAddresses(
        filteredReferenceLines,
        layoutContext,
        changeTimes.layoutTrackNumber,
    );
}

const getEndPointAddresses = (
    referenceLines: AlignmentDataHolder[],
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<AlignmentDataHolderWithAddresses[]> => {
    return Promise.all(
        referenceLines
            .filter((referenceLine) => !!referenceLine.header.trackNumberId)
            .flatMap((referenceLine) => {
                const trackNumberId = referenceLine.header.trackNumberId as LayoutTrackNumberId;

                const firstPoint = first(referenceLine.points);
                const lastPoint = last(referenceLine.points);

                return Promise.all([
                    firstPoint?.m === 0
                        ? getTrackMeter(trackNumberId, layoutContext, changeTime, firstPoint)
                        : undefined,
                    lastPoint?.m === referenceLine.header.length
                        ? getTrackMeter(trackNumberId, layoutContext, changeTime, lastPoint)
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
