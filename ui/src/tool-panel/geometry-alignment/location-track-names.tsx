import * as React from 'react';
import styles from './linked-items-list.scss';
import { useTranslation } from 'react-i18next';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useLocationTrackNames } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';

type LocationTrackNamesProps = {
    linkedLocationTracks: LayoutLocationTrack[];
    layoutContext: LayoutContext;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (locationTrackId: LocationTrackId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            locationTracks: [locationTrackId],
        });
}

const LocationTrackNames: React.FC<LocationTrackNamesProps> = ({
    linkedLocationTracks,
    layoutContext,
}) => {
    const { t } = useTranslation();
    const ltNames =
        useLocationTrackNames(
            linkedLocationTracks.map((lt) => lt.id),
            layoutContext,
        )?.toSorted((a, b) => a.name.localeCompare(b.name)) ?? [];

    const trackName =
        ltNames.length > 1
            ? t('tool-panel.location-track.track-name-short-plural')
            : t('tool-panel.location-track.track-name-short-singular');

    const clickAction = createSelectAction();

    return (
        <InfoboxField
            label={trackName}
            value={
                <div className={styles['linked-items-list']}>
                    {ltNames.map((ltName) => {
                        return (
                            <div className={styles['linked-items-list__list-item']} key={ltName.id}>
                                <LocationTrackBadge
                                    key={ltName.id}
                                    alignmentName={ltName.name}
                                    status={LocationTrackBadgeStatus.DEFAULT}
                                    onClick={() => clickAction(ltName.id)}
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
