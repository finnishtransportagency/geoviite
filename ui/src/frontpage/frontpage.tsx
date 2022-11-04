import * as React from 'react';
import Card from 'card/card';
import PublicationCard from 'publication/publication-card';
import styles from './frontpage.scss';
import PublicationDetails from 'publication/publication-details';
import { PublicationListingItem, ratkoPushFailed } from 'publication/publication-model';
import { getPublications } from 'publication/publication-api';
import { useTranslation } from 'react-i18next';

type FrontPageProps = {
    selectedPublication: PublicationListingItem | undefined;
    onSelectedPublicationChanged: (item: PublicationListingItem | undefined) => void;
};

const Frontpage: React.FC<FrontPageProps> = ({
    selectedPublication,
    onSelectedPublicationChanged,
}) => {
    const { t } = useTranslation();
    const [publications, setPublications] = React.useState<PublicationListingItem[] | null>();

    React.useEffect(() => {
        let cancel = false;
        function fetchPublications () {
            getPublications().then(result => {
                if (!cancel) {
                    setPublications(result)
                }
            });
        }

        fetchPublications();
        const intervalTimer = setInterval(fetchPublications, 30000)

        return () => {
            cancel = true;
            clearInterval(intervalTimer)
        };
    }, []);

    const hasAnyFailed = () =>
        !!publications && publications?.some((item) => ratkoPushFailed(item.status));

    return (
        <React.Fragment>
            {!selectedPublication && publications && (
                <React.Fragment>
                    <div className={styles['frontpage']}>
                        <Card
                            className={styles['frontpage__card']}
                            header={t('frontpage.publications')}
                            content={
                                <PublicationCard
                                    publications={publications}
                                    itemClicked={(pub) => {
                                        onSelectedPublicationChanged(pub);
                                    }}
                                    anyFailed={hasAnyFailed()}
                                />
                            }
                        />
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
