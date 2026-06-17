import * as React from 'react';
import styles from 'tool-panel/geometry-alignment/geometry-alignment-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { deduplicateById, fieldComparator, negComparator } from 'utils/array-utils';
import { expandBoundingBox } from 'model/geometry';
import {
    getLocationTracksNear,
    getNonLinkedLocationTracks,
} from 'track-layout/layout-location-track-api';
import { LayoutContext, TimeStamp } from 'common/common-model';
import {
    LinkingGeometryWithAlignment,
    LinkingGeometryWithEmptyAlignment,
    PreliminaryLinkingGeometry,
} from 'linking/linking-model';
import { OnSelectOptions } from 'selection/selection-model';
import { AlignmentHeader } from 'track-layout/layout-map-api';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import {
    LocationTrackCandidates,
    TrackNumberCandidate,
    TrackNumberCandidates,
} from 'linking/alignment-linking-candidates';
import {
    getNonLinkedTrackNumbers,
    getTrackNumbersNear,
} from 'track-layout/layout-track-number-api';

type GeometryAlignmentLinkingReferenceLineCandidatesProps = {
    geometryAlignment: AlignmentHeader;
    layoutContext: LayoutContext;
    trackNumberChangeTime: TimeStamp;
    selectedTrackNumber?: LayoutTrackNumber;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onSelect: (options: OnSelectOptions) => void;
    onShowAddTrackNumberDialog: () => void;
    disableAddButton: boolean;
};

type GeometryAlignmentLinkingLocationTrackCandidatesProps = {
    geometryAlignment: AlignmentHeader;
    layoutContext: LayoutContext;
    locationTrackChangeTime: TimeStamp;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onSelect: (options: OnSelectOptions) => void;
    onShowAddLocationTrackDialog: () => void;
    disableAddButton: boolean;
    selectedPartOfUnfinishedSplit: boolean;
};

type LayoutTrackNumberSearchResult = LayoutTrackNumber & {
    foundWithBoundingBox?: true;
};

function toBoundingBoxSearchResults<T extends object>(
    searchResults: T[],
): Array<T & { foundWithBoundingBox: true }> {
    return searchResults.map((result) => ({
        ...result,
        foundWithBoundingBox: true,
    }));
}

function byDraftsFirst<T extends { isDraft: boolean }>(a: T, b: T) {
    if (a.isDraft !== b.isDraft) {
        // Actual drafts should be first. The order between drafts is however kept in place.
        return a.isDraft ? -1 : 1;
    }

    return 0;
}

const NEAR_TRACK_SEARCH_BUFFER = 10.0;

function lockedAlignmentIdOf<TId extends LocationTrackId | LayoutTrackNumberId>(
    linkingState:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry
        | undefined,
): TId | undefined {
    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';
    return linkingInProgress ? (linkingState.layoutAlignment.id as TId) : undefined;
}

export const GeometryAlignmentLinkingReferenceLineCandidates: React.FC<
    GeometryAlignmentLinkingReferenceLineCandidatesProps
> = ({
    geometryAlignment,
    layoutContext,
    trackNumberChangeTime,
    selectedTrackNumber,
    linkingState,
    onSelect,
    onShowAddTrackNumberDialog,
    disableAddButton,
}) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(layoutContext, trackNumberChangeTime);
    const [tnSearchResults, setTnSearchResults] = React.useState<LayoutTrackNumberSearchResult[]>(
        [],
    );
    const [tnSearchInput, setTnSearchInput] = React.useState<string>('');
    const [isLoading, setIsLoading] = React.useState(true);

    React.useEffect(() => {
        if (geometryAlignment.boundingBox) {
            setIsLoading(true);

            Promise.all([
                getTrackNumbersNear(
                    layoutContext,
                    expandBoundingBox(geometryAlignment.boundingBox, NEAR_TRACK_SEARCH_BUFFER),
                ).then(toBoundingBoxSearchResults),

                getNonLinkedTrackNumbers(layoutContext).then((lines) =>
                    lines.sort(negComparator(fieldComparator((line) => line.id))),
                ),
            ])
                .then((trackGroups) => {
                    const uniqueReferenceLines = deduplicateById(trackGroups.flat(), (l) => l.id);

                    uniqueReferenceLines.sort(byDraftsFirst);
                    setTnSearchResults(uniqueReferenceLines);
                })
                .finally(() => {
                    setIsLoading(false);
                });
        }
    }, [geometryAlignment.boundingBox, trackNumberChangeTime]);

    const candidates: TrackNumberCandidate[] = React.useMemo(() => {
        if (!trackNumbers) return [];
        const hasSearchInput = tnSearchInput.length > 0;

        return tnSearchResults.flatMap((result) => {
            const trackNumber = trackNumbers.find((tn) => tn.id === result.id);
            if (!trackNumber) return [];

            const trackNumberMatchesSearchInput = trackNumber.number
                .toLowerCase()
                .includes(tnSearchInput);
            const trackNumberWithEmptyGeometryIsAlreadyPublished =
                !result.foundWithBoundingBox && !result.isDraft;

            const displayTrackNumberOption =
                (hasSearchInput && trackNumberMatchesSearchInput) ||
                (!hasSearchInput && !trackNumberWithEmptyGeometryIsAlreadyPublished);

            const isSelected = result.id === selectedTrackNumber?.id;

            return isSelected || displayTrackNumberOption
                ? [{ referenceLine: result, trackNumber }]
                : [];
        });
    }, [tnSearchResults, trackNumbers, tnSearchInput, selectedTrackNumber]);

    return (
        <React.Fragment>
            <div className={styles['geometry-alignment-infobox__search-container']}>
                <InfoboxText value={t('tool-panel.alignment.geometry.choose-reference-line')} />
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    disabled={disableAddButton}
                    onClick={() => onShowAddTrackNumberDialog()}
                    qa-id="create-tracknumer-button"
                />
            </div>

            <TextField
                className={'geometry-alignment-infobox__alignments-container-search-field'}
                placeholder={t(
                    'tool-panel.alignment.geometry.search-all-reference-lines-without-geometry',
                )}
                wide={true}
                onChange={(event) => setTnSearchInput(event.target.value.toLowerCase())}
            />

            <TrackNumberCandidates
                candidates={candidates}
                selectedId={selectedTrackNumber?.id}
                lockedAlignmentId={lockedAlignmentIdOf<LayoutTrackNumberId>(linkingState)}
                isLoading={isLoading}
                emptyMessage={t('tool-panel.alignment.geometry.no-linkable-reference-lines')}
                onSelect={(trackNumberId) =>
                    onSelect({
                        trackNumbers: [trackNumberId],
                        locationTracks: [],
                    })
                }
            />
        </React.Fragment>
    );
};

