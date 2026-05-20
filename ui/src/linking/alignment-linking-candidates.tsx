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

function useScrollSelectedIntoView<TId extends string>(selectedId: TId | undefined) {
    const elementsRef = React.useRef(new Map<TId, HTMLLIElement>());

    const setRef = (id: TId) => (el: HTMLLIElement | null) => {
        if (el) elementsRef.current.set(id, el);
        else elementsRef.current.delete(id);
    };

    React.useEffect(() => {
        if (selectedId === undefined) return;
        elementsRef.current.get(selectedId)?.scrollIntoView({
            behavior: 'smooth',
            block: 'nearest',
        });
    }, [selectedId]);

    return setRef;
}

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
}) => {
    const setRef = useScrollSelectedIntoView<LocationTrackId>(selectedId);

    return (
        <CandidatesList
            isLoading={isLoading}
            isEmpty={candidates.length === 0}
            emptyMessage={emptyMessage}>
            {candidates.map((track) => {
                const isSelected = track.id === selectedId;
                return (
                    <li
                        key={track.id}
                        ref={setRef(track.id)}
                        className={styles['geometry-alignment-infobox__alignment']}
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
                    </li>
                );
            })}
        </CandidatesList>
    );
};

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
}) => {
    const setRef = useScrollSelectedIntoView<ReferenceLineId>(selectedId);

    return (
        <CandidatesList
            isLoading={isLoading}
            isEmpty={candidates.length === 0}
            emptyMessage={emptyMessage}>
            {candidates.map(({ referenceLine, trackNumber }) => {
                const isSelected = referenceLine.id === selectedId;
                return (
                    <li
                        key={referenceLine.id}
                        ref={setRef(referenceLine.id)}
                        className={styles['geometry-alignment-infobox__alignment']}
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
                    </li>
                );
            })}
        </CandidatesList>
    );
};
