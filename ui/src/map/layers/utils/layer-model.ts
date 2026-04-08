import BaseLayer from 'ol/layer/Base';
import { MapLayerName } from 'map/map-model';
import { OptionalItemCollections } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';
import {
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    OperationalPointPublicationCandidate,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import { OperationalPointId } from 'track-layout/track-layout-model';

export type LayerItemSearchResult = OptionalItemCollections & {
    locationTrackPublicationCandidates?: LocationTrackPublicationCandidate[];
    referenceLinePublicationCandidates?: ReferenceLinePublicationCandidate[];
    trackNumberPublicationCandidates?: TrackNumberPublicationCandidate[];
    switchPublicationCandidates?: SwitchPublicationCandidate[];
    kmPostPublicationCandidates?: KmPostPublicationCandidate[];
    operationalPointPublicationCandidates?: OperationalPointPublicationCandidate[];
    operationalPointsHitByIcon?: OperationalPointId[];
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
