import React from 'react';
import { LayoutBranch, LayoutContext, TimeStamp } from 'common/common-model';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { ChangeTimes } from 'common/common-slice';
import { useRateLimitedTwoPartEffect } from 'utils/react-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { filterNotEmpty, indexIntoMap, partitionBy } from 'utils/array-utils';
import { FieldValidationIssue, FieldValidationIssueType, validate } from 'utils/validation-utils';

export type OperationalPointSaveRequestBase = {
    rinfCodeOverride?: string;
};

export type UseLinkingHookResult<Id, T, ItemAssociation> = {
    isInitializing: boolean;
    itemAssociation: ItemAssociation | undefined;
    linkedItems: T[];
    unlinkedItems: T[];
    isEditing: boolean;
    hasChanges: boolean;
    startEditing: () => void;
    cancelEditing: () => void;
    saveEdits: () => Promise<{ linkedNames: string[]; unlinkedNames: string[] }>;
    setLinks: (ids: Id[], direction: LinkingDirection) => void;
};

export function createUseLinkingHook<Id, T extends { id: Id; name: string }, ItemAssociation>(
    fetchOperationalPointItems: (
        context: LayoutContext,
        operationalPointId: OperationalPointId,
    ) => Promise<{ itemAssociation: ItemAssociation; items: T[] }>,
    getItemOperationalPoints: (item: T) => OperationalPointId[],
    getChangeTime: (changeTimes: ChangeTimes) => TimeStamp,
    updateChangeTimes: () => Promise<unknown>,
    linkToOperationalPoint: (
        branch: LayoutBranch,
        ids: Id[],
        operationalPointId: OperationalPointId,
    ) => Promise<Id[]>,
    unlinkFromOperationalPoint: (
        branch: LayoutBranch,
        ids: Id[],
        operationalPointId: OperationalPointId,
    ) => Promise<Id[]>,
): (
    layoutContext: LayoutContext,
    operationalPoint: OperationalPoint,
    changeTimes: ChangeTimes,
) => UseLinkingHookResult<Id, T, ItemAssociation> {
    return (layoutContext, operationalPoint, changeTimes) => {
        const [isInitializing, setIsInitializing] = React.useState(true);
        const [isEditing, setIsEditing] = React.useState(false);
        const [editedLinkedIds, setEditedLinkedIds] = React.useState<Set<Id>>(new Set());

        const [itemAssociation, setItemAssociation] = React.useState<ItemAssociation | undefined>(
            undefined,
        );
        const [displayItems, setDisplayItems] = React.useState<T[]>([]);

        useRateLimitedTwoPartEffect(
            () => fetchOperationalPointItems(layoutContext, operationalPoint.id),
            ({ itemAssociation, items }) => {
                setIsInitializing(false);
                setItemAssociation(itemAssociation);
                setDisplayItems(items);
            },
            1000,
            [
                layoutContext,
                operationalPoint.id,
                getMaxTimestamp(changeTimes.operationalPoints, getChangeTime(changeTimes)),
            ],
        );

        const isItemLinked = React.useCallback(
            (item: T): boolean =>
                getItemOperationalPoints(item).some((op) => op === operationalPoint.id),
            [operationalPoint.id],
        );

        const originalLinkedIds = React.useMemo(
            () => new Set(displayItems.filter(isItemLinked).map((item) => item.id)),
            [displayItems, isItemLinked],
        );

        const currentLinkedIds = isEditing ? editedLinkedIds : originalLinkedIds;

        const [linkedItems, unlinkedItems] = partitionBy(displayItems, (item) =>
            currentLinkedIds.has(item.id),
        );

        const hasChanges = React.useMemo(() => {
            if (!isEditing) return false;
            if (editedLinkedIds.size !== originalLinkedIds.size) return true;
            for (const id of editedLinkedIds) {
                if (!originalLinkedIds.has(id)) return true;
            }
            return false;
        }, [isEditing, editedLinkedIds, originalLinkedIds]);

        const startEditing = React.useCallback(() => {
            setEditedLinkedIds(new Set(originalLinkedIds));
            setIsEditing(true);
        }, [originalLinkedIds]);

        const cancelEditing = React.useCallback(() => {
            setIsEditing(false);
            setEditedLinkedIds(new Set());
        }, []);

        const setLinks = React.useCallback(
            (ids: Id[], direction: LinkingDirection) => {
                if (!isEditing) return;
                setEditedLinkedIds((prev) => {
                    const next = new Set(prev);
                    ids.forEach((id) => {
                        if (direction === 'linking') {
                            next.add(id);
                        } else {
                            next.delete(id);
                        }
                    });
                    return next;
                });
            },
            [isEditing],
        );

        const saveEdits = React.useCallback(async () => {
            if (!isEditing) return { linkedNames: [], unlinkedNames: [] };

            const idsToLink: Id[] = [...editedLinkedIds].filter((id) => !originalLinkedIds.has(id));
            const idsToUnlink: Id[] = [...originalLinkedIds].filter(
                (id) => !editedLinkedIds.has(id),
            );

            const itemsById = indexIntoMap(displayItems);

            let linkedNames: string[] = [];
            let unlinkedNames: string[] = [];

            try {
                if (idsToLink.length > 0) {
                    const affectedIds = await linkToOperationalPoint(
                        layoutContext.branch,
                        idsToLink,
                        operationalPoint.id,
                    );
                    linkedNames = affectedIds.map((id) => itemsById.get(id)?.name ?? '');
                }

                if (idsToUnlink.length > 0) {
                    const affectedIds = await unlinkFromOperationalPoint(
                        layoutContext.branch,
                        idsToUnlink,
                        operationalPoint.id,
                    );
                    unlinkedNames = affectedIds.map((id) => itemsById.get(id)?.name ?? '');
                }

                await updateChangeTimes();
                // avoid flashing old state after saving
                await fetchOperationalPointItems(layoutContext, operationalPoint.id);
                setIsEditing(false);
                setEditedLinkedIds(new Set());
            } catch {
                await updateChangeTimes();
            }

            return { linkedNames, unlinkedNames };
        }, [
            isEditing,
            editedLinkedIds,
            originalLinkedIds,
            displayItems,
            layoutContext.branch,
            operationalPoint.id,
        ]);

        return {
            isInitializing,
            itemAssociation,
            linkedItems,
            unlinkedItems,
            isEditing,
            hasChanges,
            startEditing,
            cancelEditing,
            saveEdits,
            setLinks,
        };
    };
}

