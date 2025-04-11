import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import type { AppDispatch, AppState } from './store';
import { InfraModelState } from 'infra-model/infra-model-slice';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { DataProductsState } from 'data-products/data-products-slice';
import { CommonState } from 'common/common-slice';
import { PrivilegeCode, userHasPrivilege } from 'user/user-model';

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<AppState> = useSelector;

export function useTrackLayoutAppSelector<T>(fn: (state: TrackLayoutState) => T) {
    return useAppSelector((state) => fn(state.trackLayout));
}

export function useInfraModelAppSelector<T>(fn: (state: InfraModelState) => T) {
    return useAppSelector((state) => fn(state.infraModel));
}

export function useDataProductsAppSelector<T>(fn: (state: DataProductsState) => T) {
    return useAppSelector((state) => fn(state.dataProducts));
}

export function useCommonDataAppSelector<T>(fn: (state: CommonState) => T) {
    return useAppSelector((state) => fn(state.common));
}

export function useUserHasPrivilege(code: PrivilegeCode): boolean {
    return useCommonDataAppSelector((state) =>
        userHasPrivilege(
            (state.user?.role.privileges ?? []).map((p) => p.code),
            code,
        ),
    );
}
