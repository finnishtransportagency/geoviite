import * as React from 'react';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useAppDispatch, useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.publication);
    const publication = useTrackLayoutAppSelector((state) => state.selection.publication);
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, trackLayoutActionCreators);

    return (
        <Frontpage
            selectedPublication={publication}
            changeTime={changeTime}
            onSelectedPublicationChanged={delegates.onSelectedPublicationChanged}
        />
    );
};
