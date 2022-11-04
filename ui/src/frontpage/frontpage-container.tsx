import * as React from 'react';
import { actionCreators } from 'track-layout/track-layout-store';
import { createDelegates } from 'store/store-utils';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const selectionState = useTrackLayoutAppSelector((state) => state.trackLayout.selection);
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, actionCreators);

    return (
        <Frontpage
            selectedPublication={selectionState.publication}
            onSelectedPublicationChanged={delegates.onSelectedPublicationChanged}
        />
    );
};
