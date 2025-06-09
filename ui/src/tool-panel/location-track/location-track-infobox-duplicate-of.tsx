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
import {
    LocationTrackDuplicateInfoIcon,
    LocationTrackInfoboxDuplicateTrackEntry,
} from 'tool-panel/location-track/location-track-infobox-duplicate-track-entry';
import { useLocationTracks, useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';

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

export const LocationTrackInfoboxDuplicateOf: React.FC<LocationTrackInfoboxDuplicateOfProps> = ({
    targetLocationTrack,
    existingDuplicate,
    duplicatesOfLocationTrack,
    currentTrackNumberId,
    layoutContext,
}: LocationTrackInfoboxDuplicateOfProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(layoutContext);
    const explicitDuplicateLocationTrackNames = useLocationTracks(
        duplicatesOfLocationTrack
            ?.map((d) => d.duplicateStatus.duplicateOfId)
            ?.filter(filterNotEmpty) ?? [],
        layoutContext,
    );
    const duplicateTrackNumberWarning =
        existingDuplicate &&
        currentTrackNumberId &&
        currentTrackNumberId !== existingDuplicate?.trackNumberId
            ? t('tool-panel.location-track.duplicate-on-different-track-number', {
                  currentTrack: targetLocationTrack.name,
                  otherTrack: existingDuplicate.name,
                  currentTrackNumber: getTrackNumberName(trackNumbers, currentTrackNumberId),
                  otherTrackNumber: getTrackNumberName(
                      trackNumbers,
                      existingDuplicate.trackNumberId,
                  ),
              })
            : '';

    return existingDuplicate ? (
        <span title={duplicateTrackNumberWarning}>
            <LocationTrackLink
                locationTrackId={existingDuplicate.id}
                locationTrackName={existingDuplicate.name}
            />
            &nbsp;
            {currentTrackNumberId !== existingDuplicate.trackNumberId && (
                <LocationTrackDuplicateInfoIcon level={'ERROR'} />
            )}
        </span>
    ) : duplicatesOfLocationTrack ? (
        <ul className={styles['location-track-infobox-duplicate-of__ul']}>
            {duplicatesOfLocationTrack.filter(filterUniqueById((d) => d.id)).map((duplicate) => (
                <LocationTrackInfoboxDuplicateTrackEntry
                    key={duplicate.id}
                    targetLocationTrack={targetLocationTrack}
                    duplicate={duplicate}
                    explicitDuplicateLocationTrackNames={explicitDuplicateLocationTrackNames}
                    trackNumbers={trackNumbers}
                    currentTrackNumberId={currentTrackNumberId}
                />
            ))}
        </ul>
    ) : (
        <React.Fragment />
    );
};
