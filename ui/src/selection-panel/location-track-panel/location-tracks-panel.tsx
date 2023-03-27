import * as React from 'react';
import styles from './location-track.scss';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { createClassName } from 'vayla-design-lib/utils';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { useTranslation } from 'react-i18next';
import { compareByField } from 'utils/array-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';

type LocationTracksPanelProps = {
    locationTracks: LayoutLocationTrack[];
    onToggleLocationTrackSelection: (locationTrack: LocationTrackId) => void;
    selectedLocationTracks?: LocationTrackId[];
    canSelectLocationTrack: boolean;
    max?: number;
    showMoreMax?: number;
};

export const LocationTracksPanel: React.FC<LocationTracksPanelProps> = ({
    locationTracks,
    onToggleLocationTrackSelection,
    selectedLocationTracks,
    canSelectLocationTrack,
    max = 16,
    showMoreMax = 48,
}: LocationTracksPanelProps) => {
    const { t } = useTranslation();
    const [showMore, setShowMore] = React.useState(false);
    const [trackCount, setTrackCount] = React.useState(0);
    const [visibleTracks, setVisibleTracks] = React.useState<LayoutLocationTrack[]>([]);

    React.useEffect(() => {
        if (trackCount != locationTracks.length) {
            setShowMore(false);
        }
    }, [locationTracks]);

    React.useEffect(() => {
        if (locationTracks) {
            const sortedLocationTracks = [...locationTracks].sort((a, b) => {
                if (a.type === 'MAIN' && b.type !== 'MAIN') {
                    return -1;
                } else if (b.type === 'MAIN' && a.type !== 'MAIN') {
                    return 1;
                }

                return compareByField(a, b, (lt) => lt.id);
            });

            const visibleTracks = sortedLocationTracks.slice(0, showMore ? showMoreMax : max);

            setVisibleTracks(sortedLocationTracks.length <= showMoreMax ? visibleTracks : []);
            setTrackCount(sortedLocationTracks.length);
        } else {
            setVisibleTracks([]);
            setTrackCount(0);
        }
    }, [locationTracks, showMore]);

    return (
        <div>
            <ol
                className={styles['location-tracks-panel__location-tracks']}
                qa-id="location-tracks-list">
                {visibleTracks.map((track) => {
                    const isSelected = selectedLocationTracks?.some(
                        (selectedId) => selectedId == track.id,
                    );
                    const itemClassName = createClassName(
                        'location-tracks-panel__location-track',
                        canSelectLocationTrack &&
                            'location-tracks-panel__location-track--can-select',
                    );
                    return (
                        <li
                            key={track.id}
                            className={itemClassName}
                            onClick={() =>
                                canSelectLocationTrack && onToggleLocationTrackSelection(track.id)
                            }>
                            <LocationTrackBadge
                                locationTrack={track}
                                status={isSelected ? LocationTrackBadgeStatus.SELECTED : undefined}
                            />
                            <span>
                                <LocationTrackTypeLabel type={track.type} />
                            </span>
                        </li>
                    );
                })}
            </ol>

            {trackCount > showMoreMax && (
                <span className={styles['location-tracks-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {trackCount === 0 && (
                <span className={styles['location-tracks-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}

            {max < trackCount && trackCount <= showMoreMax && (
                <div className={styles['location-tracks-panel__show-more']}>
                    <ShowMoreButton onShowMore={() => setShowMore(!showMore)} expanded={showMore} />
                </div>
            )}
        </div>
    );
};
