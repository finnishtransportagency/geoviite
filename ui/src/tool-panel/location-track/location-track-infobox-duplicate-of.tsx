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
import { useLocationTracks, useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
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

const LocationTrackDuplicateInfoIcon: React.FC<{
    msg?: string | undefined;
    type: 'INFO' | 'ERROR';
}> = ({ type = 'INFO' }) => {
    return (
        <span
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

type NoticeLevel = 'ERROR' | 'INFO';
type LocationTrackDuplicateNotice = {
    translationKey: string;
    translationParams: object;
    level: NoticeLevel;
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
    const explicitDuplicateLocationTrackNames = useLocationTracks(
        duplicatesOfLocationTrack
            ?.map((d) => d.duplicateStatus.duplicateOfId)
            ?.filter(filterNotEmpty) ?? [],
        layoutContext,
    );
    const duplicateTrackNumberWarning =
        existingDuplicate && currentTrackNumberId !== existingDuplicate?.trackNumberId
            ? t('tool-panel.location-track.duplicate-on-different-track-number', {
                  trackNumber: getTrackNumberName(trackNumbers, existingDuplicate.trackNumberId),
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
                <LocationTrackDuplicateInfoIcon type={'ERROR'} />
            )}
        </span>
    ) : duplicatesOfLocationTrack ? (
        <ul className={styles['location-track-infobox-duplicate-of__ul']}>
            {duplicatesOfLocationTrack.filter(filterUniqueById((d) => d.id)).map((duplicate) => {
                const notices: LocationTrackDuplicateNotice[] = [
                    duplicate.duplicateStatus.duplicateOfId == undefined
                        ? {
                              translationKey:
                                  'tool-panel.location-track.implicit-duplicate-tooltip',
                              translationParams: {
                                  trackName: duplicate.name,
                                  otherTrackName: targetLocationTrack.name,
                              },
                              level: 'INFO' as NoticeLevel,
                          }
                        : undefined,
                    currentTrackNumberId !== duplicate.trackNumberId
                        ? {
                              translationKey:
                                  'tool-panel.location-track.duplicate-on-different-track-number',
                              translationParams: {
                                  trackNumber: getTrackNumberName(
                                      trackNumbers,
                                      duplicate.trackNumberId,
                                  ),
                              },
                              level: 'ERROR' as NoticeLevel,
                          }
                        : undefined,
                    targetLocationTrack.id !== duplicate.duplicateStatus.duplicateOfId &&
                    duplicate.duplicateStatus.duplicateOfId
                        ? {
                              translationKey:
                                  'tool-panel.location-track.overlapping-duplicate-of-different-track-tooltip',
                              translationParams: {
                                  trackName: targetLocationTrack.name,
                                  implicitDuplicateName: duplicate.name,
                                  explicitDuplicateName: explicitDuplicateLocationTrackNames.find(
                                      (d) => d.id === duplicate.duplicateStatus.duplicateOfId,
                                  )?.name,
                              },
                              level: 'ERROR' as NoticeLevel,
                          }
                        : undefined,
                    duplicate.duplicateStatus.match === 'NONE'
                        ? {
                              translationKey:
                                  'tool-panel.location-track.non-overlapping-duplicate-tooltip',
                              translationParams: {
                                  trackName: duplicate.name,
                                  otherTrackName: targetLocationTrack.name,
                              },
                              level: 'ERROR' as NoticeLevel,
                          }
                        : undefined,
                ].filter(filterNotEmpty);

                const errors = notices
                    .filter((n) => n.level === 'ERROR')
                    .map((n) => t(n.translationKey, { ...n.translationParams }));
                const infos = notices
                    .filter((n) => n.level === 'INFO')
                    .map((n) => t(n.translationKey, { ...n.translationParams }));

                const errorsString =
                    errors.length > 0
                        ? t('tool-panel.location-track.errors', { count: errors.length }) +
                          '\n' +
                          errors.map((err) => `- ${err}`).join('\n')
                        : undefined;
                const infosString =
                    infos.length > 0
                        ? t('tool-panel.location-track.infos', { count: infos.length }) +
                          '\n' +
                          infos.map((info) => `- ${info}`).join('\n')
                        : undefined;

                const noticeListingTooltip =
                    t('tool-panel.location-track.location-track', { trackName: duplicate.name }) +
                    (errorsString ? `\n${errorsString}` : '') +
                    (errorsString && infosString ? '\n' : '') +
                    (infosString ? `\n${infosString}` : '');

                return (
                    <li key={duplicate.id}>
                        <span title={notices.length ? noticeListingTooltip : ''}>
                            <LocationTrackLink
                                locationTrackId={duplicate.id}
                                locationTrackName={duplicate.name}
                            />
                            <span
                                className={
                                    styles['location-track-infobox-duplicate-of__info-icons']
                                }>
                                {notices.length > 0 && (
                                    <LocationTrackDuplicateInfoIcon
                                        type={
                                            notices.some((n) => n.level === 'ERROR')
                                                ? 'ERROR'
                                                : 'INFO'
                                        }
                                    />
                                )}
                            </span>
                        </span>
                    </li>
                );
            })}
        </ul>
    ) : (
        <React.Fragment />
    );
};
