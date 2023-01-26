import * as React from 'react';
import { InfraModelListView } from 'infra-model/list/infra-model-list-view';

import { createDelegates } from 'store/store-utils';
import { getGeometryPlanHeaders } from 'geometry/geometry-api';
import { GeometryPlanId } from 'geometry/geometry-model';
import { actionCreators } from 'infra-model/infra-model-store';
import { useInframodelAppDispatch, useInframodelAppSelector } from 'store/hooks';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { useAppNavigate } from 'common/navigate';

export type InfraModelListContainerProps = {
    changeTimes: ChangeTimes;
};

export const InfraModelListContainer: React.FC<InfraModelListContainerProps> = ({
    changeTimes,
}) => {
    const rootDispatch = useInframodelAppDispatch();
    const infraModelListDelegates = createDelegates(rootDispatch, actionCreators);
    const state = useInframodelAppSelector((state) => state.infraModel.infraModelList);
    const navigate = useAppNavigate();

    React.useEffect(() => {
        infraModelListDelegates.onPlanChangeTimeChange();
    }, [changeTimes.geometryPlan]);

    // Handle search plans side effect
    React.useEffect(() => {
        if (state.searchState == 'start') {
            infraModelListDelegates.onPlanFetchStart();
            getGeometryPlanHeaders(
                state.pageSize,
                state.page * state.pageSize,
                undefined,
                state.searchParams.sources,
                state.searchParams.trackNumberIds,
                state.searchParams.freeText,
                state.searchParams.sortBy,
                state.searchParams.sortOrder,
            )
                .then((page) => {
                    infraModelListDelegates.onPlansFetchReady({
                        plans: page.items,
                        searchParams: state.searchParams,
                        totalCount: page.totalCount,
                    });
                })
                .catch((e) => infraModelListDelegates.onPlanFetchError(e?.toString()));
        }
    }, [state.searchState]);

    const onSelectPlan = (planId: GeometryPlanId) => navigate('inframodel-edit', planId);

    return (
        <InfraModelListView
            searchParams={state.searchParams}
            onSearchParamsChange={infraModelListDelegates.onSearchParamsChange}
            onNextPage={infraModelListDelegates.onNextPage}
            onPrevPage={infraModelListDelegates.onPrevPage}
            onSelectPlan={onSelectPlan}
            plans={state.plans}
            searchState={state.searchState}
            totalCount={state.totalCount}
            page={state.page}
            pageSize={state.pageSize}
            changeTimes={changeTimes}
        />
    );
};
