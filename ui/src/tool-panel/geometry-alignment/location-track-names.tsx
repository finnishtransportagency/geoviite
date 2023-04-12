import * as React from 'react';
import styles from './linked-items-list.scss';
import { useTranslation } from 'react-i18next';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { createEmptyItemCollections } from 'selection/selection-store';
import InfoboxField from 'tool-panel/infobox/infobox-field';

type LocationTrackNamesProps = {
    linkedLocationTracks: LayoutLocationTrack[];
};

function createSelectAction() {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    return (locationTrackId: LocationTrackId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            locationTracks: [locationTrackId],
        });
}

const LocationTrackNames: React.FC<LocationTrackNamesProps> = ({ linkedLocationTracks }) => {
    const { t } = useTranslation();

    const sortedLocationTracks = linkedLocationTracks.sort((a, b) => a.name.localeCompare(b.name));

    const trackName =
        sortedLocationTracks.length > 1
            ? t('tool-panel.location-track.track-name-short-plural')
            : t('tool-panel.location-track.track-name-short-singular');

    const clickAction = createSelectAction();

    return (
        <InfoboxField
            label={trackName}
            value={
                <div className={styles['linked-items-list']}>
                    {sortedLocationTracks.map((locationTrack) => {
                        return (
                            <div
                                className={styles['linked-items-list__list-item']}
                                key={locationTrack.id}>
                                <LocationTrackBadge
                                    key={locationTrack.id}
                                    locationTrack={locationTrack}
                                    status={LocationTrackBadgeStatus.DEFAULT}
                                    onClick={() => clickAction(locationTrack.id)}
                                />
                            </div>
                        );
                    })}
                </div>
            }
        />
    );
};

export default LocationTrackNames;
