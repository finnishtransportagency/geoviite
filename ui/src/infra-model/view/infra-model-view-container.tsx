import { infraModelActionCreators, InfraModelViewType } from '../infra-model-slice';
import { createDelegates } from 'store/store-utils';
import React from 'react';
import { InfraModelEditLoader } from 'infra-model/view/infra-model-edit-loader';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { useAppNavigate } from 'common/navigate';
import { InfraModelImportLoader } from 'infra-model/view/infra-model-import-loader';
import { InfraModelUploadLoader } from 'infra-model/view/infra-model-upload-loader';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type InfraModelViewContainerProps = {
    viewType: InfraModelViewType;
};

export const InfraModelViewContainer: React.FC<InfraModelViewContainerProps> = ({
    viewType,
}: InfraModelViewContainerProps) => {
    const navigate = useAppNavigate();
    const infraModelState = useInfraModelAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const delegates = React.useMemo(() => createDelegates(infraModelActionCreators), []);

    const [isValidating, setIsValidating] = React.useState(false);
    const [isSaving, setSaving] = React.useState(false);

    const generalProps = {
        ...infraModelState,
        onExtraParametersChange: delegates.onInfraModelExtraParametersChange,
        onOverrideParametersChange: delegates.onInfraModelOverrideParametersChange,
        onSelect: delegates.onSelect,
        changeTimes: changeTimes,
        isValidating: isValidating,
        isSaving: isSaving,
        clearInfraModelState: delegates.clearInfraModelState,
        onClose: () => navigate('inframodel-list'),
    };

    const loaderProps = {
        ...generalProps,
        setLoading: setIsValidating,
        setSaving: setSaving,
        onValidation: delegates.onPlanValidated,
    };

    switch (viewType) {
        case InfraModelViewType.EDIT:
            return (
                <InfraModelEditLoader
                    {...loaderProps}
                    setExistingInfraModel={delegates.setExistingInfraModel}
                />
            );
        case InfraModelViewType.IMPORT:
            return (
                <InfraModelImportLoader
                    {...loaderProps}
                    setExistingInfraModel={delegates.setExistingInfraModel}
                />
            );
        case InfraModelViewType.UPLOAD:
            return <InfraModelUploadLoader {...loaderProps} />;
        default:
            return exhaustiveMatchingGuard(viewType);
    }
};
