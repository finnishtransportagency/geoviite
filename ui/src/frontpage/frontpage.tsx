import * as React from 'react';
import PublicationCard from 'publication/publication-card';
import styles from './frontpage.scss';
import PublicationDetails from 'publication/publication-details';
import { PublicationDetails as PublicationDetailsModel } from 'publication/publication-model';
import { useLoaderWithTimer } from 'utils/react-utils';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';
import PublicationLogView from 'publication-log/publication-log-view';
import { getPublications } from 'publication/publication-api';
import { subMonths } from 'date-fns';

type FrontPageProps = {
    selectedPublication: PublicationDetailsModel | undefined;
    onSelectedPublicationChanged: (item: PublicationDetailsModel | undefined) => void;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
}) => {
    const [publications, setPublications] = React.useState<PublicationDetailsModel[] | undefined>();
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();
    const [showPublicationLog, setShowPublicationLog] = React.useState(false);

    useLoaderWithTimer(
        setPublications,
        () => {
            return getPublications(subMonths(new Date(), 3));
        },
        [],
        30000,
    );
    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    const hasAnyFailed = () =>
        !!publications && publications?.some((item) => ratkoPushFailed(item.ratkoPushStatus));

    return (
        <React.Fragment>
            {!selectedPublication && !showPublicationLog && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        {publications && (
                            <PublicationCard
                                publications={publications}
                                itemClicked={(pub) => {
                                    onSelectedPublicationChanged(pub);
                                }}
                                onShowPublicationLog={() => setShowPublicationLog(true)}
                                anyFailed={hasAnyFailed()}
                                ratkoStatus={ratkoStatus}
                            />
                        )}
                        <UserCardContainer />
                    </div>
                    <div className={styles['frontpage__photo']} />
                </React.Fragment>
            )}
            {!selectedPublication && showPublicationLog && (
                <PublicationLogView onClose={() => setShowPublicationLog(false)} />
            )}
            {selectedPublication !== undefined && (
                <PublicationDetails
                    publication={selectedPublication}
                    onPublicationUnselected={() => onSelectedPublicationChanged(undefined)}
                    anyFailed={hasAnyFailed()}
                />
            )}
        </React.Fragment>
    );
};

export default Frontpage;
