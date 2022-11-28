import * as React from 'react';
import PublicationCard from 'publication/publication-card';
import styles from './frontpage.scss';
import PublicationDetails from 'publication/publication-details';
import { PublicationListingItem } from 'publication/publication-model';
import { getPublications } from 'publication/publication-api';
import { ratkoPushFailed } from 'ratko/ratko-model';
import { UserCardContainer } from 'user/user-card-container';

type FrontPageProps = {
    selectedPublication: PublicationListingItem | undefined;
    onSelectedPublicationChanged: (item: PublicationListingItem | undefined) => void;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
}) => {
    const [publications, setPublications] = React.useState<PublicationListingItem[] | null>();

    React.useEffect(() => {
        let cancel = false;

        function fetchPublications() {
            getPublications().then((result) => {
                if (!cancel) {
                    setPublications(result);
                }
            });
        }

        fetchPublications();
        const intervalTimer = setInterval(fetchPublications, 30000);

        return () => {
            cancel = true;
            clearInterval(intervalTimer);
        };
    }, []);

    const hasAnyFailed = () =>
        !!publications && publications?.some((item) => ratkoPushFailed(item.status));

    return (
        <React.Fragment>
            {!selectedPublication && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        {publications && (
                            <PublicationCard
                                publications={publications}
                                itemClicked={(pub) => {
                                    onSelectedPublicationChanged(pub);
                                }}
                                anyFailed={hasAnyFailed()}
                            />
                        )}
                        <UserCardContainer />
                    </div>
                    <div className={styles['frontpage__photo']} />
                </React.Fragment>
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
