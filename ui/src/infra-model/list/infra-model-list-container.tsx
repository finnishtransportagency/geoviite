import * as React from 'react';
import { InfraModelListView } from 'infra-model/list/infra-model-list-view';

import { GeometryPlanId, GeometryPlanSearchParams } from 'geometry/geometry-model';
import { useInfraModelAppSelector } from 'store/hooks';
import { useAppNavigate } from 'common/navigate';
import { ChangeTimes } from 'common/common-slice';

export type InfraModelListContainerProps = {
    changeTimes: ChangeTimes;
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
    onNextPage: () => void;
    onPrevPage: () => void;
    clearInfraModelState: () => void;
};

export const InfraModelListContainer: React.FC<InfraModelListContainerProps> = ({
    changeTimes,
    onSearchParamsChange,
    onNextPage,
    onPrevPage,
    clearInfraModelState,
}) => {
    const state = useInfraModelAppSelector((state) => state.infraModelList);
    const navigate = useAppNavigate();

    const onSelectPlan = React.useCallback(
        (planId: GeometryPlanId) => navigate('inframodel-edit', planId),
        [],
    );

    return (
        <InfraModelListView
            searchParams={state.searchParams}
            onSearchParamsChange={onSearchParamsChange}
            onNextPage={onNextPage}
            onPrevPage={onPrevPage}
            onSelectPlan={onSelectPlan}
            plans={state.plans}
            searchState={state.searchState}
            totalCount={state.totalCount}
            page={state.page}
            pageSize={state.pageSize}
            changeTimes={changeTimes}
            clearInfraModelState={clearInfraModelState}
        />
    );
};
