import * as React from 'react';
import PublicationCard from 'publication/card/publication-card';
import styles from './frontpage.scss';

import { useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';

import { TimeStamp } from 'common/common-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_LAYOUT_DRAFT } from 'user/user-model';

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
                <PublicationCard
                    publicationChangeTime={publicationChangeTime}
                    ratkoPushChangeTime={ratkoPushChangeTime}
                    splitChangeTime={splitChangeTime}
                    ratkoStatus={ratkoStatus}
                    branchType="MAIN"
                />
                <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                    <PublicationCard
                        publicationChangeTime={publicationChangeTime}
                        ratkoPushChangeTime={ratkoPushChangeTime}
                        splitChangeTime={splitChangeTime}
                        ratkoStatus={ratkoStatus}
                        branchType="DESIGN"
                    />
                </PrivilegeRequired>
                <UserCardContainer />
            </div>
        </React.Fragment>
    );
};

export default Frontpage;
