import Feature from 'ol/Feature';
import { Point as OlPoint, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LocationTrackEndpoint } from 'linking/linking-model';
import { MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION } from 'map/layers/utils/layer-visibility-limits';
import { flatten } from 'utils/array-utils';
import { getMatchingEntities, MatchOptions, pointToCoords } from 'map/layers/utils/layer-utils';
import { endPointStyle } from 'map/layers/linking-layer';
import { PublishType } from 'common/common-model';
import { getLocationTrackEndpointsByTile } from 'track-layout/layout-location-track-api';

export const FEATURE_PROPERTY_LOCATION_TRACK_ENDPOINT = 'location-track-endpoint';

function createLocationTrackEndpointFeatures(
    locationTrackEndpoint: LocationTrackEndpoint,
    isSelected: boolean,
): Feature<OlPoint>[] {
    const features: Feature<OlPoint>[] = [];

    if (!isSelected) {
        const f = new Feature({
            geometry: new OlPoint(pointToCoords(locationTrackEndpoint.location)),
        });
        f.setStyle(endPointStyle);
        f.set(FEATURE_PROPERTY_LOCATION_TRACK_ENDPOINT, locationTrackEndpoint);
        features.push(f);
    }

    return features;
}

let newestSwitchManualLinkingLayerId = 0;
export function createManualSwitchLinkingLayer(
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    publishType: PublishType,
): MapLayer {
    const layerId = ++newestSwitchManualLinkingLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();

    // Use an existing layer or create a new one. Old layer is "recycled" to
    // prevent features to disappear while moving the map.
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function clearFeatures() {
        vectorSource.clear();
    }

    function updateFeatures(features: Feature<OlPoint>[]) {
        clearFeatures();
        vectorSource.addFeatures(features);
    }

    if (resolution && resolution < MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION) {
        const locationTrackEndpointsPromise = Promise.all(
            mapTiles.map((tile) => getLocationTrackEndpointsByTile(tile, publishType)),
        ).then(flatten);

        locationTrackEndpointsPromise
            .then((locationTrackEndPoints) => {
                return locationTrackEndPoints.flatMap((m) =>
                    createLocationTrackEndpointFeatures(m, false),
                );
            })
            .then((features) => {
                // Handle latest fetch only
                if (layerId === newestSwitchManualLinkingLayerId) {
                    updateFeatures(features);
                }
            })
            .catch(clearFeatures);
    } else {
        clearFeatures();
    }

    return {
        name: 'manual-linking-switch-layer',
        layer: layer,
        searchItems: (hitArea: Polygon, options: SearchItemsOptions): LayerItemSearchResult => {
            const matchOptions: MatchOptions = {
                strategy: options.limit == 1 ? 'nearest' : 'limit',
                limit: options.limit,
            };
            const features = vectorSource.getFeaturesInExtent(hitArea.getExtent());
            const matchingEntities: LocationTrackEndpoint[] = getMatchingEntities(
                hitArea,
                features,
                FEATURE_PROPERTY_LOCATION_TRACK_ENDPOINT,
                matchOptions,
            );
            return {
                locationTracks: matchingEntities.map((entity) => entity.locationTrackId),
                locationTrackEndPoints: matchingEntities,
            };
        },
    };
}
