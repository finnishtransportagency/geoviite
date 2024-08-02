import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackDuplicate,
} from 'track-layout/track-layout-model';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import styles from './location-track-infobox.scss';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { filterUniqueById } from 'utils/array-utils';

export type LocationTrackInfoboxDuplicateOfProps = {
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    targetLocationTrack: LayoutLocationTrack;
    existingDuplicate: LocationTrackDuplicate | undefined;
    duplicatesOfLocationTrack: LocationTrackDuplicate[] | undefined;
    currentTrackNumberId: LayoutTrackNumberId | undefined;
};

const getTrackNumberName = (
    trackNumbers: LayoutTrackNumber[] | undefined,
    trackNumberId: LayoutTrackNumberId,
) => trackNumbers?.find((tn) => tn.id === trackNumberId)?.number || '';

type LocationTrackDuplicateTrackNumberWarningProps = {
    differingTrackNumberName: string;
};

const LocationTrackDuplicateInfoIcon: React.FC<{
    msg: string;
    type: 'INFO' | 'ERROR';
}> = ({ msg, type = 'INFO' }) => {
    return (
        <span
            title={msg}
            className={createClassName(
                styles['location-track-infobox-duplicate-of__icon'],
                type === 'ERROR' && styles['location-track-infobox-duplicate-of__icon--error'],
            )}>
            {type === 'ERROR' ? (
                <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
            ) : (
                <Icons.Info color={IconColor.INHERIT} size={IconSize.SMALL} />
            )}
        </span>
    );
};

const LocationTrackDuplicateTrackNumberWarning: React.FC<
    LocationTrackDuplicateTrackNumberWarningProps
> = ({ differingTrackNumberName }) => {
    const { t } = useTranslation();
    return (
        <LocationTrackDuplicateInfoIcon
            msg={t('tool-panel.location-track.duplicate-on-different-track-number', {
                trackNumber: differingTrackNumberName,
            })}
            type={'ERROR'}
        />
    );
};

export const LocationTrackInfoboxDuplicateOf: React.FC<LocationTrackInfoboxDuplicateOfProps> = ({
    targetLocationTrack,
    existingDuplicate,
    duplicatesOfLocationTrack,
    currentTrackNumberId,
    layoutContext,
}: LocationTrackInfoboxDuplicateOfProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(layoutContext);
    return existingDuplicate ? (
        <React.Fragment>
            <LocationTrackLink
                locationTrackId={existingDuplicate.id}
                locationTrackName={existingDuplicate.name}
            />
            &nbsp;
            {currentTrackNumberId !== existingDuplicate.trackNumberId && (
                <LocationTrackDuplicateTrackNumberWarning
                    differingTrackNumberName={getTrackNumberName(
                        trackNumbers,
                        existingDuplicate.trackNumberId,
                    )}
                />
            )}
        </React.Fragment>
    ) : duplicatesOfLocationTrack ? (
        <ul className={styles['location-track-infobox-duplicate-of__ul']}>
            {duplicatesOfLocationTrack.filter(filterUniqueById((d) => d.id)).map((duplicate) => (
                <li key={duplicate.id}>
                    <LocationTrackLink
                        locationTrackId={duplicate.id}
                        locationTrackName={duplicate.name}
                    />
                    <span className={styles['location-track-infobox-duplicate-of__info-icons']}>
                        {duplicate.duplicateStatus.duplicateOfId == undefined && (
                            <LocationTrackDuplicateInfoIcon
                                msg={t('tool-panel.location-track.implicit-duplicate-tooltip', {
                                    trackName: duplicate.name,
                                    otherTrackName: targetLocationTrack.name,
                                })}
                                type={'INFO'}
                            />
                        )}
                        {currentTrackNumberId !== duplicate.trackNumberId && (
                            <LocationTrackDuplicateTrackNumberWarning
                                differingTrackNumberName={getTrackNumberName(
                                    trackNumbers,
                                    duplicate.trackNumberId,
                                )}
                            />
                        )}
                        {duplicate.duplicateStatus.match === 'NONE' && (
                            <LocationTrackDuplicateInfoIcon
                                msg={t(
                                    'tool-panel.location-track.non-overlapping-duplicate-tooltip',
                                    {
                                        trackName: duplicate.name,
                                        otherTrackName: targetLocationTrack.name,
                                    },
                                )}
                                type={'ERROR'}
                            />
                        )}
                    </span>
                </li>
            ))}
        </ul>
    ) : (
        <React.Fragment />
    );
};
