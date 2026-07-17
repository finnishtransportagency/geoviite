import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { MapLayerName } from 'map/map-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ExtendingAlignment } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { AlignmentStartAndEnd } from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import {
    endLocation,
    getAlignmentStartAndEnd,
} from 'map/layers/utils/alignment-extension-layer-utils';
import { createEndPointTick, getEndPointTickStyle } from 'map/layers/utils/alignment-layer-utils';

const layerName: MapLayerName = 'alignment-extension-layer';

const extensionLineStroke = new Stroke({
    color: mapStyles.alignmentExtensionLine,
    width: 2,
    lineDash: [8, 6],
});

const extensionLineStyle = new Style({ stroke: extensionLineStroke });

const extensionTickStyle = new Style({
    stroke: new Stroke({ color: mapStyles.alignmentExtensionLine, width: 2 }),
});

const createExtensionLineFeature = (from: Point, to: Point): Feature<LineString> => {
    const feature = new Feature({
        geometry: new LineString([pointToCoords(from), pointToCoords(to)]),
    });
    feature.setStyle(extensionLineStyle);
    return feature;
};

export const extensionSketchStyle = (from: Point, to: Point): Style[] => [
    new Style({
        geometry: new LineString([pointToCoords(from), pointToCoords(to)]),
        stroke: extensionLineStroke,
    }),
    getEndPointTickStyle(pointToCoords(from), pointToCoords(to), 'end', extensionTickStyle),
];

/**
 * Renders the extension line once it has been placed. While drawing, it's drawn by the Draw interaction kept by the
 * tool.
 */
export const createAlignmentExtensionLayer = (
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    linkingState: ExtendingAlignment | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentStartAndEnd | undefined> =
        linkingState === undefined
            ? Promise.resolve(undefined)
            : getAlignmentStartAndEnd(linkingState.alignment, layoutContext, changeTimes);

    const createFeatures = (
        startAndEnd: AlignmentStartAndEnd | undefined,
    ): Feature<LineString | OlPoint>[] => {
        const extension = linkingState?.extension;
        if (extension === undefined) {
            return [];
        }
        const from = endLocation(startAndEnd, extension.end);
        if (from === undefined) {
            return [];
        }
        const to = extension.location;
        return [
            createExtensionLineFeature(from, to),
            createEndPointTick(pointToCoords(from), pointToCoords(to), 'end', extensionTickStyle),
        ];
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer };
};
