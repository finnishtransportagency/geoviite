import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import type {
    DataProductsDispatch,
    RootDispatch,
    RootState,
    TrackLayoutAppDispatch,
} from './store';
import { InfraModelDispatch } from './store';
import { InfraModelState } from 'infra-model/infra-model-slice';
import { TrackLayoutState } from 'track-layout/track-layout-slice';
import { DataProductsState } from 'data-products/data-products-slice';

export const useAppDispatch = () => useDispatch<RootDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;

export const useTrackLayoutAppDispatch = () => useDispatch<TrackLayoutAppDispatch>();
export function useTrackLayoutAppSelector<T>(fn: (state: TrackLayoutState) => T) {
    const trackLayoutState = useSelector(
        (state: RootState) => state.trackLayout as TrackLayoutState,
    );
    return fn(trackLayoutState);
}

export const useInframodelAppDispatch = () => useDispatch<InfraModelDispatch>();
export function useInfraModelAppSelector<T>(fn: (state: InfraModelState) => T) {
    const infraModelState = useSelector((state: RootState) => state.infraModel as InfraModelState);
    return fn(infraModelState);
}

export const useDataProductsAppDispatch = () => useDispatch<DataProductsDispatch>();
export function useDataProductsAppSelector<T>(fn: (state: DataProductsState) => T) {
    const dataProductsState = useSelector(
        (state: RootState) => state.dataProducts as DataProductsState,
    );
    return fn(dataProductsState);
}

//export const useDataProductsAppSelector: TypedUseSelectorHook<DataProductsSliceState> = useSelector;
