import BaseLayer from 'ol/layer/Base';
import { MapLayerName } from 'map/map-model';
import { OptionalItemCollections } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';
import {
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';

export type LayerItemSearchResult = OptionalItemCollections & {
    locationTrackPublicationCandidates?: LocationTrackPublicationCandidate[];
    referenceLinePublicationCandidates?: ReferenceLinePublicationCandidate[];
    trackNumberPublicationCandidates?: TrackNumberPublicationCandidate[];
    switchPublicationCandidates?: SwitchPublicationCandidate[];
    kmPostPublicationCandidates?: KmPostPublicationCandidate[];
};

export type SearchItemsOptions = {
    limit?: number;
};

export type MapLayer = {
    name: MapLayerName;
    layer: BaseLayer;
    searchItems?: (hitArea: Rectangle, options: SearchItemsOptions) => LayerItemSearchResult;
    onRemove?: () => void;
};
