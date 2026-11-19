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
import { fieldComparator } from 'utils/array-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';

type LocationTracksPanelProps = {
    locationTracks: LayoutLocationTrack[];
    onToggleLocationTrackSelection: (locationTrack: LocationTrackId) => void;
    selectedLocationTracks?: LocationTrackId[];
    canSelectLocationTrack: boolean;
    disabled?: boolean;
    max?: number;
    showMoreMax?: number;
};

export const LocationTracksPanel: React.FC<LocationTracksPanelProps> = ({
    locationTracks,
    onToggleLocationTrackSelection,
    selectedLocationTracks,
    canSelectLocationTrack,
    disabled,
    max = 16,
    showMoreMax = 48,
}: LocationTracksPanelProps) => {
    const { t } = useTranslation();
    const [userShowMore, setUserShowMore] = React.useState(false);
    const sortedLocationTracks = React.useMemo(
        () => [...locationTracks].sort(fieldComparator((lt) => lt.name)),
        [locationTracks],
    );
    const locationTrackCount = sortedLocationTracks.length;

    const canShowMore = max < locationTrackCount && locationTrackCount <= showMoreMax;
    // Forcefully show more because otherwise selected location tracks would be hidden
    const selectionHiddenByDefault =
        canShowMore &&
        (selectedLocationTracks ?? []).some(
            (selectedId) => sortedLocationTracks.findIndex((lt) => lt.id === selectedId) >= max,
        );
    const showMoreButton = canShowMore && !selectionHiddenByDefault;
    const showMore = canShowMore && (selectionHiddenByDefault || userShowMore);

    // Too many location tracks: show nothing. Otherwise show the first `max`, or all when expanded.
    const visibleTracks =
        locationTrackCount > showMoreMax
            ? []
            : showMore
              ? sortedLocationTracks
              : sortedLocationTracks.slice(0, max);

    return (
        <>
            {0 < locationTrackCount && locationTrackCount <= showMoreMax && (
                <ol
                    className={styles['location-tracks-panel__location-tracks']}
                    qa-id="location-tracks-list">
                    {visibleTracks.map((track) => {
                        const isSelected = selectedLocationTracks?.some(
                            (selectedId) => selectedId === track.id,
                        );
                        const itemClassName = createClassName(
                            'location-tracks-panel__location-track',
                            canSelectLocationTrack &&
                                'location-tracks-panel__location-track--can-select',
                        );
                        const status = () => {
                            if (disabled) {
                                return LocationTrackBadgeStatus.DISABLED;
                            } else if (isSelected) {
                                return LocationTrackBadgeStatus.SELECTED;
                            } else {
                                return undefined;
                            }
                        };

                        return (
                            <li
                                key={track.id}
                                className={itemClassName}
                                onClick={() =>
                                    canSelectLocationTrack &&
                                    !disabled &&
                                    onToggleLocationTrackSelection(track.id)
                                }>
                                <LocationTrackBadge locationTrack={track} status={status()} />
                                <span>
                                    <LocationTrackTypeLabel type={track.type} />
                                </span>
                            </li>
                        );
                    })}
                </ol>
            )}

            {locationTrackCount > showMoreMax && (
                <span className={styles['location-tracks-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {locationTrackCount === 0 && (
                <span className={styles['location-tracks-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}

            {showMoreButton && (
                <div className={styles['location-tracks-panel__show-more']}>
                    <ShowMoreButton
                        onShowMore={() => setUserShowMore(!userShowMore)}
                        expanded={showMore}
                    />
                </div>
            )}
        </>
    );
};
