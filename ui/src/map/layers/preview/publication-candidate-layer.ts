import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    getLocationTrackMapAlignmentsByTiles,
    getReferenceLineMapAlignmentsByTiles,
} from 'track-layout/layout-map-api';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    draftMainLayoutContext,
    LayoutContext,
    officialLayoutContext,
    SwitchStructure,
} from 'common/common-model';
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
    OperationalPointPublicationCandidate,
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
    createBaseLocationTrackFeatures,
    createBaseReferenceLineFeatures,
    createCandidateLocationTrackFeatures,
    createCandidatePointFeatures,
    createCandidateReferenceLineFeatures,
    createCandidateTrackNumberFeatures,
    getSwitchLocation,
    LocationTrackCandidateAndAlignment,
    PublicationCandidateFeatureType,
    ReferenceLineCandidateAndAlignment,
    TrackNumberCandidateAndAlignment,
} from 'map/layers/utils/publication-candidate-highlight-utils';
import { LayoutKmPost, LayoutSwitch, OperationalPoint } from 'track-layout/track-layout-model';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getKmPosts } from 'track-layout/layout-km-post-api';
import { getManyOperationalPoints } from 'track-layout/layout-operational-point-api';
import { getSwitchStructures } from 'common/common-api';

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
        true,
        locationTrackIds,
    ).then((locationTrackAlignments) => {
        return locationTrackAlignments
            .map((alignment) => {
                const candidate = locationTrackCandidates.find((c) => c.id === alignment.header.id);
                return candidate
                    ? ({
                          alignment: alignment,
                          publishCandidate: candidate,
                      } as LocationTrackCandidateAndAlignment)
                    : undefined;
            })
            .filter(filterNotEmpty);
    });

    const baseLocationTrackAlignmentsPromise =
        locationTrackCandidates.length > 0
            ? getLocationTrackMapAlignmentsByTiles(
                  changeTimes,
                  mapTiles,
                  targetLayoutContext,
                  true,
                  locationTrackIds,
              ).then((alignments) =>
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
    const baseSwitchesPromise = getSwitches(
        switchCandidates.map((c) => c.id),
        targetLayoutContext,
        changeTimes.layoutSwitch,
    );

    const kmPostCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.KM_POST,
    );
    const baseKmPostsPromise = getKmPosts(
        kmPostCandidates.map((c) => c.id),
        targetLayoutContext,
        changeTimes.layoutKmPost,
    );

    const operationalPointCandidates = publicationCandidates.filter(
        (c) => c.type === DraftChangeType.OPERATIONAL_POINT,
    );
    const baseOperationalPointsPromise = getManyOperationalPoints(
        operationalPointCandidates.map((c) => c.id),
        targetLayoutContext,
        changeTimes.operationalPoints,
    );

    const createFeatures = (data: {
        candidateLocationTracks: LocationTrackCandidateAndAlignment[];
        candidateReferenceLines: ReferenceLineCandidateAndAlignment[];
        candidateTrackNumbers: TrackNumberCandidateAndAlignment[];
        baseLocationTracks: LocationTrackCandidateAndAlignment[];
        baseReferenceLines: ReferenceLineCandidateAndAlignment[];
        baseSwitches: LayoutSwitch[];
        baseKmPosts: LayoutKmPost[];
        baseOperationalPoints: OperationalPoint[];
        switchStructures: SwitchStructure[];
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
            (candidate) => {
                const baseSwitch = data.baseSwitches.find((s) => s.id === candidate.id);
                if (!baseSwitch) {
                    return undefined;
                }

                const structure = data.switchStructures.find(
                    (str) => str.id === baseSwitch.switchStructureId,
                );
                return structure ? getSwitchLocation(baseSwitch, structure) : undefined;
            },
        );
        const candidateKmPostFeatures = createCandidatePointFeatures(
            kmPostCandidates,
            DraftChangeType.KM_POST,
            (candidate) => data.baseKmPosts.find((k) => k.id === candidate.id)?.layoutLocation,
        );

        const candidateOperationalPointFeatures = createCandidatePointFeatures(
            operationalPointCandidates,
            DraftChangeType.OPERATIONAL_POINT,
            (candidate) =>
                data.baseOperationalPoints.find((op) => op.id === candidate.id)?.location,
        );

        const showEndPointTicks = metersPerPixel <= Limits.SHOW_LOCATION_TRACK_BADGES;

        const baseLocationTrackFeatures = data.baseLocationTracks
            .flatMap(({ alignment, publishCandidate }) =>
                createBaseLocationTrackFeatures(
                    publishCandidate,
                    alignment,
                    showEndPointTicks,
                    metersPerPixel,
                ),
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
                    metersPerPixel,
                );
            })
            .filter(filterNotEmpty);

        return [
            ...candidateLocationTrackAlignmentFeatures,
            ...candidateReferenceLineAlignmentFeatures,
            ...candidateTrackNumberAlignmentFeatures,
            ...candidateSwitchFeatures,
            ...candidateKmPostFeatures,
            ...candidateOperationalPointFeatures,
            ...baseLocationTrackFeatures,
            ...baseReferenceLineFeatures,
        ];
    };

    const allData = Promise.all([
        candidateLocationTrackAlignmentPromise,
        candidateReferenceLineAlignmentPromise,
        baseLocationTrackAlignmentsPromise,
        baseReferenceLineLineAlignmentsPromise,
        baseSwitchesPromise,
        baseKmPostsPromise,
        baseOperationalPointsPromise,
        getSwitchStructures(),
    ]).then(
        ([
            candidateLocationTracks,
            candidateTrackNumbersAndReferenceLines,
            baseLocationTracks,
            baseReferenceLines,
            baseSwitches,
            baseKmPosts,
            baseOperationalPoints,
            switchStructures,
        ]) => {
            const { candidateTrackNumbers, candidateReferenceLines } =
                candidateTrackNumbersAndReferenceLines;

            return {
                candidateLocationTracks,
                candidateTrackNumbers,
                candidateReferenceLines,
                baseLocationTracks,
                baseReferenceLines,
                baseSwitches,
                baseKmPosts,
                baseOperationalPoints,
                switchStructures,
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

            const operationalPointPublicationCandidates =
                findByPropertyName<OperationalPointPublicationCandidate>(
                    CandidateDataProperties.OPERATIONAL_POINT,
                );

            return {
                locationTrackPublicationCandidates,
                referenceLinePublicationCandidates,
                trackNumberPublicationCandidates,
                switchPublicationCandidates,
                kmPostPublicationCandidates,
                operationalPointPublicationCandidates,
            };
        },
    };
}
