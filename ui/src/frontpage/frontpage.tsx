import * as React from 'react';
import PublicationCard from 'publication/card/publication-card';
import styles from './frontpage.scss';
import PublicationDetails from 'publication/publication-details';
import { PublicationDetails as PublicationDetailsModel } from 'publication/publication-model';
import { useLoaderWithTimer } from 'utils/react-utils';
import { UserCardContainer } from 'user/user-card-container';
import { getRatkoStatus, RatkoStatus } from 'ratko/ratko-api';
import PublicationLogView from 'publication/log/publication-log-view';
import { getPublications } from 'publication/publication-api';
import { startOfDay, subMonths } from 'date-fns';
import { ratkoPushFailed } from 'ratko/ratko-model';

type FrontPageProps = {
    selectedPublication: PublicationDetailsModel | undefined;
    onSelectedPublicationChanged: (item: PublicationDetailsModel | undefined) => void;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
}) => {
    const [publications, setPublications] = React.useState<PublicationDetailsModel[] | null>();
    const [ratkoStatus, setRatkoStatus] = React.useState<RatkoStatus | undefined>();
    const [showPublicationLog, setShowPublicationLog] = React.useState(false);

    useLoaderWithTimer(
        setPublications,
        () => {
            return getPublications(startOfDay(subMonths(new Date(), 1)));
        },
        [],
        30000,
    );
    useLoaderWithTimer(setRatkoStatus, getRatkoStatus, [], 30000);

    React.useEffect(() => {
        if (selectedPublication && publications) {
            const updatedPublication = publications.find((p) => p.id == selectedPublication.id);
            onSelectedPublicationChanged(updatedPublication);
        }
    }, [publications]);

    const anyFailed = !!publications?.some((p) => ratkoPushFailed(p.ratkoPushStatus));

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
            {selectedPublication && (
                <PublicationDetails
                    publication={selectedPublication}
                    onPublicationUnselected={() => onSelectedPublicationChanged(undefined)}
                    anyFailed={anyFailed}
                />
            )}
        </React.Fragment>
    );
};

export default Frontpage;
