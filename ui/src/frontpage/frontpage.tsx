import * as React from 'react';
import PublicationCard from 'publication/publication-card';
import styles from './frontpage.scss';
import PublicationDetails from 'publication/publication-details';
import { PublicationListingItem } from 'publication/publication-model';
import { getPublications } from 'publication/publication-api';
import { useLoaderWithTimer } from 'utils/react-utils';
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

    useLoaderWithTimer(setPublications, getPublications, [], 30000);

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
