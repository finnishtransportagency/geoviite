import { LineString } from 'ol/geom';
import { MapLayerName, MapTile, TrackNumberDiagramLayerSetting } from 'map/map-model';
import { AlignmentDataHolder, getMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy, objectEntries } from 'utils/array-utils';
import Feature from 'ol/Feature';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import {
    getColor,
    getDefaultColorKey,
    TrackNumberColor,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';

const getColorForTrackNumber = (
    id: LayoutTrackNumberId,
    layerSettings: TrackNumberDiagramLayerSetting,
) => {
    //Track numbers with transparent color are already filtered out
    const selectedColor = layerSettings[id]?.color ?? getDefaultColorKey(id);
    return getColor(selectedColor) + '55'; //~33 % opacity in hex
};

function createDiagramFeatures(
    alignments: AlignmentDataHolder[],
    layerSettings: TrackNumberDiagramLayerSetting,
): Feature<LineString>[] {
    const perTrackNumber = groupBy(
        alignments,
        (a) => a.header.trackNumberId || a.trackNumber?.id || '',
    );

    return objectEntries(perTrackNumber).flatMap(([trackNumberId, alignments]) => {
        if (trackNumberId === '') {
            return [];
        }
        const style = new Style({
            stroke: new Stroke({
                color: getColorForTrackNumber(trackNumberId, layerSettings),
                width: 15,
                lineCap: 'butt',
            }),
        });

        return alignments.map(({ points }) => {
            const feature = new Feature({
                geometry: new LineString(points.map(pointToCoords)),
            });

            feature.setStyle(style);

            return feature;
        });
    });
}

const layerName: MapLayerName = 'track-number-diagram-layer';

export function createTrackNumberDiagramLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    layerSettings: TrackNumberDiagramLayerSetting,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const alignmentPromise: Promise<AlignmentDataHolder[]> = getMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const createFeatures = (alignments: AlignmentDataHolder[]) => {
        const showAll = Object.values(layerSettings).every((s) => !s.selected);
        const filteredAlignments = showAll
            ? alignments
            : alignments.filter((a) => {
                  const trackNumberId = a.trackNumber?.id;
                  return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
              });

        const alignmentsWithColor = filteredAlignments.filter((a) => {
            const trackNumberId = a.trackNumber?.id;
            return trackNumberId
                ? layerSettings[trackNumberId]?.color !== TrackNumberColor.TRANSPARENT
                : false;
        });

        return createDiagramFeatures(alignmentsWithColor, layerSettings);
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return {
        name: layerName,
        layer: layer,
    };
}
