import * as React from 'react';
import { OperationalPointId } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ExternalOperationalPointEditDialog } from 'tool-panel/operational-point/external-operational-point-edit-dialog';
import { InternalOperationalPointEditDialog } from 'tool-panel/operational-point/internal-operational-point-edit-dialog';
import { useLoader } from 'utils/react-utils';
import { getAllOperationalPoints } from 'track-layout/layout-operational-point-api';
import { useCommonDataAppSelector } from 'store/hooks';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';

type OperationalPointEditDialogContainerProps = {
    operationalPointId: OperationalPointId | undefined;
    layoutContext: LayoutContext;
    onSave: (id: OperationalPointId) => void;
    onClose: () => void;
};

export const OperationalPointEditDialogContainer: React.FC<
    OperationalPointEditDialogContainerProps
> = ({ operationalPointId, layoutContext, onSave, onClose }) => {
    const [existingOperationalPointInEdit, setExistingOperationalPointInEdit] = React.useState<
        OperationalPointId | undefined
    >(operationalPointId);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const allOperationalPoints = useLoader(
        () => getAllOperationalPoints(layoutContext, changeTimes.operationalPoints),
        [layoutContext, changeTimes.operationalPoints],
    );
    const existingOperationalPoint = allOperationalPoints?.find(
        (op) => op.id === existingOperationalPointInEdit,
    );
    const origin = existingOperationalPoint?.origin;

    if (!allOperationalPoints) {
        return <React.Fragment />;
    } else {
        switch (origin) {
            case 'RATKO':
                return (
                    <ExternalOperationalPointEditDialog
                        operationalPoint={expectDefined(existingOperationalPoint)}
                        layoutContext={layoutContext}
                        onSave={onSave}
                        onClose={onClose}
                    />
                );
            case 'GEOVIITE': // Editing existing operational point
            case undefined: // Creating new operational point
                return (
                    <InternalOperationalPointEditDialog
                        operationalPoint={existingOperationalPoint}
                        onEditOperationalPoint={setExistingOperationalPointInEdit}
                        allOperationalPoints={allOperationalPoints}
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