export type LinkingDirection = 'linking' | 'unlinking';

const TOAST_MAX_NAMES = 10;

export function formatNamesForToast(names: string[]): string {
    const namesString = names.slice(0, TOAST_MAX_NAMES).join(', ');
    const ellipsis = names.length > TOAST_MAX_NAMES ? ` (+${names.length - TOAST_MAX_NAMES})` : '';
    return `${namesString}${ellipsis}`;
}

export function formatLinkingToast(
    linkedNames: string[],
    unlinkedNames: string[],
    t: (key: string, options?: Record<string, unknown>) => string,
    translationPrefix: string,
    operationalPointName: string,
): string | undefined {
    if (linkedNames.length === 0 && unlinkedNames.length === 0) {
        return undefined;
    }

    const prefix = t(`${translationPrefix}.linking-success-toast-prefix`, { operationalPointName });

    const linkedPart =
        linkedNames.length === 1
            ? t(`${translationPrefix}.single-linking-success-toast`, { name: linkedNames[0] })
            : linkedNames.length > 1
              ? t(`${translationPrefix}.multiple-linking-success-toast`, {
                    names: formatNamesForToast(linkedNames),
                })
              : '';

    const unlinkedPart =
        unlinkedNames.length === 1
            ? t(`${translationPrefix}.single-unlinking-success-toast`, { name: unlinkedNames[0] })
            : unlinkedNames.length > 1
              ? t(`${translationPrefix}.multiple-unlinking-success-toast`, {
                    names: formatNamesForToast(unlinkedNames),
                })
              : '';

    const separator = linkedPart && unlinkedPart ? '; ' : '';
    return prefix + linkedPart + separator + unlinkedPart;
}

export const Hide: React.FC<React.PropsWithChildren<{ when: boolean }>> = ({ when, children }) => (
    <div style={{ display: 'contents', ...(when && { visibility: 'hidden' }) }}>{children}</div>
);

const RINF_CODE_REGEX = /^[A-Z]{2}[0-9]{0,}$/;

export const withConditionalRinfCodeOverride = <T,>(
    request: T,
    allowRinfCodeOverride: boolean,
): T => (allowRinfCodeOverride ? request : { ...request, rinfCodeOverride: undefined });

export const validateRinfCodeOverride = (
    rinfCodeOverride: string | undefined,
): FieldValidationIssue<OperationalPointSaveRequestBase>[] =>
    [
        validate<OperationalPointSaveRequestBase>(
            rinfCodeOverride === undefined ||
                rinfCodeOverride.length < 2 ||
                rinfCodeOverride.startsWith('EU'),
            {
                field: 'rinfCodeOverride',
                reason: 'rinf-code-must-start-with-eu',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(
            rinfCodeOverride === undefined || rinfCodeOverride.length <= 12,
            {
                field: 'rinfCodeOverride',
                reason: 'rinf-code-too-long',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(
            rinfCodeOverride === undefined || RINF_CODE_REGEX.test(rinfCodeOverride),
            {
                field: 'rinfCodeOverride',
                reason: 'invalid-rinf-code',
                type: FieldValidationIssueType.ERROR,
            },
        ),
        validate<OperationalPointSaveRequestBase>(
            !!rinfCodeOverride && rinfCodeOverride.length > 0,
            {
                field: 'rinfCodeOverride',
                reason: 'mandatory-field',
                type: FieldValidationIssueType.ERROR,
            },
        ),
    ].filter(filterNotEmpty);
