import * as React from 'react';
import PublicationCard, { MAX_LISTED_PUBLICATIONS } from 'publication/card/publication-card';
import styles from './frontpage.scss';
import PublicationDetailsView from 'publication/publication';
import { PublicationId } from 'publication/publication-model';
import { useLoaderWithStatus, useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';
import PublicationLog from 'publication/log/publication-log';
import { getLatestPublications } from 'publication/publication-api';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { TimeStamp } from 'common/common-model';

type FrontPageProps = {
    selectedPublication: PublicationId | undefined;
    onSelectedPublicationChanged: (item: PublicationId | undefined) => void;
    publicationChangeTime: TimeStamp;
    ratkoPushChangeTime: TimeStamp;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
    publicationChangeTime,
    ratkoPushChangeTime,
}) => {
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();
    const [showPublicationLog, setShowPublicationLog] = React.useState(false);

    const [publications, publicationFetchStatus] = useLoaderWithStatus(
        () => getLatestPublications(MAX_LISTED_PUBLICATIONS).then((result) => result?.items),
        [publicationChangeTime, ratkoPushChangeTime],
    );

    const publication = publications?.find((p) => p.id == selectedPublication);
    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    const anyFailed = !!publications?.some((p) => ratkoPushFailed(p.ratkoPushStatus));

    return (
        <React.Fragment>
            {!selectedPublication && !showPublicationLog && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        <PublicationCard
                            publications={publications || []}
                            publicationFetchStatus={publicationFetchStatus}
                            onPublicationSelect={(pub) => {
                                onSelectedPublicationChanged(pub.id);
                            }}
                            onShowPublicationLog={() => setShowPublicationLog(true)}
                            ratkoStatus={ratkoStatus}
                        />
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
                    changeTime={publicationChangeTime}
                    publication={publication}
                    onPublicationUnselected={() => onSelectedPublicationChanged(undefined)}
                    anyFailed={anyFailed}
                />
            )}
        </React.Fragment>
    );
};

export default Frontpage;
