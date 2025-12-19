import React from 'react';
import { LayoutBranch, LayoutContext, TimeStamp } from 'common/common-model';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { ChangeTimes } from 'common/common-slice';
import { useRateLimitedTwoPartEffect, useSetState } from 'utils/react-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { partitionBy } from 'utils/array-utils';
import { expectDefined } from 'utils/type-utils';

export function createUseLinkingHook<Id, T extends { id: Id }, ItemAssociation>(
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
) => {
    isInitializing: boolean;
    itemAssociation: ItemAssociation | undefined;
    linkedItems: T[];
    unlinkedItems: T[];
    linkItems: (items: { id: Id; name: string }[]) => Promise<string[]>;
    unlinkItems: (items: { id: Id; name: string }[], idsToImmediatelyToss: []) => Promise<string[]>;
} {
    return (layoutContext, operationalPoint, changeTimes) => {
        const [isInitializing, setIsInitializing] = React.useState(true);

        // in-flight ids: ones where we actually have a request in flight.
        const [linkingInFlight, addLinkingInFlight, deleteFromLinkingInFlight] = useSetState<Id>();
        const [unlinkingInFlight, addUnlinkingInFlight, deleteFromUnlinkingInFlight] =
            useSetState<Id>();

        // preliminary ids: ones where the request to link/unlink has successfully returned, so we know the operation
        // is done; but we haven't had a fetchOperationalPointItems call return yet
        const [
            preliminaryLinked,
            addPreliminaryLinked,
            deletePreliminaryLinked,
            setPreliminaryLinked,
        ] = useSetState<Id>();
        const [
            preliminaryUnlinked,
            addPreliminaryUnlinked,
            deletePreliminaryUnlinked,
            setPreliminaryUnlinked,
        ] = useSetState<Id>();

        // tossed ids: ones that have been unlinked, and we know we want to keep them unlinked and so won't want to
        // offer them for linking again; so we need to keep them hidden until the next fetchOperationalPointItems
        // returns
        const [tossed, addTossed, _, setTossed] = useSetState<Id>();

        const [itemAssociation, setItemAssociation] = React.useState<ItemAssociation | undefined>(
            undefined,
        );

        const [displayItems, setDisplayItems] = React.useState<T[]>([]);
        useRateLimitedTwoPartEffect(
            () => fetchOperationalPointItems(layoutContext, operationalPoint.id),
            ({ itemAssociation, items }) => {
                setIsInitializing(false);
                setPreliminaryLinked(new Set());
                setPreliminaryUnlinked(new Set());
                setTossed(new Set());
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

        const [linkedItems, unlinkedItems] = partitionBy(
            displayItems.filter(({ id }) => !tossed.has(id)),
            (s) => {
                if (unlinkingInFlight.has(s.id) || preliminaryUnlinked.has(s.id)) {
                    return false;
                } else if (linkingInFlight.has(s.id) || preliminaryLinked.has(s.id)) {
                    return true;
                } else {
                    return getItemOperationalPoints(s).some((op) => op === operationalPoint.id);
                }
            },
        );

        const linkItems = async (items: { id: Id; name: string }[]) => {
            items.forEach(({ id }) => {
                addLinkingInFlight(id);
                deletePreliminaryUnlinked(id);
            });
            try {
                const affectedIds = await linkToOperationalPoint(
                    layoutContext.branch,
                    items.map(({ id }) => id),
                    operationalPoint.id,
                );
                await updateChangeTimes();
                items.forEach(({ id }) => {
                    addPreliminaryLinked(id);
                    deleteFromLinkingInFlight(id);
                });
                return items.filter(({ id }) => affectedIds.includes(id)).map(({ name }) => name);
            } catch {
                items.forEach(({ id }) => {
                    deleteFromLinkingInFlight(id);
                });
                await updateChangeTimes();
                return [];
            }
        };
        const unlinkItems = async (
            items: { id: Id; name: string }[],
            idsToImmediatelyToss: Id[],
        ) => {
            items.forEach(({ id }) => {
                deletePreliminaryLinked(id);
                addUnlinkingInFlight(id);
                if (idsToImmediatelyToss.includes(id)) {
                    addTossed(id);
                }
            });
            try {
                const ids = await unlinkFromOperationalPoint(
                    layoutContext.branch,
                    items.map(({ id }) => id),
                    operationalPoint.id,
                );
                await updateChangeTimes();
                items.forEach(({ id }) => {
                    addPreliminaryUnlinked(id);
                    deleteFromUnlinkingInFlight(id);
                });
                return items.filter(({ id }) => ids.includes(id)).map(({ name }) => name);
            } catch {
                items.forEach(({ id }) => {
                    deleteFromUnlinkingInFlight(id);
                });
                await updateChangeTimes();
                return [];
            }
        };

        return {
            isInitializing,
            itemAssociation,
            linkedItems,
            unlinkedItems,
            linkItems,
            unlinkItems,
        };
    };
}

export function getSuccessToastMessageParams(
    linkableObjectType: 'track' | 'switch',
    operationalPointName: string,
    names: string[],
    linkingDirection: LinkingDirection,
): [string, Record<string, string>] | undefined {
    if (names.length === 1) {
        const key = `tool-panel.operational-point.${linkableObjectType}-links.single-${linkingDirection}-success-toast`;
        const args = {
            name: expectDefined(names[0]),
            operationalPointName: operationalPointName,
        };
        return [key, args];
    } else if (names.length > 1) {
        const key = `tool-panel.operational-point.${linkableObjectType}-links.multiple-${linkingDirection}-success-toast`;
        const args = {
            count: `${names.length}`,
            operationalPointName: operationalPointName,
        };
        return [key, args];
    } else {
        // no toasts if nothing was successfully done
        return undefined;
    }
}
export type LinkingDirection = 'linking' | 'unlinking';
