import { infraModelActionCreators, InfraModelViewType } from '../infra-model-slice';
import { createDelegates } from 'store/store-utils';
import React from 'react';
import { InfraModelEditLoader } from 'infra-model/view/infra-model-edit-loader';
import { useInfraModelAppSelector, useCommonDataAppSelector } from 'store/hooks';
import { InfraModelImportLoader } from './infra-model-import-loader';
import { ValidationResponse } from 'infra-model/infra-model-api';
import { InfraModelUploadLoader } from './infra-model-upload-loader';
import { InfraModelBaseProps } from './infra-model-view';

type InfraModelViewContainerProps = {
    viewType: InfraModelViewType;
};

export const InfraModelViewContainer: React.FC<InfraModelViewContainerProps> = ({
    viewType,
}: InfraModelViewContainerProps) => {
    const infraModelState = useInfraModelAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const delegates = createDelegates(infraModelActionCreators);

    const [isLoading, setLoading] = React.useState(false);
    // TODO: GVT-1795 this wont persist when visiting another tab. keep the whole response in redux?
    const [validationResponse, setValidationResponse] = React.useState<ValidationResponse | null>(
        null,
    );
    const onValidation = (validationResponse: ValidationResponse | null) => {
        setValidationResponse(validationResponse);
        delegates.onPlanFetchReady({
            plan: validationResponse?.geometryPlan || null,
            planLayout: validationResponse?.planLayout || null,
        });
    };

    const delegatesProps = {
        onExtraParametersChange: delegates.onInfraModelExtraParametersChange,
        onOverrideParametersChange: delegates.onInfraModelOverrideParametersChange,
        onPlanFetchReady: delegates.onPlanFetchReady,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onClickLocation: delegates.onClickLocation,
        onViewportChange: delegates.onViewportChange,
        onCommitField: delegates.onCommitField,
        showArea: delegates.showArea,
    };
    const generalProps: InfraModelBaseProps = {
        ...infraModelState,
        ...delegatesProps,
        changeTimes: changeTimes,
        viewType: viewType,
        isLoading: isLoading,
        validationResponse: validationResponse,
    };

    switch (viewType) {
        case InfraModelViewType.EDIT:
            return (
                <InfraModelEditLoader
                    {...generalProps}
                    onValidation={onValidation}
                    setExistingInfraModel={delegates.setExistingInfraModel}
                    setLoading={setLoading}
                />
            );
        case InfraModelViewType.IMPORT:
            return (
                <InfraModelImportLoader
                    {...generalProps}
                    onValidation={onValidation}
                    setLoading={setLoading}
                />
            );
        case InfraModelViewType.UPLOAD:
            return (
                <InfraModelUploadLoader
                    {...generalProps}
                    onValidation={onValidation}
                    setLoading={setLoading}
                />
            );
    }
};
