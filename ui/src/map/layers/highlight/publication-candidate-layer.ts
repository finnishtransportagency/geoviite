import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    getReferenceLineMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { draftMainLayoutContext, LayoutContext, officialLayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty } from 'utils/array-utils';
import Feature from 'ol/Feature';
import { createLayer, findMatchingEntities, loadLayerData } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import {
    DraftChangeType,
    KmPostPublicationCandidate,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    ReferenceLinePublicationCandidate,
    SwitchPublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import { Rectangle } from 'model/geometry';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import {
    CandidateDataProperties,
    createLocationTrackCandidateFeatures,
    createOfficialLocationTrackFeatures,
    createOfficialReferenceLineFeatures,
    createPointCandidateFeatures,
    createReferenceLineCandidateFeatures,
    createTrackNumberCandidateFeatures,
    LocationTrackCandidateAndAlignment,
    PublicationCandidateFeatureType,
    ReferenceLineCandidateAndAlignment,
    TrackNumberCandidateAndAlignment,
} from 'map/layers/utils/publication-candidate-highlight-utils';

const layerName: MapLayerName = 'publication-candidate-layer';

export function createPublicationCandidateLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<PublicationCandidateFeatureType>> | undefined,
    changeTimes: ChangeTimes,
    layoutContext: LayoutContext,
    metersPerPixel: number,
    onLoadingData: (loading: boolean) => void,
    publicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const targetLayoutContext =
        designPublicationMode === 'PUBLISH_CHANGES'
            ? officialLayoutContext(layoutContext)
            : draftMainLayoutContext();

    const locationTrackCandidates = publicationCandidates.filter(
        (c) => c.type == DraftChangeType.LOCATION_TRACK,
    );
    const locationTrackIds = locationTrackCandidates.map((c) => c.id);

    const trackNumberCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.TRACK_NUMBER,
    );
    const trackNumberIds = trackNumberCandidates.map((c) => c.id);

    const referenceLineCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.REFERENCE_LINE,
    );
    const referenceLineIds = referenceLineCandidates.map((c) => c.id);

    const referenceLineAlignmentPromise = getReferenceLineMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((rlAlignments) => {
        const rlCandidates = rlAlignments
            .map((alignment) => {
                const candidate = referenceLineCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? {
                          alignment: alignment,
                          publishCandidate: candidate,
                      }
                    : undefined;
            })
            .filter(filterNotEmpty);
        const tnCandidates = rlAlignments
            .map((alignment) => {
                const candidate = trackNumberCandidates.find(
                    (c) => c.id == alignment.header.trackNumberId,
                );
                return candidate
                    ? {
                          alignment: alignment,
                          publishCandidate: candidate,
                      }
                    : undefined;
            })
            .filter(filterNotEmpty);

        return { trackNumberCandidates: tnCandidates, referenceLineCandidates: rlCandidates };
    });

    const locationTrackAlignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((locationTrackAlignments) =>
        locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id == alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty),
    );

    const officialLocationTrackAlignmentsPromise =
        locationTrackCandidates.length > 0
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, targetLayoutContext).then(
                  (alignments) =>
                      alignments
                          .map((alignment) => {
                              const publishCandidate = locationTrackCandidates.find(
                                  (c) => c.id === alignment.header.id,
                              );

                              return publishCandidate
                                  ? {
                                        alignment,
                                        publishCandidate,
                                    }
                                  : undefined;
                          })
                          .filter(filterNotEmpty),
              )
            : Promise.resolve([]);

    const officialReferenceLineLineAlignmentsPromise =
        referenceLineCandidates.length > 0
            ? getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, targetLayoutContext).then(
                  (alignments) =>
                      alignments
                          .map((alignment) => {
                              const publishCandidate = referenceLineCandidates.find(
                                  (c) => c.id === alignment.header.id,
                              );
                              return publishCandidate
                                  ? {
                                        alignment,
                                        publishCandidate,
                                    }
                                  : undefined;
                          })
                          .filter(filterNotEmpty),
              )
            : Promise.resolve([]);
    const switchCandidates = publicationCandidates.filter((c) => c.type === DraftChangeType.SWITCH);

    const kmPostCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.KM_POST,
    );

    const createFeatures = (data: {
        locationTrackCandidates: LocationTrackCandidateAndAlignment[];
        referenceLineCandidates: ReferenceLineCandidateAndAlignment[];
        trackNumberCandidates: TrackNumberCandidateAndAlignment[];
        officialLocationTracks: LocationTrackCandidateAndAlignment[];
        officialReferenceLines: ReferenceLineCandidateAndAlignment[];
    }) => {
        const filteredLocationTracks = data.locationTrackCandidates.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
        });
        const filteredReferenceLines = data.referenceLineCandidates.filter((c) => {
            return referenceLineIds.includes(c.alignment.header.id);
        });
        const filteredTrackNumbers = data.trackNumberCandidates.filter(
            (c) =>
                c.alignment.header.trackNumberId !== undefined &&
                trackNumberIds.includes(c.alignment.header.trackNumberId),
        );

        const locationTrackAlignmentFeatures = createLocationTrackCandidateFeatures(
            filteredLocationTracks,
            metersPerPixel,
        );
        const referenceLineAlignmentFeatures = createReferenceLineCandidateFeatures(
            filteredReferenceLines,
            metersPerPixel,
        );
        const trackNumberAlignmentFeatures = createTrackNumberCandidateFeatures(
            filteredTrackNumbers,
            metersPerPixel,
        );
        const switchFeatures = createPointCandidateFeatures(
            switchCandidates,
            DraftChangeType.SWITCH,
        );
        const kmPostFeatures = createPointCandidateFeatures(
            kmPostCandidates,
            DraftChangeType.KM_POST,
        );

        const showEndPointTicks = metersPerPixel <= Limits.SHOW_LOCATION_TRACK_BADGES;

        const officialLocationTrackFeatures = data.officialLocationTracks
            .flatMap(({ alignment, publishCandidate }) =>
                createOfficialLocationTrackFeatures(publishCandidate, alignment, showEndPointTicks),
            )
            .filter(filterNotEmpty);
        const officialReferenceLineFeatures: Feature<LineString | OlPoint>[] =
            data.officialReferenceLines
                .flatMap(({ alignment, publishCandidate }) => {
                    const tnCandidate = trackNumberCandidates.find(
                        (tn) => tn.id === publishCandidate.trackNumberId,
                    );

                    return createOfficialReferenceLineFeatures(
                        publishCandidate,
                        alignment,
                        tnCandidate,
                        showEndPointTicks,
                    );
                })
                .filter(filterNotEmpty);

        return [
            ...locationTrackAlignmentFeatures,
            ...referenceLineAlignmentFeatures,
            ...trackNumberAlignmentFeatures,
            ...switchFeatures,
            ...kmPostFeatures,
            ...officialLocationTrackFeatures,
            ...officialReferenceLineFeatures,
        ];
    };

    const allData = Promise.all([
        locationTrackAlignmentPromise,
        referenceLineAlignmentPromise,
        officialLocationTrackAlignmentsPromise,
        officialReferenceLineLineAlignmentsPromise,
    ]).then(
        ([
            locationTrackCandidates,
            trackNumberAndReferenceLineCandidates,
            officialLocationTracks,
            officialReferenceLines,
        ]) => {
            const { trackNumberCandidates, referenceLineCandidates } =
                trackNumberAndReferenceLineCandidates;

            return {
                locationTrackCandidates,
                trackNumberCandidates,
                referenceLineCandidates,
                officialLocationTracks,
                officialReferenceLines,
            };
        },
    );

    loadLayerData(source, isLatest, onLoadingData, allData, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const findByPropertyName = <T>(propertyName: string) =>
                findMatchingEntities<T>(hitArea, source, propertyName, options);

            const locationTrackPublicationCandidates =
                findByPropertyName<LocationTrackPublicationCandidate>(
                    CandidateDataProperties.LOCATION_TRACK,
                );

            const referenceLinePublicationCandidates =
                findByPropertyName<ReferenceLinePublicationCandidate>(
                    CandidateDataProperties.REFERENCE_LINE,
                );

            const trackNumberPublicationCandidates =
                findByPropertyName<TrackNumberPublicationCandidate>(
                    CandidateDataProperties.TRACK_NUMBER,
                );

            const switchPublicationCandidates = findByPropertyName<SwitchPublicationCandidate>(
                CandidateDataProperties.SWITCH,
            );

            const kmPostPublicationCandidates = findByPropertyName<KmPostPublicationCandidate>(
                CandidateDataProperties.KM_POST,
            );

            return {
                locationTrackPublicationCandidates,
                referenceLinePublicationCandidates,
                trackNumberPublicationCandidates,
                switchPublicationCandidates,
                kmPostPublicationCandidates,
            };
        },
    };
}
