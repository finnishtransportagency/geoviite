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
import {
    createLayer,
    findMatchingEntities,
    GeoviiteMapLayer,
    loadLayerData,
} from 'map/layers/utils/layer-utils';
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
    createCandidateLocationTrackFeatures,
    createBaseLocationTrackFeatures,
    createBaseReferenceLineFeatures,
    createCandidatePointFeatures,
    createCandidateReferenceLineFeatures,
    createCandidateTrackNumberFeatures,
    LocationTrackCandidateAndAlignment,
    PublicationCandidateFeatureType,
    ReferenceLineCandidateAndAlignment,
    TrackNumberCandidateAndAlignment,
} from 'map/layers/utils/publication-candidate-highlight-utils';

const layerName: MapLayerName = 'publication-candidate-layer';

export function createPublicationCandidateLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<PublicationCandidateFeatureType> | undefined,
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
        (c) => c.type === DraftChangeType.LOCATION_TRACK,
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

    const candidateReferenceLineAlignmentPromise = getReferenceLineMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((rlAlignments) => {
        const rlCandidates = rlAlignments
            .map((alignment) => {
                const candidate = referenceLineCandidates.find((c) => c.id === alignment.header.id);
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
                    (c) => c.id === alignment.header.trackNumberId,
                );
                return candidate
                    ? {
                          alignment: alignment,
                          publishCandidate: candidate,
                      }
                    : undefined;
            })
            .filter(filterNotEmpty);

        return { candidateTrackNumbers: tnCandidates, candidateReferenceLines: rlCandidates };
    });

    const candidateLocationTrackAlignmentPromise = getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    ).then((locationTrackAlignments) =>
        locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id === alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty),
    );

    const baseLocationTrackAlignmentsPromise =
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

    const baseReferenceLineLineAlignmentsPromise =
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
        candidateLocationTracks: LocationTrackCandidateAndAlignment[];
        candidateReferenceLines: ReferenceLineCandidateAndAlignment[];
        candidateTrackNumbers: TrackNumberCandidateAndAlignment[];
        baseLocationTracks: LocationTrackCandidateAndAlignment[];
        baseReferenceLines: ReferenceLineCandidateAndAlignment[];
    }) => {
        const filteredLocationTrackCandidates = data.candidateLocationTracks.filter((c) => {
            return locationTrackIds.includes(c.alignment.header.id);
        });
        const filteredReferenceLineCandidates = data.candidateReferenceLines.filter((c) => {
            return referenceLineIds.includes(c.alignment.header.id);
        });
        const filteredTrackNumberCandidates = data.candidateTrackNumbers.filter(
            (c) =>
                c.alignment.header.trackNumberId !== undefined &&
                trackNumberIds.includes(c.alignment.header.trackNumberId),
        );

        const candidateLocationTrackAlignmentFeatures = createCandidateLocationTrackFeatures(
            filteredLocationTrackCandidates,
            metersPerPixel,
        );
        const candidateReferenceLineAlignmentFeatures = createCandidateReferenceLineFeatures(
            filteredReferenceLineCandidates,
            metersPerPixel,
        );
        const candidateTrackNumberAlignmentFeatures = createCandidateTrackNumberFeatures(
            filteredTrackNumberCandidates,
            metersPerPixel,
        );
        const candidateSwitchFeatures = createCandidatePointFeatures(
            switchCandidates,
            DraftChangeType.SWITCH,
        );
        const candidateKmPostFeatures = createCandidatePointFeatures(
            kmPostCandidates,
            DraftChangeType.KM_POST,
        );

        const showEndPointTicks = metersPerPixel <= Limits.SHOW_LOCATION_TRACK_BADGES;

        const baseLocationTrackFeatures = data.baseLocationTracks
            .flatMap(({ alignment, publishCandidate }) =>
                createBaseLocationTrackFeatures(publishCandidate, alignment, showEndPointTicks),
            )
            .filter(filterNotEmpty);
        const baseReferenceLineFeatures: Feature<LineString | OlPoint>[] = data.baseReferenceLines
            .flatMap(({ alignment, publishCandidate }) => {
                const tnCandidate = trackNumberCandidates.find(
                    (tn) => tn.id === publishCandidate.trackNumberId,
                );

                return createBaseReferenceLineFeatures(
                    publishCandidate,
                    alignment,
                    tnCandidate,
                    showEndPointTicks,
                );
            })
            .filter(filterNotEmpty);

        return [
            ...candidateLocationTrackAlignmentFeatures,
            ...candidateReferenceLineAlignmentFeatures,
            ...candidateTrackNumberAlignmentFeatures,
            ...candidateSwitchFeatures,
            ...candidateKmPostFeatures,
            ...baseLocationTrackFeatures,
            ...baseReferenceLineFeatures,
        ];
    };

    const allData = Promise.all([
        candidateLocationTrackAlignmentPromise,
        candidateReferenceLineAlignmentPromise,
        baseLocationTrackAlignmentsPromise,
        baseReferenceLineLineAlignmentsPromise,
    ]).then(
        ([
            candidateLocationTracks,
            candidateTrackNumbersAndReferenceLines,
            baseLocationTracks,
            baseReferenceLines,
        ]) => {
            const { candidateTrackNumbers, candidateReferenceLines } =
                candidateTrackNumbersAndReferenceLines;

            return {
                candidateLocationTracks,
                candidateTrackNumbers,
                candidateReferenceLines,
                baseLocationTracks,
                baseReferenceLines,
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
