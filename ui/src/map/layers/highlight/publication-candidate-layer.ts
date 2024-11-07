import { LineString, Point as OlPoint } from 'ol/geom';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty } from 'utils/array-utils';
import Feature from 'ol/Feature';
import {
    createLayer,
    findMatchingEntities,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import {
    DraftChangeType,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    PublicationStage,
    SwitchPublicationCandidate,
} from 'publication/publication-model';
import { Rectangle } from 'model/geometry';

export const LOCATION_TRACK_CANDIDATE_DATA_PROPERTY = 'location-track-candidate-data';
export const SWITCH_CANDIDATE_DATA_PROPERTY = 'switch-candidate-data';

type PublicationCandidateFeatureTypes = LineString | OlPoint;

type LocationTrackCandidateWithAlignment = {
    publishCandidate: PublicationCandidate;
    alignment: LocationTrackAlignmentDataHolder;
};

function colorByStage(stage: PublicationStage): string {
    return stage === PublicationStage.STAGED ? '#0066ccaa' : '#ffc300aa';
}

// const getColorForTrackNumber = (
//     id: LayoutTrackNumberId,
//     layerSettings: TrackNumberDiagramLayerSetting,
// ) => {
//     //Track numbers with transparent color are already filtered out
//     const selectedColor = layerSettings[id]?.color ?? getDefaultColorKey(id);
//     return getColor(selectedColor) + '55'; //~33 % opacity in hex
// };

function createLocationTrackCandidateFeatures(
    candidates: LocationTrackCandidateWithAlignment[],
): Feature<LineString>[] {
    return candidates.map((c) => {
        const style = new Style({
            stroke: new Stroke({
                color: colorByStage(c.publishCandidate.stage),
                width: 15,
                lineCap: 'butt',
            }),
        });
        const feature = new Feature({
            geometry: new LineString(c.alignment.points.map(pointToCoords)),
        });
        feature.setStyle(style);
        feature.set(LOCATION_TRACK_CANDIDATE_DATA_PROPERTY, c.publishCandidate);
        return feature;
    });
}

function createSwitchCandidateFeatures(
    switchCandidates: SwitchPublicationCandidate[],
): Feature<OlPoint>[] {
    const x = switchCandidates
        .map((c) => {
            if (!c.location) {
                return undefined;
            }

            const color = colorByStage(c.stage);
            const style = new Style({
                image: new Circle({
                    radius: 20,
                    stroke: new Stroke({ color: color }),
                    fill: new Fill({ color: color }),
                }),
            });

            const feature = new Feature({
                geometry: new OlPoint(pointToCoords(c.location)),
            });
            feature.setStyle(style);
            feature.set(SWITCH_CANDIDATE_DATA_PROPERTY, c);
            return feature;
        })
        .filter(filterNotEmpty);
    return x;
}

const layerName: MapLayerName = 'publication-candidate-layer';

export function createPublicationCandidateLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<PublicationCandidateFeatureTypes>> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    _resolution: number,
    onLoadingData: (loading: boolean) => void,
    publicationCandidates: PublicationCandidate[],
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const locationTrackCandidates = publicationCandidates.filter(
        (c) => c.type == DraftChangeType.LOCATION_TRACK,
    );
    const locationTrackIds = locationTrackCandidates
        .map((c) => (c.type == DraftChangeType.LOCATION_TRACK ? c.id : undefined))
        .filter(filterNotEmpty);

    const locationTrackAlignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((locationTrackAlignments) => {
        return locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateWithAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty);
    });

    const switchCandidates = publicationCandidates
        .map((c) =>
            c.type == DraftChangeType.SWITCH ? (c as SwitchPublicationCandidate) : undefined,
        )
        .filter(filterNotEmpty);

    const createFeatures = (data: {
        locationTrackCandidates: LocationTrackCandidateWithAlignment[];
    }) => {
        // // const showAll = Object.values(layerSettings).every((s) => !s.selected);
        const filteredAlignments = data.locationTrackCandidates.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
            // const trackNumberId = a.trackNumber?.id;
            // return trackNumberId ? !!layerSettings[trackNumberId]?.selected : false;
        });
        //
        // const alignmentsWithColor = filteredAlignments.filter((a) => {
        //     return true;
        // });
        const locationTrackAlignmentFeatures =
            createLocationTrackCandidateFeatures(filteredAlignments);
        const switchFeatures = createSwitchCandidateFeatures(switchCandidates);

        return [...locationTrackAlignmentFeatures, ...switchFeatures];
    };

    const allData = Promise.all([locationTrackAlignmentPromise]).then((result) => ({
        locationTrackCandidates: result[0],
    }));

    loadLayerData(source, isLatest, onLoadingData, allData, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const locationTrackPublicationCandidates =
                findMatchingEntities<LocationTrackPublicationCandidate>(
                    hitArea,
                    source,
                    LOCATION_TRACK_CANDIDATE_DATA_PROPERTY,
                    options,
                );

            const switchPublicationCandidates = findMatchingEntities<SwitchPublicationCandidate>(
                hitArea,
                source,
                SWITCH_CANDIDATE_DATA_PROPERTY,
                options,
            );

            return {
                locationTrackPublicationCandidates: locationTrackPublicationCandidates,
                switchPublicationCandidates: switchPublicationCandidates,
            };
        },
    };
}
