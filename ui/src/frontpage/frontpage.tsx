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
import PublicationLogLink from 'publication-log/publication-log-link';
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
    const [showPublicationLogItems, setShowPublicationLogItems] = React.useState<
        PublicationDetailsModel[] | undefined
    >(undefined);

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
            {!selectedPublication && !showPublicationLogItems && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        {publications && (
                            <PublicationLogLink
                                setShowPublicationLogItems={() =>
                                    setShowPublicationLogItems(publications)
                                }
                            />
                        )}
                        {publications && (
                            <PublicationCard
                                publications={publications}
                                itemClicked={(pub) => {
                                    onSelectedPublicationChanged(pub);
                                }}
                                anyFailed={hasAnyFailed()}
                                ratkoStatus={ratkoStatus}
                            />
                        )}
                        <UserCardContainer />
                    </div>
                    <div className={styles['frontpage__photo']} />
                </React.Fragment>
            )}
            {!selectedPublication && showPublicationLogItems && (
                <PublicationLogView onLogUnselected={() => setShowPublicationLogItems(undefined)} />
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
