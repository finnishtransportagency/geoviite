import * as React from 'react';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { LayoutContext, officialLayoutContext } from 'common/common-model';
import { ExternalOperationalPointEditDialog } from 'tool-panel/operational-point/dialog/external-operational-point-edit-dialog';
import { InternalOperationalPointEditDialog } from 'tool-panel/operational-point/dialog/internal-operational-point-edit-dialog';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getAllOperationalPoints } from 'track-layout/layout-operational-point-api';
import { useCommonDataAppSelector } from 'store/hooks';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';

type OperationalPointEditDialogContainerProps = {
    operationalPointId: OperationalPointId | undefined;
    layoutContext: LayoutContext;
    onSave: (id: OperationalPointId) => void;
    onClose: () => void;
};

const isNotItself = (op: OperationalPoint, id: OperationalPointId | undefined) => op.id !== id;

export const OperationalPointEditDialogContainer: React.FC<
    OperationalPointEditDialogContainerProps
> = ({ operationalPointId, layoutContext, onSave, onClose }) => {
    const [existingOperationalPointInEdit, setExistingOperationalPointInEdit] = React.useState<
        OperationalPointId | undefined
    >(operationalPointId);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const [allOperationalPoints, allOperationalPointsFetchStatus] = useLoaderWithStatus(
        () => getAllOperationalPoints(layoutContext, changeTimes.operationalPoints),
        [layoutContext, changeTimes.operationalPoints],
    );
    const [isDraftOnly, isDraftOnlyFetchStatus] = useLoaderWithStatus(
        () =>
            getAllOperationalPoints(
                officialLayoutContext(layoutContext),
                changeTimes.operationalPoints,
            ).then(
                (points) =>
                    !points.some((official) => official.id === existingOperationalPointInEdit),
            ),
        [layoutContext, changeTimes.operationalPoints],
    );

    const existingOperationalPointOrUndefined = allOperationalPoints?.find(
        (op) => op.id === existingOperationalPointInEdit,
    );
    const origin = existingOperationalPointOrUndefined?.origin;
    const allOtherOperationalPoints =
        allOperationalPoints?.filter((op) =>
            isNotItself(op, existingOperationalPointOrUndefined?.id),
        ) ?? [];

    if (
        allOperationalPointsFetchStatus !== LoaderStatus.Ready &&
        isDraftOnlyFetchStatus !== LoaderStatus.Ready
    ) {
        return <React.Fragment />;
    } else {
        switch (origin) {
            case 'RATKO':
                return (
                    <ExternalOperationalPointEditDialog
                        operationalPoint={expectDefined(existingOperationalPointOrUndefined)}
                        layoutContext={layoutContext}
                        allOtherOperationalPoints={allOtherOperationalPoints}
                        onSave={onSave}
                        onClose={onClose}
                    />
                );
            case 'GEOVIITE': // Editing existing operational point
            case undefined: // Creating new operational point
                return (
                    <InternalOperationalPointEditDialog
                        operationalPoint={existingOperationalPointOrUndefined}
                        onEditOperationalPoint={setExistingOperationalPointInEdit}
                        allOtherOperationalPoints={allOtherOperationalPoints}
                        isDraftOnly={isDraftOnly ?? false}
                        layoutContext={layoutContext}
                        onSave={onSave}
                        onClose={onClose}
                    />
                );
            default:
                return exhaustiveMatchingGuard(origin);
        }
    }
};
