import * as React from 'react';
import PublicationCard from 'publication/card/publication-card';
import styles from './frontpage.scss';
import PublicationDetailsView from 'publication/publication';
import { PublicationDetails, PublicationId } from 'publication/publication-model';
import { useLoader, useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';
import PublicationLog from 'publication/log/publication-log';
import { getPublicationDetails } from 'publication/publication-api';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { TimeStamp } from 'common/common-model';

type FrontPageProps = {
    selectedPublication: PublicationId | undefined;
    onSelectedPublicationChanged: (item: PublicationId | undefined) => void;
    changeTime: TimeStamp;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
    changeTime,
}) => {
    const [publications, setPublications] = React.useState<PublicationDetails[] | null>();
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();
    const [showPublicationLog, setShowPublicationLog] = React.useState(false);

    const publication = publications?.find((p) => p.id == selectedPublication);
    useLoader(
        () => getPublicationDetails().then((result) => setPublications(result?.items)),
        [changeTime],
    );
    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    const anyFailed = !!publications?.some((p) => ratkoPushFailed(p.ratkoPushStatus));

    return (
        <React.Fragment>
            {!selectedPublication && !showPublicationLog && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        {publications && (
                            <PublicationCard
                                publications={publications}
                                onPublicationSelect={(pub) => {
                                    onSelectedPublicationChanged(pub.id);
                                }}
                                onShowPublicationLog={() => setShowPublicationLog(true)}
                                ratkoStatus={ratkoStatus}
                            />
                        )}
                        <UserCardContainer />
                    </div>
                    <div className={styles['frontpage__photo']} />
                </React.Fragment>
            )}
            {!selectedPublication && showPublicationLog && (
                <PublicationLog onClose={() => setShowPublicationLog(false)} />
            )}
            {publication && (
                <PublicationDetailsView
                    changeTime={changeTime}
                    publication={publication}
                    onPublicationUnselected={() => onSelectedPublicationChanged(undefined)}
                    anyFailed={anyFailed}
                />
            )}
        </React.Fragment>
    );
};

export default Frontpage;
