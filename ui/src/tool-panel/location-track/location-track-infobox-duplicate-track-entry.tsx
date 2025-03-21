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
import { PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS } from 'tool-panel/location-track/split-store';

type LocationTrackInfoboxDuplicateTrackEntryProps = {
    duplicate: LocationTrackDuplicate;
    targetLocationTrack: LayoutLocationTrack;
    currentTrackNumberId: LayoutTrackNumberId | undefined;
    trackNumbers: LayoutTrackNumber[] | undefined;
    explicitDuplicateLocationTrackNames: LayoutLocationTrack[];
};

type NoticeLevel = 'ERROR' | 'WARNING' | 'INFO';
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
                level === 'WARNING' && styles['location-track-infobox-duplicate-of__icon--warning'],
                level === 'ERROR' && styles['location-track-infobox-duplicate-of__icon--error'],
            )}>
            {level === 'ERROR' || level === 'WARNING' ? (
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
    if (
        duplicate.duplicateStatus.match === 'FULL' &&
        duplicate.duplicateStatus.duplicateOfId === undefined
    ) {
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
    currentLocationTrackName: string,
    duplicate: LocationTrackDuplicate,
    trackNumbers: LayoutTrackNumber[] | undefined,
): LocationTrackDuplicateNotice | undefined {
    if (currentTrackNumberId !== undefined && currentTrackNumberId !== duplicate.trackNumberId) {
        return {
            translationKey: 'tool-panel.location-track.duplicate-on-different-track-number',
            translationParams: {
                currentTrack: currentLocationTrackName,
                currentTrackNumber: trackNumbers?.find((tn) => tn.id === currentTrackNumberId)
                    ?.number,
                otherTrackNumber: trackNumbers?.find((tn) => tn.id === duplicate?.trackNumberId)
                    ?.number,
                otherTrack: duplicate.name,
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

function validateIsPartialDuplicate(
    duplicate: LocationTrackDuplicate,
): LocationTrackDuplicateNotice | undefined {
    const isPartialDuplicate = duplicate.duplicateStatus.match === 'PARTIAL';
    const overLappingLength = duplicate.duplicateStatus.overlappingLength;
    const nonOverlappingLength =
        (overLappingLength !== undefined && duplicate.length - overLappingLength) || undefined;
    if (
        isPartialDuplicate &&
        overLappingLength !== undefined &&
        nonOverlappingLength !== undefined
    ) {
        return {
            translationKey: 'tool-panel.location-track.is-partial-duplicate-tooltip',
            translationParams: {
                trackName: duplicate.name,
                overlappingLength: overLappingLength.toFixed(1),
                nonOverlappingLength: nonOverlappingLength.toFixed(1),
            },
            level: 'INFO',
        };
    } else {
        return undefined;
    }
}

function validateHasShortNonOverlappingLength(
    duplicate: LocationTrackDuplicate,
): LocationTrackDuplicateNotice | undefined {
    const overLappingLength = duplicate.duplicateStatus.overlappingLength;
    const nonOverlappingLength =
        (overLappingLength !== undefined && duplicate.length - overLappingLength) || undefined;
    const shortNonOverlappingLength =
        nonOverlappingLength !== undefined &&
        nonOverlappingLength <
            PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS;
    if (shortNonOverlappingLength && nonOverlappingLength !== undefined) {
        return {
            translationKey: 'tool-panel.location-track.short-non-overlapping-length-warning',
            translationParams: {
                trackName: duplicate.name,
                nonOverlappingLength: nonOverlappingLength.toFixed(1),
            },
            level: 'WARNING',
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
        validateExplicitDuplicateOnSameTrackNumber(
            currentTrackNumberId,
            targetLocationTrack.name,
            duplicate,
            trackNumbers,
        ),
        validateExplicitDuplicateOfThisLocationTrack(
            targetLocationTrack,
            duplicate,
            explicitDuplicateLocationTrackNames,
        ),
        validateDuplicateHasOverlappingGeometry(duplicate, targetLocationTrack),
        validateIsPartialDuplicate(duplicate),
        validateHasShortNonOverlappingLength(duplicate),
    ].filter(filterNotEmpty);

    const createNoticeTooltipFragmentByLevel = (
        allNotices: LocationTrackDuplicateNotice[],
        level: NoticeLevel,
    ): string => {
        const noticesForLevel = allNotices
            .filter((n) => n.level === level)
            .map((n) => t(n.translationKey, { ...n.translationParams }));

        const termKeyByLevel = {
            ERROR: 'tool-panel.location-track.errors',
            WARNING: 'tool-panel.location-track.warnings',
            INFO: 'tool-panel.location-track.infos',
        };
        const headingTermKey = termKeyByLevel[level];
        const noticeHeading = t(headingTermKey, { count: noticesForLevel.length });

        return noticesForLevel.length > 0
            ? noticeHeading + '\n' + noticesForLevel.map((notice) => `- ${notice}`).join('\n')
            : '';
    };

    function createDuplicateNoticeListingTooltip(
        notices: LocationTrackDuplicateNotice[],
        trackName: string,
    ): string {
        const errorsString = createNoticeTooltipFragmentByLevel(notices, 'ERROR');
        const warningsString = createNoticeTooltipFragmentByLevel(notices, 'WARNING');
        const infosString = createNoticeTooltipFragmentByLevel(notices, 'INFO');

        const noticesStr = [errorsString, warningsString, infosString]
            .filter((str) => str !== '')
            .map((str) => `\n\n${str}`)
            .join('');
        return t('tool-panel.location-track.location-track', { trackName }) + noticesStr;
    }

    const noticeListingTooltip = createDuplicateNoticeListingTooltip(notices, duplicate.name);
    const iconType =
        (['ERROR', 'WARNING', 'INFO'] as NoticeLevel[]).find((level) =>
            notices.some((notice) => notice.level === level),
        ) || 'INFO';
    return (
        <li key={duplicate.id}>
            <span
                className={styles['location-track-infobox-duplicate-of__duplicate-track']}
                title={notices.length ? noticeListingTooltip : ''}>
                <LocationTrackLink
                    locationTrackId={duplicate.id}
                    locationTrackName={duplicate.name}
                />
                {notices.length > 0 && <LocationTrackDuplicateInfoIcon level={iconType} />}
            </span>
        </li>
    );
};
