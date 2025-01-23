import * as React from 'react';
import styles from 'tool-panel/geometry-alignment/geometry-alignment-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    ReferenceLineBadge,
    ReferenceLineBadgeStatus,
} from 'geoviite-design-lib/alignment/reference-line-badge';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { useTranslation } from 'react-i18next';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import {
    LayoutLocationTrack,
    LayoutReferenceLine,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    getNonLinkedReferenceLines,
    getReferenceLinesNear,
} from 'track-layout/layout-reference-line-api';
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
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';

type GeometryAlignmentLinkingReferenceLineCandidatesProps = {
    geometryAlignment: AlignmentHeader;
    layoutContext: LayoutContext;
    trackNumberChangeTime: TimeStamp;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
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

type AlignmentRef = {
    id: LocationTrackId | ReferenceLineId;
    ref: React.RefObject<HTMLLIElement>;
};

type LayoutReferenceLineSearchResult = LayoutReferenceLine & {
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

function createReference(id: ReferenceLineId | LocationTrackId): AlignmentRef {
    return {
        id: id,
        ref: React.createRef(),
    };
}

const NEAR_TRACK_SEARCH_BUFFER = 10.0;

export const GeometryAlignmentLinkingReferenceLineCandidates: React.FC<
    GeometryAlignmentLinkingReferenceLineCandidatesProps
> = ({
    geometryAlignment,
    layoutContext,
    trackNumberChangeTime,
    selectedLayoutReferenceLine,
    linkingState,
    onSelect,
    onShowAddTrackNumberDialog,
    disableAddButton,
}) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(layoutContext, trackNumberChangeTime);
    const [referenceLineRefs, setReferenceLineRefs] = React.useState<AlignmentRef[]>([]);
    const [referenceLines, setReferenceLines] = React.useState<LayoutReferenceLineSearchResult[]>(
        [],
    );
    const [referenceLineSearchInput, setReferenceLineSearchInput] = React.useState<string>('');
    const [isLoading, setIsLoading] = React.useState(true);

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';

    React.useEffect(() => {
        if (geometryAlignment.boundingBox) {
            setIsLoading(true);

            Promise.all([
                getReferenceLinesNear(
                    layoutContext,
                    expandBoundingBox(geometryAlignment.boundingBox, NEAR_TRACK_SEARCH_BUFFER),
                ).then(toBoundingBoxSearchResults),

                getNonLinkedReferenceLines(layoutContext).then((lines) =>
                    lines.sort(negComparator(fieldComparator((line) => line.id))),
                ),
            ])
                .then((trackGroups) => {
                    const uniqueReferenceLines = deduplicateById(trackGroups.flat(), (l) => l.id);

                    uniqueReferenceLines.sort(byDraftsFirst);
                    setReferenceLines(uniqueReferenceLines);
                })
                .finally(() => {
                    setIsLoading(false);
                });
        }
    }, [geometryAlignment.boundingBox, trackNumberChangeTime]);

    React.useEffect(() => {
        const lines = referenceLines.map((rl) => createReference(rl.id));
        if (
            selectedLayoutReferenceLine &&
            !referenceLines.some((lt) => lt.id === selectedLayoutReferenceLine.id)
        ) {
            lines.push(createReference(selectedLayoutReferenceLine.id));
        }

        setReferenceLineRefs(lines);
    }, [selectedLayoutReferenceLine, referenceLines]);

    React.useEffect(() => {
        const ref = referenceLineRefs.find((r) => r.id === selectedLayoutReferenceLine?.id);

        if (ref) {
            ref.ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [selectedLayoutReferenceLine]);

    const referenceLineElements = referenceLines?.map((line) => {
        const isSelected = line.id === selectedLayoutReferenceLine?.id;
        const ref = referenceLineRefs.find((r) => r.id === line.id);
        const trackNumber = trackNumbers?.find((tn) => tn.id === line.trackNumberId);

        const trackNumberExists = ref && trackNumber;
        const hasSearchInput = referenceLineSearchInput.length > 0;
        const trackNumberMatchesSearchInput = trackNumber?.number
            .toLowerCase()
            .includes(referenceLineSearchInput);

        const trackNumberWithEmptyGeometryIsAlreadyPublished =
            !line.foundWithBoundingBox && line.hasOfficial && !line.isDraft;

        const displayTrackNumberOption =
            (hasSearchInput && trackNumberMatchesSearchInput) ||
            (!hasSearchInput && !trackNumberWithEmptyGeometryIsAlreadyPublished);

        if (!trackNumberExists || (!isSelected && !displayTrackNumberOption)) {
            return <React.Fragment key={line.id} />;
        }

        return (
            <li
                key={ref.id}
                className={styles['geometry-alignment-infobox__alignment']}
                onClick={() =>
                    onSelect({
                        trackNumbers: [line.trackNumberId],
                        locationTracks: [],
                    })
                }
                ref={ref.ref}>
                <ReferenceLineBadge
                    trackNumber={trackNumber}
                    status={
                        isSelected
                            ? ReferenceLineBadgeStatus.SELECTED
                            : ReferenceLineBadgeStatus.DEFAULT
                    }
                />
                {linkingInProgress && linkingState.layoutAlignment.id === line.id && (
                    <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                )}
            </li>
        );
    });

    const displayedReferenceLineElementsAmount = referenceLineElements.filter(
        (e) => e.type !== React.Fragment,
    ).length;

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
                onChange={(event) => setReferenceLineSearchInput(event.target.value.toLowerCase())}
            />

            <ul className={styles['geometry-alignment-infobox__alignments-container']}>
                {referenceLineElements}

                {isLoading && <Spinner />}

                {!isLoading && displayedReferenceLineElementsAmount === 0 && (
                    <span className={styles['geometry-alignment-infobox__no-matches']}>
                        {t('tool-panel.alignment.geometry.no-linkable-reference-lines')}
                    </span>
                )}
            </ul>
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
    const [locationTrackRefs, setLocationTrackRefs] = React.useState<AlignmentRef[]>([]);
    const [locationTracks, setLocationTracks] = React.useState<LayoutLocationTrack[]>([]);
    const [layoutLocationTrackSearchInput, setLayoutLocationTrackSearchInput] =
        React.useState<string>('');
    const [isLoading, setIsLoading] = React.useState(true);

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';

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

    React.useEffect(() => {
        const tracks = locationTracks.map((lt) => createReference(lt.id));
        if (
            selectedLayoutLocationTrack &&
            !locationTracks.some((lt) => lt.id === selectedLayoutLocationTrack.id)
        ) {
            tracks.push(createReference(selectedLayoutLocationTrack.id));
        }

        setLocationTrackRefs(tracks);
    }, [selectedLayoutLocationTrack, locationTracks]);

    React.useEffect(() => {
        const ref = locationTrackRefs.find((r) => r.id === selectedLayoutLocationTrack?.id);
        if (ref) {
            ref.ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [selectedLayoutLocationTrack]);

    const locationTrackElements = locationTracks?.map((track) => {
        const isSelected = track.id === selectedLayoutLocationTrack?.id;
        const ref = locationTrackRefs.find((r) => r.id === track.id);

        const alignmentExists = ref;
        const hasSearchInput = layoutLocationTrackSearchInput.length > 0;
        const layoutLocationTrackMatchesSearchInput = track.name
            .toLowerCase()
            .includes(layoutLocationTrackSearchInput);

        const displayLocationTrackOption =
            !hasSearchInput || (hasSearchInput && layoutLocationTrackMatchesSearchInput);

        if (!alignmentExists || (!isSelected && !displayLocationTrackOption)) {
            return <React.Fragment key={track.id} />;
        }

        return (
            <li
                key={ref.id}
                className={styles['geometry-alignment-infobox__alignment']}
                onClick={() =>
                    onSelect({
                        trackNumbers: [],
                        locationTracks: [track.id],
                    })
                }
                ref={ref.ref}>
                <LocationTrackBadge
                    locationTrack={track}
                    status={
                        isSelected
                            ? LocationTrackBadgeStatus.SELECTED
                            : LocationTrackBadgeStatus.DEFAULT
                    }
                />
                {linkingInProgress && linkingState.layoutAlignment.id === track.id && (
                    <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                )}
                <span>
                    <LocationTrackTypeLabel type={track.type} />
                </span>
            </li>
        );
    });

    const displayedLocationTrackElementsAmount = locationTrackElements.filter(
        (e) => e.type !== React.Fragment,
    ).length;

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

            <ul className={styles['geometry-alignment-infobox__alignments-container']}>
                {locationTrackElements}

                {isLoading && <Spinner />}

                {!isLoading && displayedLocationTrackElementsAmount === 0 && (
                    <span className={styles['geometry-alignment-infobox__no-matches']}>
                        {t('tool-panel.alignment.geometry.no-linkable-location-tracks')}
                    </span>
                )}
            </ul>

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
