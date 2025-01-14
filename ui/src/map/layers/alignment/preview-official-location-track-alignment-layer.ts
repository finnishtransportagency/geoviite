import { LineString, Point as OlPoint } from 'ol/geom';
import OlView from 'ol/View';
import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { createLayer, findMatchingEntities, loadLayerData } from 'map/layers/utils/layer-utils';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { ALL_ALIGNMENTS } from 'map/layers/utils/layer-visibility-limits';
import { ChangeTimes } from 'common/common-slice';
import {
    createAlignmentFeature,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { LocationTrackId, ReferenceLineId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import VectorLayer from 'ol/layer/Vector';
import Feature from 'ol/Feature';
import {
    getLocationTrackMapAlignmentsByTiles,
    getReferenceLineMapAlignmentsByTiles,
    LocationTrackAlignmentDataHolder,
    ReferenceLineAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { draftMainLayoutContext, LayoutContext, officialLayoutContext } from 'common/common-model';
import {
    DraftChangeType,
    LocationTrackPublicationCandidate,
    PublicationCandidate,
    PublicationStage,
    ReferenceLinePublicationCandidate,
    TrackNumberPublicationCandidate,
} from 'publication/publication-model';
import {
    ChangeType,
    colorByStage,
    LOCATION_TRACK_CANDIDATE_DATA_PROPERTY,
    REFERENCE_LINE_CANDIDATE_DATA_PROPERTY,
    TRACK_NUMBER_CANDIDATE_DATA_PROPERTY,
} from 'map/layers/highlight/publication-candidate-layer';
import { filterNotEmpty } from 'utils/array-utils';
import { DesignPublicationMode } from 'preview/preview-tool-bar';

let shownLocationTracksCompare = '';
let shownReferenceLinesCompare = '';

const layerName: MapLayerName = 'preview-official-location-track-alignment-layer';

export function createPreviewOfficialLocationTrackAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: VectorLayer<Feature<LineString | OlPoint | Rectangle>> | undefined,
    publicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
    isSplitting: boolean,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);
    const layerLayoutContext =
        designPublicationMode === 'PUBLISH_CHANGES'
            ? officialLayoutContext(layoutContext)
            : draftMainLayoutContext();

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    const resolution = olView.getResolution() || 0;

    function updateShownLocationTracks(locationTrackIds: LocationTrackId[]) {
        const compare = locationTrackIds.sort().join();

        if (compare !== shownLocationTracksCompare) {
            shownLocationTracksCompare = compare;
            onViewContentChanged({ locationTracks: locationTrackIds });
        }
    }

    function updateShownReferenceLines(referenceLineIds: ReferenceLineId[]) {
        const compare = referenceLineIds.sort().join();

        if (compare !== shownReferenceLinesCompare) {
            shownReferenceLinesCompare = compare;
            onViewContentChanged({ referenceLines: referenceLineIds });
        }
    }

    const locationTrackAlignmentPromise =
        resolution <= ALL_ALIGNMENTS && publicationCandidates.length > 0
            ? getLocationTrackMapAlignmentsByTiles(changeTimes, mapTiles, layerLayoutContext).then(
                  (alignments) =>
                      alignments
                          .filter((alignment) =>
                              publicationCandidates.some(
                                  (candidate) =>
                                      candidate.type === DraftChangeType.LOCATION_TRACK &&
                                      candidate.id === alignment.header.id,
                              ),
                          )
                          .map((alignment) => ({
                              alignment: alignment,
                              candidate: publicationCandidates.find(
                                  (c) =>
                                      c.type === DraftChangeType.LOCATION_TRACK &&
                                      c.id === alignment.header.id,
                              ) as LocationTrackPublicationCandidate | undefined,
                          })),
              )
            : Promise.resolve([]);
    const referenceLineAlignmentPromise =
        resolution <= ALL_ALIGNMENTS && publicationCandidates.length > 0
            ? getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, layerLayoutContext).then(
                  (alignments) =>
                      alignments
                          .filter((alignment) =>
                              publicationCandidates.some(
                                  (candidate) =>
                                      candidate.type === DraftChangeType.REFERENCE_LINE &&
                                      candidate.id === alignment.header.id,
                              ),
                          )
                          .map((alignment) => ({
                              alignment: alignment,
                              candidate: publicationCandidates.find(
                                  (c) =>
                                      c.type === DraftChangeType.REFERENCE_LINE &&
                                      c.id === alignment.header.id,
                              ) as ReferenceLinePublicationCandidate | undefined,
                              tnCandidate: publicationCandidates.find(
                                  (c) =>
                                      c.type === DraftChangeType.TRACK_NUMBER &&
                                      c.id === alignment.header.trackNumberId,
                              ) as TrackNumberPublicationCandidate | undefined,
                          })),
              )
            : Promise.resolve([]);
    const allPromises = Promise.all([
        locationTrackAlignmentPromise,
        referenceLineAlignmentPromise,
    ]).then(([locationTracks, referenceLines]) => ({
        locationTracks,
        referenceLines,
    }));

    const createFeatures = (alignments: {
        locationTracks: {
            alignment: LocationTrackAlignmentDataHolder;
            candidate: LocationTrackPublicationCandidate | undefined;
        }[];
        referenceLines: {
            alignment: ReferenceLineAlignmentDataHolder;
            candidate: ReferenceLinePublicationCandidate | undefined;
            tnCandidate: TrackNumberPublicationCandidate | undefined;
        }[];
    }) => {
        const showEndPointTicks = resolution <= Limits.SHOW_LOCATION_TRACK_BADGES;

        const ltFeatures = alignments.locationTracks
            .flatMap(({ alignment, candidate }) => {
                const lineFeature = candidate
                    ? createAlignmentFeature(
                          alignment,
                          showEndPointTicks,
                          new Style({
                              stroke: new Stroke({
                                  color: mapStyles.alignmentPreviewOfficialLine,
                                  width: 1,
                              }),
                              zIndex: 1,
                          }),
                      )
                    : undefined;
                const highlightFeature = candidate
                    ? createAlignmentFeature(
                          alignment,
                          false,
                          new Style({
                              stroke: new Stroke({
                                  color: colorByStage(candidate.stage, ChangeType.EXPLICIT, true),
                                  width: candidate.stage === PublicationStage.UNSTAGED ? 23 : 11,
                                  lineCap: 'butt',
                              }),
                              zIndex: -1,
                          }),
                      )
                    : undefined;
                highlightFeature?.map((f) =>
                    f.set(LOCATION_TRACK_CANDIDATE_DATA_PROPERTY, candidate),
                );
                return [lineFeature, highlightFeature];
            })
            .flat()
            .filter(filterNotEmpty);
        const rlFeatures: Feature<LineString | OlPoint>[] = alignments.referenceLines
            .flatMap(({ alignment, candidate, tnCandidate }) => {
                const lineFeature = candidate
                    ? createAlignmentFeature(
                          alignment,
                          showEndPointTicks,
                          new Style({
                              stroke: new Stroke({
                                  color: mapStyles.alignmentPreviewOfficialLine,
                                  width: 3,
                              }),
                              zIndex: 1,
                          }),
                      )
                    : undefined;
                const highlightFeature = candidate
                    ? createAlignmentFeature(
                          alignment,
                          false,
                          new Style({
                              stroke: new Stroke({
                                  color: colorByStage(candidate.stage, ChangeType.EXPLICIT, true),
                                  width: 15,
                                  lineCap: 'butt',
                              }),
                              zIndex: -1,
                          }),
                      )
                    : undefined;
                highlightFeature?.forEach((f) => {
                    f.set(REFERENCE_LINE_CANDIDATE_DATA_PROPERTY, candidate);
                    f.set(TRACK_NUMBER_CANDIDATE_DATA_PROPERTY, tnCandidate);
                });
                return [lineFeature, highlightFeature];
            })
            .flat()
            .filter(filterNotEmpty);

        return [...ltFeatures, ...rlFeatures];
    };

    const onLoadingChange = (
        loading: boolean,
        alignments:
            | {
                  locationTracks: {
                      alignment: LocationTrackAlignmentDataHolder;
                      candidate: LocationTrackPublicationCandidate | undefined;
                  }[];
                  referenceLines: {
                      alignment: ReferenceLineAlignmentDataHolder;
                      candidate: ReferenceLinePublicationCandidate | undefined;
                      tnCandidate: TrackNumberPublicationCandidate | undefined;
                  }[];
              }
            | undefined,
    ) => {
        if (!loading) {
            updateShownLocationTracks(
                alignments?.locationTracks?.map(({ alignment }) => alignment.header.id) ?? [],
            );
            updateShownReferenceLines(
                alignments?.referenceLines?.map(({ alignment }) => alignment.header.id) ?? [],
            );
        }
        onLoadingData(loading);
    };

    loadLayerData(source, isLatest, onLoadingChange, allPromises, createFeatures);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            locationTrackPublicationCandidates:
                findMatchingEntities<LocationTrackPublicationCandidate>(
                    hitArea,
                    source,
                    LOCATION_TRACK_CANDIDATE_DATA_PROPERTY,
                    options,
                ),
            referenceLinePublicationCandidates:
                findMatchingEntities<ReferenceLinePublicationCandidate>(
                    hitArea,
                    source,
                    REFERENCE_LINE_CANDIDATE_DATA_PROPERTY,
                    options,
                ),
            trackNumberPublicationCandidates: findMatchingEntities<TrackNumberPublicationCandidate>(
                hitArea,
                source,
                TRACK_NUMBER_CANDIDATE_DATA_PROPERTY,
                options,
            ),
        }),
        onRemove: () => {
            updateShownLocationTracks([]);
            updateShownReferenceLines([]);
        },
    };
}
