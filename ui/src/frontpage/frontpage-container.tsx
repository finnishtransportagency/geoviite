import * as React from 'react';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((state) => {
        return {
            publication: state.selection.publication,
            changeTime: state.changeTimes.publication,
        };
    });
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, trackLayoutActionCreators);

    return (
        <Frontpage
            selectedPublication={state.publication}
            changeTime={state.changeTime}
            onSelectedPublicationChanged={delegates.onSelectedPublicationChanged}
        />
    );
};
