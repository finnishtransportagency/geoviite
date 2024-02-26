import mapStyles from 'map/map.module.scss';
import { LineString } from 'ol/geom';
import { Stroke, Style } from 'ol/style';
import { AlignmentHighlight, MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
    getLocationTrackSectionsWithoutProfileByTiles,
} from 'track-layout/layout-map-api';
import { MapLayer } from 'map/layers/utils/layer-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { createHighlightFeatures } from 'map/layers/utils/highlight-layer-utils';
import { createLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';

const highlightBackgroundStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentRedHighlight,
        width: 12,
    }),
});

const layerName: MapLayerName = 'missing-profile-highlight-layer';

export function createMissingProfileHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<VectorSource<LineString>> | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<[AlignmentDataHolder[], AlignmentHighlight[]]> =
        resolution <= HIGHLIGHTS_SHOW
            ? Promise.all([
                  getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, publishType),
                  getLocationTrackSectionsWithoutProfileByTiles(
                      changeTimes.layoutLocationTrack,
                      publishType,
                      mapTiles,
                  ),
              ])
            : Promise.resolve([[], []]);

    const createFeatures = ([locationTracks, sections]: [
        AlignmentDataHolder[],
        AlignmentHighlight[],
    ]) => createHighlightFeatures(locationTracks, sections, highlightBackgroundStyle);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
