import React from 'react';
import { useTranslation } from 'react-i18next';
import { OperationalPointState as OperationalPointStateModel } from '../../track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type OperationalPointStateProps = {
    state: OperationalPointStateModel;
};

function getTranslationKey(state: OperationalPointStateModel) {
    switch (state) {
        case 'IN_USE':
        case 'DELETED':
            return state;
        default:
            return exhaustiveMatchingGuard(state);
    }
}

export const OperationalPointState: React.FC<OperationalPointStateProps> = ({
    state,
}: OperationalPointStateProps) => {
    const { t } = useTranslation();

    return <span qa-id={state}>{t(`enum.OperationalPointState.${getTranslationKey(state)}`)}</span>;
};
