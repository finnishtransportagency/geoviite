import { LineString } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import { createAlignmentBackgroundFeatures } from 'map/layers/utils/background-layer-utils';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import {
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LayoutContext } from 'common/common-model';
import OlView from 'ol/View';
import * as Limits from 'map/layers/utils/layer-visibility-limits';

const layerName: MapLayerName = 'location-track-background-layer';

export function createLocationTrackBackgroundLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    olView: OlView,
    isSplitting: boolean,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const alignmentPromise: Promise<AlignmentDataHolder[]> = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const resolution = olView.getResolution() || 0;

    const createFeatures = (locationTracks: AlignmentDataHolder[]) => {
        const tracksToRender =
            resolution <= Limits.SHOW_VERY_SHORT_TRACKS_MIN_RESOLUTION
                ? locationTracks
                : locationTracks.filter((track) => track.points.length > 2);

        return createAlignmentBackgroundFeatures(tracksToRender);
    };

    loadLayerData(source, isLatest, onLoadingData, alignmentPromise, createFeatures);

    return { name: layerName, layer: layer };
}
