import Feature from 'ol/Feature';
import { Point as OlPoint, Polygon } from 'ol/geom';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { MapTile } from 'map/map-model';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LocationTrackEndpoint } from 'linking/linking-model';
import { MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION } from 'map/layers/utils/layer-visibility-limits';
import { flatten } from 'utils/array-utils';
import {
    clearFeatures,
    getMatchingEntities,
    MatchOptions,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { PublishType } from 'common/common-model';
import { getLocationTrackEndpointsByTile } from 'track-layout/layout-location-track-api';
import { endPointStyle } from 'map/layers/switch/switch-layer-utils';

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

let newestLayerId = 0;
export function createManualSwitchLinkingLayer(
    mapTiles: MapTile[],
    resolution: number,
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    publishType: PublishType,
): MapLayer {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });

    function updateFeatures(features: Feature<OlPoint>[]) {
        clearFeatures(vectorSource);
        vectorSource.addFeatures(features);
    }

    if (resolution <= MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION) {
        const locationTrackEndpointsPromise = Promise.all(
            mapTiles.map((tile) => getLocationTrackEndpointsByTile(tile, publishType)),
        ).then(flatten);

        locationTrackEndpointsPromise
            .then((locationTrackEndPoints) => {
                // Handle latest fetch only
                if (layerId !== newestLayerId) return;

                const features = locationTrackEndPoints.flatMap((e) =>
                    createLocationTrackEndpointFeatures(e, false),
                );

                updateFeatures(features);
            })
            .catch(() => clearFeatures(vectorSource));
    } else {
        clearFeatures(vectorSource);
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
