import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutState as LayoutStateModel } from 'track-layout/track-layout-model';

type LayoutStateProps = {
    state: LayoutStateModel | null;
};

function getTranslationKey(layoutState: LayoutStateModel | null) {
    switch (layoutState) {
        case 'IN_USE':
        case 'NOT_IN_USE':
        case 'PLANNED':
        case 'DELETED':
            return layoutState;
        default:
            return 'UNKNOWN';
    }
}

const LayoutState: React.FC<LayoutStateProps> = ({ state }: LayoutStateProps) => {
    const { t } = useTranslation();

    return <React.Fragment>{t(`enum.layout-state.${getTranslationKey(state)}`)}</React.Fragment>;
};

export default LayoutState;
