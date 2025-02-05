import mapStyles from 'map/map.module.scss';
import { LineString } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import { AlignmentHighlight, MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getAlignmentSectionsWithoutLinkingByTiles,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { getMaxTimestamp } from 'utils/date-utils';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { createHighlightFeatures } from 'map/layers/utils/highlight-layer-utils';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';

const highlightBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedHighlight,
        width: 12,
    }),
});

type MissingLinkingLayerData = {
    alignments: AlignmentDataHolder[];
    sections: AlignmentHighlight[];
};

const layerName: MapLayerName = 'missing-linking-highlight-layer';

export function createMissingLinkingHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const alignmentPromise: Promise<AlignmentDataHolder[]> =
        resolution <= HIGHLIGHTS_SHOW
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext)
            : Promise.resolve([]);
    const linkingStatusPromise: Promise<AlignmentHighlight[]> =
        resolution <= HIGHLIGHTS_SHOW
            ? getAlignmentSectionsWithoutLinkingByTiles(
                  getMaxTimestamp(changeTimes.layoutLocationTrack, changeTimes.layoutReferenceLine),
                  layoutContext,
                  'ALL',
                  mapTiles,
              )
            : Promise.resolve([]);
    const dataPromise: Promise<MissingLinkingLayerData> = Promise.all([
        alignmentPromise,
        linkingStatusPromise,
    ]).then(([alignments, sections]) => ({ alignments, sections }));

    const createFeatures = (data: MissingLinkingLayerData) =>
        createHighlightFeatures(data.alignments, data.sections, highlightBackgroundStyle);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
