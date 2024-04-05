import React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LayoutState as LayoutStateModel,
    LocationTrackLayoutState,
} from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type LayoutStateProps = {
    state: LayoutStateModel | LocationTrackLayoutState;
};

function getTranslationKey(layoutState: LayoutStateModel | LocationTrackLayoutState) {
    switch (layoutState) {
        case 'BUILT':
        case 'IN_USE':
        case 'NOT_IN_USE':
        case 'PLANNED':
        case 'DELETED':
            return layoutState;
        default:
            return exhaustiveMatchingGuard(layoutState);
    }
}

const LayoutState: React.FC<LayoutStateProps> = ({ state }: LayoutStateProps) => {
    const { t } = useTranslation();

    return <span qa-id={state}>{t(`enum.layout-state.${getTranslationKey(state)}`)}</span>;
};

export default LayoutState;
