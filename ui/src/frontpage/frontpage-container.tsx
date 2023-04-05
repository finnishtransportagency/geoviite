import * as React from 'react';
import { actionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((state) => {
        console.log(state);
        return {
            publication: state.selection.publication,
            changeTime: state.changeTimes.publication,
        };
    });
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, actionCreators);

    return (
        <Frontpage
            selectedPublication={state.publication}
            changeTime={state.changeTime}
            onSelectedPublicationChanged={delegates.onSelectedPublicationChanged}
        />
    );
};
