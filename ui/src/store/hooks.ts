import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import type {
    DataProductsDispatch,
    DataProductsRootState,
    InfraModelRootState,
    TrackLayoutAppDispatch,
    TrackLayoutRootState,
} from './store';
import { InframodelDispatch } from './store';

export const useTrackLayoutAppDispatch = () => useDispatch<TrackLayoutAppDispatch>();
export const useTrackLayoutAppSelector: TypedUseSelectorHook<TrackLayoutRootState> = useSelector;

export const useInframodelAppDispatch = () => useDispatch<InframodelDispatch>();
export const useInframodelAppSelector: TypedUseSelectorHook<InfraModelRootState> = useSelector;

export const useDataProductsAppDispatch = () => useDispatch<DataProductsDispatch>();
export const useDataProductsAppSelector: TypedUseSelectorHook<DataProductsRootState> = useSelector;
