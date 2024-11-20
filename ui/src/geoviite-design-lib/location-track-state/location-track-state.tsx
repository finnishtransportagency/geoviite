import React from 'react';
import { useTranslation } from 'react-i18next';
import { LocationTrackState as LocationTrackStateModel } from '../../track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type LocationTrackStateProps = {
    state: LocationTrackStateModel;
};

function getTranslationKey(layoutState: LocationTrackStateModel) {
    switch (layoutState) {
        case 'BUILT':
        case 'IN_USE':
        case 'NOT_IN_USE':
        case 'DELETED':
            return layoutState;
        default:
            return exhaustiveMatchingGuard(layoutState);
    }
}

export const LocationTrackState: React.FC<LocationTrackStateProps> = ({
    state,
}: LocationTrackStateProps) => {
    const { t } = useTranslation();

    return <span qa-id={state}>{t(`enum.LocationTrackState.${getTranslationKey(state)}`)}</span>;
};
