import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import type { AppDispatch, AppState } from './store';
import { InfraModelState } from 'infra-model/infra-model-slice';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { DataProductsState } from 'data-products/data-products-slice';
import { CommonState } from 'common/common-slice';

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<AppState> = useSelector;

export function useTrackLayoutAppSelector<T>(fn: (state: TrackLayoutState) => T) {
    const trackLayoutState = useSelector(
        (state: AppState) => state.trackLayout as TrackLayoutState,
    );
    return fn(trackLayoutState);
}

export function useInfraModelAppSelector<T>(fn: (state: InfraModelState) => T) {
    const infraModelState = useSelector((state: AppState) => state.infraModel as InfraModelState);
    return fn(infraModelState);
}

export function useDataProductsAppSelector<T>(fn: (state: DataProductsState) => T) {
    const dataProductsState = useSelector(
        (state: AppState) => state.dataProducts as DataProductsState,
    );
    return fn(dataProductsState);
}

export function useCommonDataAppSelector<T>(fn: (state: CommonState) => T) {
    const commonState = useSelector((state: AppState) => state.common as CommonState);
    return fn(commonState);
}
