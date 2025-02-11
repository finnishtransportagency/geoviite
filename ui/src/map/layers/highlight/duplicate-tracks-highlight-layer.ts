import Feature from 'ol/Feature';
import { LineString } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    AlignmentDataHolder,
    getLocationTrackMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { redHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import { LayoutContext } from 'common/common-model';

function createHighlightFeatures(locationTracks: AlignmentDataHolder[]): Feature<LineString>[] {
    return locationTracks
        .filter((lt) => lt.header.duplicateOf)
        .flatMap(({ points }) => {
            const feature = new Feature({ geometry: new LineString(points.map(pointToCoords)) });

            feature.setStyle(redHighlightStyle);

            return feature;
        });
}

const layerName: MapLayerName = 'duplicate-tracks-highlight-layer';

export function createDuplicateTracksHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentDataHolder[]> =
        resolution <= HIGHLIGHTS_SHOW
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext)
            : Promise.resolve([]);

    const createFeatures = (locationTracks: AlignmentDataHolder[]) =>
        createHighlightFeatures(locationTracks);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
}
