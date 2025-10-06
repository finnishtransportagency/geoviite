import Feature from 'ol/Feature';
import OlView from 'ol/View';
import { LineString } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { Stroke } from 'ol/style';
import { LayoutContext } from 'common/common-model';
import { getProjectionLines, ProjectionLine } from 'common/geocoding-api';
import { DEBUG_PROJECTION_LINES } from '../utils/layer-visibility-limits';
import { MapLayer } from 'map/layers/utils/layer-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { first } from 'utils/array-utils';
import { MapLayerName } from 'map/map-model';
import { formatTrackMeter } from 'utils/geography-utils';
import { ChangeTimes } from 'common/common-slice';
import { getMaxTimestamp } from 'utils/date-utils';
import { coordsToPoint, Line } from 'model/geometry';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { expectDefined } from 'utils/type-utils';

function extrapolateProjectionLine(line: Line, extrapolateToMeters: number): Line {
    const dx = line.end.x - line.start.x;
    const dy = line.end.y - line.start.y;
    const length = Math.sqrt(dx * dx + dy * dy);
    const ex = (dx / length) * extrapolateToMeters;
    const ey = (dy / length) * extrapolateToMeters;

    return {
        start: { x: line.start.x - ex, y: line.start.y - ey },
        end: { x: line.start.x + ex, y: line.start.y + ey },
    };
}

const PROJECTION_LINE_EXTRAPOLATION_LENGTH = 1000;

function createProjectionLineFeatures(
    data: ProjectionLine[],
    olView: OlView,
): Feature<LineString>[] {
    const style = {
        stroke: new Stroke({
            color: '#0000FF',
            lineDash: [1, 20],
            width: 5,
        }),
    };
    // extremely ad-hoc optimization to make long reference lines destroy Firefox less completely: Avoid rendering
    // distant projection lines
    const viewCenter = olView.getCenter();
    if (viewCenter === undefined) {
        return [];
    }
    const centerPoint = coordsToPoint(viewCenter);
    const dataToRender: ProjectionLine[] = [];
    for (let i = 0; i < data.length; i += 1000) {
        const point = expectDefined(data[i]).projection.start;
        const dx = point.x - centerPoint.x,
            dy = point.y - centerPoint.y;
        if (
            dx * dx + dy * dy <
            PROJECTION_LINE_EXTRAPOLATION_LENGTH * PROJECTION_LINE_EXTRAPOLATION_LENGTH * 4
        ) {
            dataToRender.push(...data.slice(i, i + 1000));
        }
    }

    return dataToRender.map((projectionLine) => {
        const line = extrapolateProjectionLine(
            projectionLine.projection,
            PROJECTION_LINE_EXTRAPOLATION_LENGTH,
        );
        return new Feature({
            geometry: new LineString([pointToCoords(line.start), pointToCoords(line.end)]),
            text: formatTrackMeter(projectionLine.address),
            style,
        });
    });
}

const layerName: MapLayerName = 'debug-projection-lines-layer';

async function getDataPromise(
    selection: Selection,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<ProjectionLine[] | undefined> {
    const selectedLocationTrack = first(selection.selectedItems.locationTracks);

    const trackNumberId =
        first(selection.selectedItems.trackNumbers) ??
        (
            selectedLocationTrack &&
            (await getLocationTrack(
                selectedLocationTrack,
                layoutContext,
                changeTimes.layoutLocationTrack,
            ))
        )?.trackNumberId;

    return (
        trackNumberId &&
        getProjectionLines(
            trackNumberId,
            layoutContext,
            getMaxTimestamp(changeTimes.layoutReferenceLine, changeTimes.layoutKmPost),
        )
    );
}

export function createDebugProjectionLinesLayer(
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    selection: Selection,
    layoutContext: LayoutContext,
    resolution: number,
    changeTimes: ChangeTimes,
    olView: OlView,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise =
        resolution > DEBUG_PROJECTION_LINES
            ? Promise.resolve(undefined)
            : getDataPromise(selection, layoutContext, changeTimes);

    const createFeatures = (projectionLines: ProjectionLine[] | undefined) =>
        projectionLines ? createProjectionLineFeatures(projectionLines, olView) : [];
    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
