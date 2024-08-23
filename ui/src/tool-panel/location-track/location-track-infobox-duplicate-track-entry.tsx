import React from 'react';
import { useTranslation } from 'react-i18next';
import { filterNotEmpty } from 'utils/array-utils';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackDuplicate,
} from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

type LocationTrackInfoboxDuplicateTrackEntryProps = {
    duplicate: LocationTrackDuplicate;
    targetLocationTrack: LayoutLocationTrack;
    currentTrackNumberId: LayoutTrackNumberId | undefined;
    trackNumbers: LayoutTrackNumber[] | undefined;
    explicitDuplicateLocationTrackNames: LayoutLocationTrack[];
};

type NoticeLevel = 'ERROR' | 'INFO';
type LocationTrackDuplicateNotice = {
    translationKey: string;
    translationParams: object;
    level: NoticeLevel;
};

export const LocationTrackDuplicateInfoIcon: React.FC<{
    level: NoticeLevel;
}> = ({ level }) => {
    return (
        <span
            className={createClassName(
                styles['location-track-infobox-duplicate-of__icon'],
                level === 'ERROR' && styles['location-track-infobox-duplicate-of__icon--error'],
            )}>
            {level === 'ERROR' ? (
                <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
            ) : (
                <Icons.Info color={IconColor.INHERIT} size={IconSize.SMALL} />
            )}
        </span>
    );
};

function validateIsExplicitDuplicate(
    duplicate: LocationTrackDuplicate,
    targetLocationTrackName: string,
): LocationTrackDuplicateNotice | undefined {
    if (duplicate.duplicateStatus.duplicateOfId === undefined) {
        return {
            translationKey: 'tool-panel.location-track.implicit-duplicate-tooltip',
            translationParams: {
                trackName: duplicate.name,
                otherTrackName: targetLocationTrackName,
            },
            level: 'INFO',
        };
    } else {
        return undefined;
    }
}

function validateExplicitDuplicateOnSameTrackNumber(
    currentTrackNumberId: LayoutTrackNumberId | undefined,
    duplicate: LocationTrackDuplicate,
    trackNumbers: LayoutTrackNumber[] | undefined,
): LocationTrackDuplicateNotice | undefined {
    if (currentTrackNumberId && currentTrackNumberId !== duplicate.trackNumberId) {
        return {
            translationKey: 'tool-panel.location-track.duplicate-on-different-track-number',
            translationParams: {
                trackNumber: trackNumbers?.find((tn) => tn.id === currentTrackNumberId)?.number,
            },
            level: 'ERROR',
        };
    } else {
        return undefined;
    }
}

function validateExplicitDuplicateOfThisLocationTrack(
    targetLocationTrack: LayoutLocationTrack,
    duplicate: LocationTrackDuplicate,
    explicitDuplicateLocationTrackNames: LayoutLocationTrack[],
): LocationTrackDuplicateNotice | undefined {
    if (
        duplicate.duplicateStatus.duplicateOfId !== undefined &&
        targetLocationTrack.id !== duplicate.duplicateStatus.duplicateOfId
    ) {
        return {
            translationKey:
                'tool-panel.location-track.overlapping-duplicate-of-different-track-tooltip',
            translationParams: {
                trackName: targetLocationTrack.name,
                implicitDuplicateName: duplicate.name,
                explicitDuplicateName: explicitDuplicateLocationTrackNames.find(
                    (d) => d.id === duplicate.duplicateStatus.duplicateOfId,
                )?.name,
            },
            level: 'ERROR',
        };
    } else {
        return undefined;
    }
}

function validateDuplicateHasOverlappingGeometry(
    duplicate: LocationTrackDuplicate,
    targetLocationTrack: LayoutLocationTrack,
): LocationTrackDuplicateNotice | undefined {
    if (duplicate.duplicateStatus.match === 'NONE') {
        return {
            translationKey: 'tool-panel.location-track.non-overlapping-duplicate-tooltip',
            translationParams: {
                trackName: duplicate.name,
                otherTrackName: targetLocationTrack.name,
            },
            level: 'ERROR',
        };
    } else {
        return undefined;
    }
}

export const LocationTrackInfoboxDuplicateTrackEntry: React.FC<
    LocationTrackInfoboxDuplicateTrackEntryProps
> = ({
    duplicate,
    targetLocationTrack,
    currentTrackNumberId,
    trackNumbers,
    explicitDuplicateLocationTrackNames,
}: LocationTrackInfoboxDuplicateTrackEntryProps) => {
    const { t } = useTranslation();

    const notices: LocationTrackDuplicateNotice[] = [
        validateIsExplicitDuplicate(duplicate, targetLocationTrack.name),
        validateExplicitDuplicateOnSameTrackNumber(currentTrackNumberId, duplicate, trackNumbers),
        validateExplicitDuplicateOfThisLocationTrack(
            targetLocationTrack,
            duplicate,
            explicitDuplicateLocationTrackNames,
        ),
        validateDuplicateHasOverlappingGeometry(duplicate, targetLocationTrack),
    ].filter(filterNotEmpty);

    const createNoticeTooltipFragmentByLevel = (
        allNotices: LocationTrackDuplicateNotice[],
        level: NoticeLevel,
    ): string => {
        const noticesForLevel = allNotices
            .filter((n) => n.level === level)
            .map((n) => t(n.translationKey, { ...n.translationParams }));

        const noticeHeading =
            level === 'ERROR'
                ? t('tool-panel.location-track.errors', { count: noticesForLevel.length })
                : t('tool-panel.location-track.infos', { count: noticesForLevel.length });

        return noticesForLevel.length > 0
            ? noticeHeading + '\n' + noticesForLevel.map((notice) => `- ${notice}`).join('\n')
            : '';
    };

    function createDuplicateNoticeListingTooltip(
        notices: LocationTrackDuplicateNotice[],
        trackName: string,
    ): string {
        const errorsString = createNoticeTooltipFragmentByLevel(notices, 'ERROR');
        const infosString = createNoticeTooltipFragmentByLevel(notices, 'INFO');

        return (
            t('tool-panel.location-track.location-track', { trackName }) +
            (errorsString ? `\n${errorsString}` : '') +
            (errorsString && infosString ? '\n' : '') +
            (infosString ? `\n${infosString}` : '')
        );
    }

    const noticeListingTooltip = createDuplicateNoticeListingTooltip(notices, duplicate.name);

    return (
        <li key={duplicate.id}>
            <span title={notices.length ? noticeListingTooltip : ''}>
                <LocationTrackLink
                    locationTrackId={duplicate.id}
                    locationTrackName={duplicate.name}
                />
                <span className={styles['location-track-infobox-duplicate-of__info-icons']}>
                    {notices.length > 0 && (
                        <LocationTrackDuplicateInfoIcon
                            level={notices.some((n) => n.level === 'ERROR') ? 'ERROR' : 'INFO'}
                        />
                    )}
                </span>
            </span>
        </li>
    );
};
