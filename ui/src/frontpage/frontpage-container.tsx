import * as React from 'react';
import { actionCreators } from 'store/track-layout-store';
import { createDelegates } from 'store/store-utils';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((state) => ({
        publication: state.trackLayout.selection.publication,
        changeTime: state.trackLayout.changeTimes.publication,
    }));
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
