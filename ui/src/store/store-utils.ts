import { appStore } from 'store/store';

type ReducerFunc<TState, TAction> = (state: TState, action: TAction) => void;
type CreateActionFunc<TPayload, TAction> = (payload: TPayload) => TAction;

export type WrappedReducers<TRootState, TState, TReducers> = {
    [Property in keyof TReducers]: TReducers[Property] extends ReducerFunc<TState, infer TAction>
        ? ReducerFunc<TRootState, TAction>
        : unknown;
};

export function wrapReducers<
    TRootState,
    TState,
    TReducers extends { [key: string]: ReducerFunc<TState, unknown> },
>(
    getMapFunc: (state: TRootState) => TState,
    reducers: TReducers,
): WrappedReducers<TRootState, TState, TReducers> {
    const mappedReducers: {
        [key: string]: unknown;
    } = {};
    Object.keys(reducers).map((key) => {
        mappedReducers[key] = (state: TRootState, action: unknown) =>
            reducers[key](getMapFunc(state), action);
    });
    return mappedReducers as WrappedReducers<TRootState, TState, TReducers>;
}

export function createDelegates<
    TActionCreators extends { [key: string]: CreateActionFunc<unknown, unknown> },
>(actionCreators: TActionCreators): TActionCreators {
    const delegates: { [key: string]: unknown } = {};
    const dispatch: React.Dispatch<unknown> = appStore.dispatch;
    Object.keys(actionCreators).forEach((key) => {
        delegates[key] = function (payload: unknown) {
            dispatch(actionCreators[key](payload));
        };
    });
    return delegates as TActionCreators;
}

export function createDelegatesWithDispatcher<
    TDispatch extends React.Dispatch<unknown>,
    TActionCreators extends { [key: string]: CreateActionFunc<unknown, unknown> },
>(dispatch: TDispatch, actionCreators: TActionCreators): TActionCreators {
    const delegates: { [key: string]: unknown } = {};
    Object.keys(actionCreators).forEach((key) => {
        delegates[key] = function (payload: unknown) {
            dispatch(actionCreators[key](payload));
        };
    });
    return delegates as TActionCreators;
}
