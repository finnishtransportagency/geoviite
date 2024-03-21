import * as React from 'react';
import './infra-model-list.module.scss';
import { InfraModelSearchForm } from 'infra-model/list/infra-model-search-form';
import { InfraModelSearchResult } from 'infra-model/list/infra-model-search-result';
import { GeometryPlanId, GeometryPlanSearchParams } from 'geometry/geometry-model';
import { InfraModelListState } from 'infra-model/list/infra-model-list-store';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { ChangeTimes } from 'common/common-slice';
import { useCommonDataAppSelector } from 'store/hooks';
import { userHasPrivilege, VIEW_LAYOUT_DRAFT } from 'user/user-model';
import { draftMainLayoutContext, officialMainLayoutContext } from 'common/common-model';

export type InfraModelListViewProps = Pick<
    InfraModelListState,
    'searchState' | 'searchParams' | 'plans' | 'page' | 'pageSize' | 'totalCount'
> & {
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
    onNextPage: () => void;
    onPrevPage: () => void;
    changeTimes: ChangeTimes;
    clearInfraModelState: () => void;
};

export const InfraModelListView: React.FC<InfraModelListViewProps> = (
    props: InfraModelListViewProps,
) => {
    const privileges = useCommonDataAppSelector((state) => state.userPrivileges).map((p) => p.code);
    const trackNumbers = useTrackNumbers(
        userHasPrivilege(privileges, VIEW_LAYOUT_DRAFT)
            ? draftMainLayoutContext()
            : officialMainLayoutContext(),
    );
    React.useEffect(() => {
        props.clearInfraModelState();
    }, []);
    return (
        <div className="infra-model-list">
            <div className="infra-model-list__search-form">
                <InfraModelSearchForm
                    trackNumbers={trackNumbers || []}
                    searchParams={props.searchParams}
                    onSearchParamsChange={props.onSearchParamsChange}
                />
            </div>
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
    );
};
