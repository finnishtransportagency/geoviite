import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackDuplicate,
} from 'track-layout/track-layout-model';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import styles from './location-track-infobox.scss';
import { PublishType, TimeStamp } from 'common/common-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

export type LocationTrackInfoboxDuplicateOfProps = {
    publishType: PublishType;
    changeTime: TimeStamp;
    existingDuplicate: LocationTrackDuplicate | undefined;
    duplicatesOfLocationTrack: LocationTrackDuplicate[] | undefined;
    currentTrackNumberId: LayoutTrackNumberId | undefined;
};

const findTrackNumber = (
    trackNumbers: LayoutTrackNumber[] | undefined,
    trackNumberId: LayoutTrackNumberId,
) => trackNumbers?.find((tn) => tn.id === trackNumberId)?.number;

export const LocationTrackInfoboxDuplicateOf: React.FC<LocationTrackInfoboxDuplicateOfProps> = ({
    existingDuplicate,
    duplicatesOfLocationTrack,
    currentTrackNumberId,
    publishType,
}: LocationTrackInfoboxDuplicateOfProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(publishType);
    return existingDuplicate ? (
        <React.Fragment>
            <LocationTrackLink
                locationTrackId={existingDuplicate.id}
                locationTrackName={existingDuplicate.name}
            />
            {currentTrackNumberId !== existingDuplicate.trackNumberId && (
                <span
                    className={styles['location-track-infobox-duplicate-of__on-different-track']}
                    title={t('tool-panel.location-track.duplicate-on-different-track-number', {
                        trackNumber: findTrackNumber(trackNumbers, existingDuplicate.trackNumberId),
                    })}>
                    &nbsp;
                    <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
                </span>
            )}
        </React.Fragment>
    ) : duplicatesOfLocationTrack ? (
        <ul className={styles['location-track-infobox-duplicate-of__ul']}>
            {duplicatesOfLocationTrack.map((duplicate) => (
                <li key={duplicate.id}>
                    <LocationTrackLink
                        locationTrackId={duplicate.id}
                        locationTrackName={duplicate.name}
                    />{' '}
                    {currentTrackNumberId !== duplicate.trackNumberId && (
                        <span
                            className={
                                styles['location-track-infobox-duplicate-of__on-different-track']
                            }
                            title={t(
                                'tool-panel.location-track.duplicate-on-different-track-number',
                                {
                                    trackNumber: findTrackNumber(
                                        trackNumbers,
                                        duplicate.trackNumberId,
                                    ),
                                },
                            )}>
                            &nbsp;
                            <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
                        </span>
                    )}
                </li>
            ))}
        </ul>
    ) : (
        <React.Fragment />
    );
};
