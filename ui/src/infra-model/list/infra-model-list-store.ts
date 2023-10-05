import { PayloadAction } from '@reduxjs/toolkit';
import {
    GeometryPlanHeader,
    GeometryPlanSearchParams,
    GeometrySortBy,
} from 'geometry/geometry-model';
import { objectEquals } from 'utils/object-utils';

export type SearchState = 'idle' | 'start' | 'search';

export type InfraModelListState = {
    searchState: SearchState;
    searchParams: GeometryPlanSearchParams;
    searchErrorMsg: string | undefined;
    plans: GeometryPlanHeader[];
    totalCount: number;
    page: number;
    pageSize: number;
};

export const initialInfraModelListState: InfraModelListState = {
    searchState: 'start',
    searchParams: {
        freeText: '',
        trackNumberIds: [],
        sources: ['GEOMETRIAPALVELU'],
        sortBy: GeometrySortBy.NO_SORTING,
        sortOrder: undefined,
    },
    searchErrorMsg: undefined,
    plans: [],
    totalCount: 0,
    page: 0,
    pageSize: 100,
};

export type PlanFetchReadyParams = {
    totalCount: number;
    plans: GeometryPlanHeader[];
    searchParams: GeometryPlanSearchParams;
};

export const infraModelListReducers = {
    onSearchParamsChange: function (
        state: InfraModelListState,
        { payload: searchParams }: PayloadAction<GeometryPlanSearchParams>,
    ) {
        if (!objectEquals(searchParams, state.searchParams)) {
            state.searchState = 'start';
            state.searchParams = searchParams;
            state.page = 0;
        }
    },
    onNextPage: function (state: InfraModelListState) {
        if ((state.page + 1) * state.pageSize < state.totalCount) {
            state.page += 1;
            state.searchState = 'start';
        }
    },
    onPrevPage: function (state: InfraModelListState) {
        if (state.page > 0) {
            state.page -= 1;
            state.searchState = 'start';
        }
    },
    onPlanFetchStart: function (state: InfraModelListState) {
        state.searchState = 'search';
    },
    onPlanChangeTimeChange: function (state: InfraModelListState) {
        if (state.searchState == 'idle') state.searchState = 'start';
    },
    onPlanFetchError: function (
        state: InfraModelListState,
        { payload: message }: PayloadAction<string>,
    ) {
        state.searchErrorMsg = message;
        state.searchState = 'idle';
    },
    onPlansFetchReady: function (
        state: InfraModelListState,
        { payload: params }: PayloadAction<PlanFetchReadyParams>,
    ) {
        // Accept latest result only
        if (
            state.searchState == 'search' &&
            objectEquals(params.searchParams, state.searchParams)
        ) {
            state.searchState = 'idle';
            state.plans = params.plans;
            state.totalCount = params.totalCount;
            if (state.page * state.pageSize >= state.totalCount) state.page = 0;
        }
    },
};
