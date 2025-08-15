import * as React from 'react';
import MainPublicationCard from 'publication/card/main-publication-card';
import styles from './frontpage.scss';

import { useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';

import { TimeStamp } from 'common/common-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_LAYOUT_DRAFT } from 'user/user-model';
import DesignPublicationCard from 'publication/card/design-publication-card';

type FrontPageProps = {
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
    splitChangeTime: TimeStamp;
};

const Frontpage: React.FC<FrontPageProps> = ({
    publicationChangeTime,
    ratkoPushChangeTime,
    splitChangeTime,
}) => {
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();

    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    return (
        <React.Fragment>
            <div className={styles['frontpage']}>
                <MainPublicationCard
                    publicationChangeTime={publicationChangeTime}
                    ratkoPushChangeTime={ratkoPushChangeTime}
                    splitChangeTime={splitChangeTime}
                    ratkoStatus={ratkoStatus}
                />
                <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                    <DesignPublicationCard publicationChangeTime={publicationChangeTime} />
                </PrivilegeRequired>
                <UserCardContainer />
            </div>
        </React.Fragment>
    );
};

export default Frontpage;
