import { infraModelActionCreators } from '../infra-model-slice';
import { createDelegates } from 'store/store-utils';
import React from 'react';
import { InfraModelEditLoader } from 'infra-model/view/infra-model-edit-loader';
import { useInfraModelAppSelector, useCommonDataAppSelector } from 'store/hooks';
import { InfraModelImportLoader } from './infra-model-import-loader';
import { InfraModelUploadLoader } from './infra-model-upload-loader';
import { InfraModelBaseProps } from './infra-model-view';
import { useAppNavigate } from 'common/navigate';

export enum InfraModelViewType {
    UPLOAD,
    IMPORT,
    EDIT,
}

type InfraModelViewContainerProps = {
    viewType: InfraModelViewType;
};

export const InfraModelViewContainer: React.FC<InfraModelViewContainerProps> = ({
    viewType,
}: InfraModelViewContainerProps) => {
    const navigate = useAppNavigate();

    const infraModelState = useInfraModelAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const delegates = createDelegates(infraModelActionCreators);

    const [isLoading, setLoading] = React.useState(false);

    const generalProps: InfraModelBaseProps = {
        ...infraModelState,
        onExtraParametersChange: delegates.onInfraModelExtraParametersChange,
        onOverrideParametersChange: delegates.onInfraModelOverrideParametersChange,
        onSelect: delegates.onSelect,
        onHighlightItems: delegates.onHighlightItems,
        onHoverLocation: delegates.onHoverLocation,
        onClickLocation: delegates.onClickLocation,
        onViewportChange: delegates.onViewportChange,
        changeTimes: changeTimes,
        isLoading: isLoading,
        onClose: () => navigate('inframodel-list'),
    };
    const loaderProps = {
        ...generalProps,
        setLoading: setLoading,
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
    }
};
