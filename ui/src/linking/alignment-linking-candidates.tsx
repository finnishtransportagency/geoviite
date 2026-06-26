import * as React from 'react';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import {
    TrackNumberBadge,
    TrackNumberBadgeStatus,
} from 'geoviite-design-lib/alignment/track-number-badge';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import styles from 'tool-panel/geometry-alignment/geometry-alignment-infobox.scss';
import { createClassName } from 'vayla-design-lib/utils';

type SelectionCandidateProps = {
    isSelected: boolean;
    // if non-undefined, the candidate is disabled and the reason shown as tooltip
    disabledReason?: string;
    onClick: () => void;
    children: React.ReactNode;
};

const SelectionCandidate: React.FC<SelectionCandidateProps> = ({
    isSelected,
    disabledReason,
    onClick,
    children,
}) => {
    const ref = React.useRef<HTMLLIElement>(null);
    const disabled = disabledReason !== undefined;

    React.useEffect(() => {
        if (isSelected) {
            ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [isSelected]);

    return (
        <li
            ref={ref}
            className={createClassName(
                styles['geometry-alignment-infobox__alignment'],
                disabled && styles['geometry-alignment-infobox__alignment--disabled'],
            )}
            title={disabledReason}
            onClick={() => !disabled && onClick()}>
            {children}
        </li>
    );
};

type CandidatesListProps = {
    isLoading: boolean;
    isEmpty: boolean;
    emptyMessage: React.ReactNode;
    children: React.ReactNode;
    withTopBorder?: boolean;
};

const CandidatesList: React.FC<CandidatesListProps> = ({
    isLoading,
    isEmpty,
    emptyMessage,
    children,
    withTopBorder,
}) => (
    <ul
        className={createClassName(
            styles['geometry-alignment-infobox__alignments-container'],
            withTopBorder &&
                styles['geometry-alignment-infobox__alignments-container--with-top-border'],
        )}>
        {children}

        {isLoading && <Spinner />}

        {!isLoading && isEmpty && (
            <span className={styles['geometry-alignment-infobox__no-matches']}>{emptyMessage}</span>
        )}
    </ul>
);

type LocationTrackCandidatesProps = {
    candidates: LayoutLocationTrack[];
    selectedId: LocationTrackId | undefined;
    lockedAlignmentId?: LocationTrackId;
    isLoading: boolean;
    emptyMessage: React.ReactNode;
    withTopBorder?: boolean;
    // A returned reason disables selecting the candidate and is shown as its tooltip
    getDisabledReason?: (track: LayoutLocationTrack) => string | undefined;
    onSelect: (track: LocationTrackId) => void;
};

export const LocationTrackCandidates: React.FC<LocationTrackCandidatesProps> = ({
    candidates,
    selectedId,
    lockedAlignmentId,
    isLoading,
    emptyMessage,
    withTopBorder,
    getDisabledReason,
    onSelect,
}) => (
    <CandidatesList
        isLoading={isLoading}
        isEmpty={candidates.length === 0}
        emptyMessage={emptyMessage}
        withTopBorder={withTopBorder}>
        {candidates.map((track) => {
            const isSelected = track.id === selectedId;
            const disabledReason = getDisabledReason?.(track);
            return (
                <SelectionCandidate
                    key={track.id}
                    isSelected={isSelected}
                    disabledReason={disabledReason}
                    onClick={() => onSelect(track.id)}>
                    <LocationTrackBadge
                        locationTrack={track}
                        status={
                            isSelected
                                ? LocationTrackBadgeStatus.SELECTED
                                : disabledReason !== undefined
                                  ? LocationTrackBadgeStatus.DISABLED
                                  : LocationTrackBadgeStatus.DEFAULT
                        }
                    />
                    {lockedAlignmentId === track.id && (
                        <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                    )}
                    <span>
                        <LocationTrackTypeLabel type={track.type} />
                    </span>
                </SelectionCandidate>
            );
        })}
    </CandidatesList>
);

export type TrackNumberCandidate = {
    trackNumber: LayoutTrackNumber;
};

type TrackNumberCandidatesProps = {
    candidates: TrackNumberCandidate[];
    selectedId: LayoutTrackNumberId | undefined;
    lockedAlignmentId: LayoutTrackNumberId | undefined;
    isLoading: boolean;
    emptyMessage: React.ReactNode;
    onSelect: (trackNumberId: LayoutTrackNumberId) => void;
};

export const TrackNumberCandidates: React.FC<TrackNumberCandidatesProps> = ({
    candidates,
    selectedId,
    lockedAlignmentId,
    isLoading,
    emptyMessage,
    onSelect,
}) => (
    <CandidatesList
        isLoading={isLoading}
        isEmpty={candidates.length === 0}
        emptyMessage={emptyMessage}>
        {candidates.map(({ trackNumber }) => {
            const isSelected = trackNumber.id === selectedId;
            return (
                <SelectionCandidate
                    key={trackNumber.id}
                    isSelected={isSelected}
                    onClick={() => onSelect(trackNumber.id)}>
                    <TrackNumberBadge
                        trackNumber={trackNumber}
                        status={
                            isSelected
                                ? TrackNumberBadgeStatus.SELECTED
                                : TrackNumberBadgeStatus.DEFAULT
                        }
                    />
                    {lockedAlignmentId === trackNumber.id && (
                        <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                    )}
                </SelectionCandidate>
            );
        })}
    </CandidatesList>
);
