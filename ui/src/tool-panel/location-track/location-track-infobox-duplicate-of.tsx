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

const LocationTrackDuplicateTrackNumberWarning: React.FC<
    LocationTrackDuplicateTrackNumberWarningProps
> = ({ differingTrackNumberName }) => {
    const { t } = useTranslation();
    return (
        <span
            title={t('tool-panel.location-track.duplicate-on-different-track-number', {
                trackNumber: differingTrackNumberName,
            })}
            className={styles['location-track-infobox-duplicate-of__on-different-track']}>
            <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
        </span>
    );
};

function isImplicitDuplicate(duplicate: LocationTrackDuplicate): boolean {
    return duplicate.duplicateStatus.duplicateOfId == undefined;
}

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
            {duplicatesOfLocationTrack.map((duplicate) => (
                <li key={duplicate.id}>
                    <LocationTrackLink
                        locationTrackId={duplicate.id}
                        locationTrackName={duplicate.name}
                    />
                    {isImplicitDuplicate(duplicate) && (
                        <span
                            className={
                                styles[
                                    'location-track-infobox-duplicate-of__partial-duplicate-info'
                                ]
                            }
                            title={t('tool-panel.location-track.implicit-duplicate-tooltip', {
                                trackName: duplicate.name,
                                otherTrackName: targetLocationTrack.name,
                            })}>
                            <Icons.Info size={IconSize.SMALL} />
                        </span>
                    )}
                    &nbsp;
                    {currentTrackNumberId !== duplicate.trackNumberId && (
                        <LocationTrackDuplicateTrackNumberWarning
                            differingTrackNumberName={getTrackNumberName(
                                trackNumbers,
                                duplicate.trackNumberId,
                            )}
                        />
                    )}
                </li>
            ))}
        </ul>
    ) : (
        <React.Fragment />
    );
};