export const GeometryAlignmentLinkingLocationTrackCandidates: React.FC<
    GeometryAlignmentLinkingLocationTrackCandidatesProps
> = ({
    geometryAlignment,
    layoutContext,
    locationTrackChangeTime,
    selectedLayoutLocationTrack,
    linkingState,
    onSelect,
    onShowAddLocationTrackDialog,
    disableAddButton,
    selectedPartOfUnfinishedSplit,
}) => {
    const { t } = useTranslation();
    const [locationTracks, setLocationTracks] = React.useState<LayoutLocationTrack[]>([]);
    const [layoutLocationTrackSearchInput, setLayoutLocationTrackSearchInput] =
        React.useState<string>('');
    const [isLoading, setIsLoading] = React.useState(true);

    React.useEffect(() => {
        if (geometryAlignment.boundingBox) {
            setIsLoading(true);

            Promise.all([
                getLocationTracksNear(
                    layoutContext,
                    expandBoundingBox(geometryAlignment.boundingBox, NEAR_TRACK_SEARCH_BUFFER),
                ),
                getNonLinkedLocationTracks(layoutContext).then((tracks) =>
                    tracks.sort(negComparator(fieldComparator((a) => a.id))),
                ),
            ])
                .then((trackGroups) => {
                    const uniqueLocationTracks = deduplicateById(trackGroups.flat(), (l) => l.id);

                    uniqueLocationTracks.sort(byDraftsFirst);
                    setLocationTracks(uniqueLocationTracks);
                })
                .finally(() => {
                    setIsLoading(false);
                });
        }
    }, [geometryAlignment.boundingBox, locationTrackChangeTime]);

    const candidates = React.useMemo(() => {
        const hasSearchInput = layoutLocationTrackSearchInput.length > 0;
        if (!hasSearchInput) return locationTracks;

        return locationTracks.filter(
            (track) =>
                track.id === selectedLayoutLocationTrack?.id ||
                track.name.toLowerCase().includes(layoutLocationTrackSearchInput),
        );
    }, [locationTracks, layoutLocationTrackSearchInput, selectedLayoutLocationTrack]);

    return (
        <React.Fragment>
            <div className={styles['geometry-alignment-infobox__search-container']}>
                <InfoboxText value={t('tool-panel.alignment.geometry.choose-location-track')} />
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    disabled={disableAddButton}
                    onClick={() => onShowAddLocationTrackDialog()}
                    qa-id="create-location-track-button"
                />
            </div>

            <TextField
                className={'geometry-alignment-infobox__alignments-container-search-field'}
                placeholder={t('tool-panel.alignment.geometry.search-location-tracks')}
                wide={true}
                onChange={(event) =>
                    setLayoutLocationTrackSearchInput(event.target.value.toLowerCase())
                }
            />

            <LocationTrackCandidates
                candidates={candidates}
                selectedId={selectedLayoutLocationTrack?.id}
                lockedAlignmentId={lockedAlignmentIdOf<LocationTrackId>(linkingState)}
                isLoading={isLoading}
                emptyMessage={t('tool-panel.alignment.geometry.no-linkable-location-tracks')}
                onSelect={(locationTrackId) =>
                    onSelect({
                        trackNumbers: [],
                        locationTracks: [locationTrackId],
                    })
                }
            />

            {selectedPartOfUnfinishedSplit && (
                <InfoboxContentSpread>
                    <MessageBox>
                        {t('tool-panel.alignment.geometry.part-of-unfinished-split', {
                            locationTrackName: selectedLayoutLocationTrack?.name,
                        })}
                    </MessageBox>
                </InfoboxContentSpread>
            )}
        </React.Fragment>
    );
};
