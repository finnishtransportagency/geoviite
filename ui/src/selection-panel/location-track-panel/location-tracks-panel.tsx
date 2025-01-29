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
    const [visibleTracks, setVisibleTracks] = React.useState<LayoutLocationTrack[]>([]);
    const [showMoreButton, setShowMoreButton] = React.useState(false);
    const locationTrackCount = locationTracks.length;

    React.useEffect(() => {
        const sortedLocationTracks = [...locationTracks].sort(fieldComparator((lt) => lt.name));

        if (sortedLocationTracks.length <= max) {
            //Just show everything since there aren't that many location tracks
            //Hide show more button because there's no need for it
            setShowMore(false);
            setShowMoreButton(false);
            setVisibleTracks(sortedLocationTracks);
        } else if (sortedLocationTracks.length <= showMoreMax) {
            const indexes =
                selectedLocationTracks?.map((selectedId) =>
                    sortedLocationTracks.findIndex((lt) => lt.id === selectedId),
                ) || [];

            if (Math.max(...indexes) >= max) {
                //Forcefully show more because otherwise selected location tracks would be hidden
                setShowMore(true);
                setShowMoreButton(false);
                setVisibleTracks(sortedLocationTracks);
            } else {
                //Normal use case, let the user decide whether to show more or less
                setShowMoreButton(true);
                setVisibleTracks(sortedLocationTracks.splice(0, showMore ? showMoreMax : max));
            }
        } else {
            //Way too many location tracks
            //Show nothing and hide show more button
            setVisibleTracks([]);
            setShowMore(false);
            setShowMoreButton(false);
        }
    }, [locationTracks, showMore]);

    return (
        <div>
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
                    <ShowMoreButton onShowMore={() => setShowMore(!showMore)} expanded={showMore} />
                </div>
            )}
        </div>
    );
};
