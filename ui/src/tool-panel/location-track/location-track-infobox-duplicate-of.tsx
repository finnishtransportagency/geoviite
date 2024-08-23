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
import { PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS } from 'tool-panel/location-track/split-store';

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
    type: 'INFO' | 'WARNING' | 'ERROR';
}> = ({ type = 'INFO' }) => {
    return (
        <span
            className={createClassName(
                styles['location-track-infobox-duplicate-of__icon'],
                type === 'WARNING' && styles['location-track-infobox-duplicate-of__icon--warning'],
                type === 'ERROR' && styles['location-track-infobox-duplicate-of__icon--error'],
            )}>
            {type === 'ERROR' || type === 'WARNING' ? (
                <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
            ) : (
                <Icons.Info color={IconColor.INHERIT} size={IconSize.SMALL} />
            )}
        </span>
    );
};

type NoticeLevel = 'ERROR' | 'WARNING' | 'INFO';
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
                const isFullButNotMarkedDuplicate =
                    duplicate.duplicateStatus.match === 'FULL' &&
                    duplicate.duplicateStatus.duplicateOfId == undefined;

                const isPartialDuplicate = duplicate.duplicateStatus.match === 'PARTIAL';
                const overLappingLength = duplicate.duplicateStatus.overlappingLength;
                const nonOverlappingLength =
                    (duplicate.length != undefined &&
                        overLappingLength != undefined &&
                        duplicate.length - overLappingLength) ||
                    undefined;
                const shortNonOverlappingLength =
                    nonOverlappingLength != undefined &&
                    nonOverlappingLength <
                        PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS;

                const notices: LocationTrackDuplicateNotice[] = [
                    isPartialDuplicate &&
                    overLappingLength !== undefined &&
                    nonOverlappingLength !== undefined
                        ? {
                              translationKey:
                                  'tool-panel.location-track.is-partial-duplicate-tooltip',
                              translationParams: {
                                  trackName: duplicate.name,
                                  overlappingLength: overLappingLength.toFixed(1),
                                  nonOverlappingLength: nonOverlappingLength.toFixed(1),
                              },
                              level: 'INFO' as NoticeLevel,
                          }
                        : undefined,
                    isFullButNotMarkedDuplicate
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
                    shortNonOverlappingLength && nonOverlappingLength != undefined
                        ? {
                              translationKey:
                                  'tool-panel.location-track.short-non-overlapping-length-warning',
                              translationParams: {
                                  trackName: duplicate.name,
                                  nonOverlappingLength: nonOverlappingLength.toFixed(1),
                              },
                              level: 'WARNING' as NoticeLevel,
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
                const warnings = notices
                    .filter((n) => n.level === 'WARNING')
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
                const warningsString =
                    warnings.length > 0
                        ? t('tool-panel.location-track.warnings', { count: warnings.length }) +
                          '\n' +
                          warnings.map((warning) => `- ${warning}`).join('\n')
                        : undefined;
                const infosString =
                    infos.length > 0
                        ? t('tool-panel.location-track.infos', { count: infos.length }) +
                          '\n' +
                          infos.map((info) => `- ${info}`).join('\n')
                        : undefined;

                const noticeListingTooltip =
                    t('tool-panel.location-track.location-track', { trackName: duplicate.name }) +
                    [errorsString, warningsString, infosString]
                        .filter(filterNotEmpty)
                        .map((str) => `\n\n${str}`)
                        .join('');

                const iconType =
                    errors.length > 0 ? 'ERROR' : warnings.length > 0 ? 'WARNING' : 'INFO';

                return (
                    <li key={duplicate.id}>
                        <span
                            className={
                                styles['location-track-infobox-duplicate-of__duplicate-track']
                            }
                            title={notices.length ? noticeListingTooltip : ''}>
                            <LocationTrackLink
                                locationTrackId={duplicate.id}
                                locationTrackName={duplicate.name}
                            />
                            {notices.length > 0 && (
                                <LocationTrackDuplicateInfoIcon type={iconType} />
                            )}
                        </span>
                    </li>
                );
            })}
        </ul>
    ) : (
        <React.Fragment />
    );
};
