import React from 'react';
import { LayoutSwitch, OperationalPoint } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { FieldValidationIssue, FieldValidationIssueType, validate } from 'utils/validation-utils';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { SwitchWithinOperationalPoint } from 'track-layout/layout-switch-api';

export type OperationalPointSaveRequestBase = {
    rinfIdOverride?: string;
};

export type LinkingDirection = 'linking' | 'unlinking';

const TOAST_MAX_NAMES = 10;

export type OperationalPointSwitchLinkingInfo = SwitchWithinOperationalPoint & {
    layoutSwitch: LayoutSwitch;
};

export function formatNamesForToast(names: string[]): string {
    const namesString = names.slice(0, TOAST_MAX_NAMES).join(', ');
    const ellipsis = names.length > TOAST_MAX_NAMES ? ` (+${names.length - TOAST_MAX_NAMES})` : '';
    return `${namesString}${ellipsis}`;
}

export function linkingSummaryTranslationInfo(
    linkedNames: string[],
    unlinkedNames: string[],
): { translationKey: string; params: Record<string, string> } {
    if (linkedNames.length > 0 && unlinkedNames.length > 0) {
        return {
            translationKey: 'linked-and-unlinked',
            params: {
                linkedNames: formatNamesForToast(linkedNames),
                unlinkedNames: formatNamesForToast(unlinkedNames),
            },
        };
    } else if (linkedNames.length > 0) {
        return {
            translationKey: 'linked',
            params: {
                linkedNames: formatNamesForToast(linkedNames),
            },
        };
    } else {
        return {
            translationKey: 'unlinked',
            params: {
                unlinkedNames: formatNamesForToast(unlinkedNames),
            },
        };
    }
}

export const Hide: React.FC<React.PropsWithChildren<{ when: boolean }>> = ({ when, children }) =>
    when ? <div /> : <div style={{ display: 'contents' }}>{children}</div>;

const RINF_ID_REGEX = /^[A-Z]{2}[0-9]{1,}$/;

export const withConditionalRinfIdOverride = <T,>(request: T, allowRinfIdOverride: boolean): T =>
    allowRinfIdOverride ? request : { ...request, rinfIdOverride: undefined };

export const validateRinfIdOverride = (
    rinfIdOverride: string | undefined,
): FieldValidationIssue<OperationalPointSaveRequestBase>[] =>
    [
        validate<OperationalPointSaveRequestBase>(
            rinfIdOverride === undefined ||
                rinfIdOverride.length < 2 ||
                rinfIdOverride.startsWith('EU'),
            {
                field: 'rinfIdOverride',
                reason: 'rinf-id-must-start-with-eu',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(
            rinfIdOverride === undefined || rinfIdOverride.length <= 12,
            {
                field: 'rinfIdOverride',
                reason: 'rinf-id-too-long',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(
            rinfIdOverride === undefined || RINF_ID_REGEX.test(rinfIdOverride),
            {
                field: 'rinfIdOverride',
                reason: 'invalid-rinf-id',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(!!rinfIdOverride && rinfIdOverride.length > 0, {
            field: 'rinfIdOverride',
            reason: 'mandatory-field',
            type: FieldValidationIssueType.ERROR,
        }),
    ].filter(filterNotEmpty);

export const hasSameRinfId = (
    rinfId: string | undefined,
    otherOperationalPoint: OperationalPoint,
): boolean => {
    return (
        !!rinfId &&
        otherOperationalPoint.state !== 'DELETED' &&
        !!otherOperationalPoint.rinfId &&
        isEqualIgnoreCase(otherOperationalPoint.rinfId, rinfId)
    );
};
