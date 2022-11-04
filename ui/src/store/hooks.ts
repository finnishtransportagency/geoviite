import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import type { InfraModelRootState, TrackLayoutAppDispatch, TrackLayoutRootState } from './store';
import { InframodelDispatch } from './store';

export const useTrackLayoutAppDispatch = () => useDispatch<TrackLayoutAppDispatch>();
export const useTrackLayoutAppSelector: TypedUseSelectorHook<TrackLayoutRootState> = useSelector;

export const useInframodelAppDispatch = () => useDispatch<InframodelDispatch>();
export const useInframodelAppSelector: TypedUseSelectorHook<InfraModelRootState> = useSelector;
