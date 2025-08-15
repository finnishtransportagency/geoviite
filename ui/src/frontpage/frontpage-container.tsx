import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';
import Frontpage from 'frontpage/frontpage';

export const FrontpageContainer: React.FC = () => {
    const publicationChangeTime = useCommonDataAppSelector(
        (state) => state.changeTimes.publication,
    );
    const ratkoPushChangeTime = useCommonDataAppSelector((state) => state.changeTimes.ratkoPush);
    const splitChangeTime = useCommonDataAppSelector((state) => state.changeTimes.split);
    const designChangeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutDesign);

    return (
        <Frontpage
            publicationChangeTime={publicationChangeTime}
            ratkoPushChangeTime={ratkoPushChangeTime}
            splitChangeTime={splitChangeTime}
            designChangeTime={designChangeTime}
        />
    );
};
