import * as React from 'react';
import PublicationCard, { MAX_LISTED_PUBLICATIONS } from 'publication/card/publication-card';
import styles from './frontpage.scss';

import { useLoaderWithStatus, useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';

import { getLatestPublications } from 'publication/publication-api';

import { TimeStamp } from 'common/common-model';

type FrontPageProps = {
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
};

const Frontpage: React.FC<FrontPageProps> = ({ publicationChangeTime, ratkoPushChangeTime }) => {
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();
    const [publications, publicationFetchStatus] = useLoaderWithStatus(
        () => getLatestPublications(MAX_LISTED_PUBLICATIONS).then((result) => result?.items),
        [publicationChangeTime, ratkoPushChangeTime],
    );

    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    return (
        <React.Fragment>
            <div className={styles['frontpage']}>
                <PublicationCard
                    publications={publications || []}
                    publicationFetchStatus={publicationFetchStatus}
                    ratkoStatus={ratkoStatus}
                />
                <UserCardContainer />
            </div>
            <div className={styles['frontpage__photo']} />
        </React.Fragment>
    );
};

export default Frontpage;
