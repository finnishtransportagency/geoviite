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
    LayoutReferenceLine,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import styles from 'tool-panel/geometry-alignment/geometry-alignment-infobox.scss';

type SelectionCandidateProps = {
    isSelected: boolean;
    onClick: () => void;
    children: React.ReactNode;
};

const SelectionCandidate: React.FC<SelectionCandidateProps> = ({
    isSelected,
    onClick,
    children,
}) => {
    const ref = React.useRef<HTMLLIElement>(null);

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
            className={styles['geometry-alignment-infobox__alignment']}
            onClick={onClick}>
            {children}
        </li>
    );
};

type CandidatesListProps = {
    isLoading: boolean;
    isEmpty: boolean;
    emptyMessage: React.ReactNode;
    children: React.ReactNode;
};

const CandidatesList: React.FC<CandidatesListProps> = ({
    isLoading,
    isEmpty,
    emptyMessage,
    children,
}) => (
    <ul className={styles['geometry-alignment-infobox__alignments-container']}>
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
    lockedAlignmentId: LocationTrackId | undefined;
    isLoading: boolean;
    emptyMessage: React.ReactNode;
    onSelect: (track: LocationTrackId) => void;
};

export const LocationTrackCandidates: React.FC<LocationTrackCandidatesProps> = ({
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
        {candidates.map((track) => {
            const isSelected = track.id === selectedId;
            return (
                <SelectionCandidate
                    key={track.id}
                    isSelected={isSelected}
                    onClick={() => onSelect(track.id)}>
                    <LocationTrackBadge
                        locationTrack={track}
                        status={
                            isSelected
                                ? LocationTrackBadgeStatus.SELECTED
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

export type ReferenceLineCandidate = {
    referenceLine: LayoutReferenceLine;
    trackNumber: LayoutTrackNumber;
};

type ReferenceLineCandidatesProps = {
    candidates: ReferenceLineCandidate[];
    selectedId: ReferenceLineId | undefined;
    lockedAlignmentId: ReferenceLineId | undefined;
    isLoading: boolean;
    emptyMessage: React.ReactNode;
    onSelect: (trackNumberId: LayoutTrackNumberId) => void;
};

export const ReferenceLineCandidates: React.FC<ReferenceLineCandidatesProps> = ({
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
        {candidates.map(({ referenceLine, trackNumber }) => {
            const isSelected = referenceLine.id === selectedId;
            return (
                <SelectionCandidate
                    key={referenceLine.id}
                    isSelected={isSelected}
                    onClick={() => onSelect(referenceLine.trackNumberId)}>
                    <TrackNumberBadge
                        trackNumber={trackNumber}
                        status={
                            isSelected
                                ? TrackNumberBadgeStatus.SELECTED
                                : TrackNumberBadgeStatus.DEFAULT
                        }
                    />
                    {lockedAlignmentId === referenceLine.id && (
                        <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                    )}
                </SelectionCandidate>
            );
        })}
    </CandidatesList>
);
