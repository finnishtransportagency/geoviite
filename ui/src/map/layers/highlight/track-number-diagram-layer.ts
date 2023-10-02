import { LineString } from 'ol/geom';
import { MapTile, TrackNumberDiagramLayerSetting } from 'map/map-model';
import {
    AlignmentDataHolder,
    getMapAlignmentsByTiles,
    getReferenceLineMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { groupBy } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import Feature from 'ol/Feature';
import { Stroke, Style } from 'ol/style';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import {
    getColor,
    getDefaultColorKey,
    TrackNumberColor,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';

let newestLayerId = 0;

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

    return Object.entries(perTrackNumber).flatMap(([trackNumberId, alignments]) => {
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

export function createTrackNumberDiagramLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    changeTimes: ChangeTimes,
    publishType: PublishType,
    resolution: number,
    layerSettings: TrackNumberDiagramLayerSetting,
): MapLayer {
    const layerId = ++newestLayerId;
    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    const alignmentPromise =
        resolution > Limits.ALL_ALIGNMENTS
            ? getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, publishType)
            : getMapAlignmentsByTiles(changeTimes, mapTiles, publishType);

    let inFlight = true;
    alignmentPromise
        .then((alignments) => {
            if (layerId !== newestLayerId) return;

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

            const features = createDiagramFeatures(alignmentsWithColor, layerSettings);

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
        })
        .catch(() => {
            if (layerId === newestLayerId) clearFeatures(vectorSource);
        })
        .finally(() => {
            inFlight = false;
        });

    return {
        name: 'track-number-diagram-layer',
        layer: layer,
        requestInFlight: () => inFlight,
    };
}
