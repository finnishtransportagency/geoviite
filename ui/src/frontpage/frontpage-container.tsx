import * as React from 'react';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const publicationChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.publication,
    );
    const ratkoPushChangeTime = useCommonDataAppSelector((state) => state.changeTimes.ratkoPush);
    const publication = useTrackLayoutAppSelector((state) => state.selection.publication);
    const delegates = createDelegates(trackLayoutActionCreators);

    return (
        <Frontpage
            selectedPublication={publication}
            publicationChangeTime={publicationChangeTime}
            ratkoPushChangeTime={ratkoPushChangeTime}
            onSelectedPublicationChanged={delegates.onSelectedPublicationChanged}
        />
    );
};
