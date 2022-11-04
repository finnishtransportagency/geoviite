import * as React from 'react';
import './infra-model-list.module.scss';
import { InfraModelSearchForm } from 'infra-model/list/infra-model-search-form';
import { InfraModelSearchResult } from 'infra-model/list/infra-model-search-result';
import { GeometryPlanId, GeometryPlanSearchParams } from 'geometry/geometry-model';
import { InfraModelListState } from 'infra-model/list/infra-model-list-store';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { ChangeTimes } from 'track-layout/track-layout-store';

export type InfraModelListViewProps = Pick<
    InfraModelListState,
    'searchState' | 'searchParams' | 'plans' | 'page' | 'pageSize' | 'totalCount'
> & {
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
    onNextPage: () => void;
    onPrevPage: () => void;
    changeTimes: ChangeTimes;
};

export const InfraModelListView: React.FC<InfraModelListViewProps> = (
    props: InfraModelListViewProps,
) => {
    const trackNumbers = useTrackNumbers('DRAFT');
    return (
        <div className="infra-model-list">
            <div className="infra-model-list__search-form">
                <InfraModelSearchForm
                    trackNumbers={trackNumbers || []}
                    searchParams={props.searchParams}
                    onSearchParamsChange={props.onSearchParamsChange}
                />
            </div>
            <div className="infra-model-list__search-result">
                <InfraModelSearchResult
                    searchParams={props.searchParams}
                    plans={props.plans}
                    searchState={props.searchState}
                    onSearchParamsChange={props.onSearchParamsChange}
                    onSelectPlan={props.onSelectPlan}
                    page={props.page}
                    pageSize={props.pageSize}
                    totalCount={props.totalCount}
                    onNextPage={props.onNextPage}
                    onPrevPage={props.onPrevPage}
                />
            </div>
        </div>
    );
};
