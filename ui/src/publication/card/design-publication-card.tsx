import * as React from 'react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import Card from 'geoviite-design-lib/card/card';
import styles from './publication-card.scss';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { getLatestPublications } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { DesignPublicationList } from 'publication/card/design-publication-list';
import { getLayoutDesigns } from 'track-layout/layout-design-api';

type PublishListProps = {
    publicationChangeTime: TimeStamp;
    designChangeTime: TimeStamp;
};

export const MAX_LISTED_PUBLICATIONS = 8;

const DesignPublicationCard: React.FC<PublishListProps> = ({
    publicationChangeTime,
    designChangeTime,
}) => {
    const { t } = useTranslation();

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    useEffect(() => {
        trackLayoutActionDelegates.clearPublicationSelection();
    }, []);

    const [pageCount, setPageCount] = React.useState(1);
    const [designs, designFetchStatus] = useLoaderWithStatus(
        () => getLayoutDesigns(true, true, designChangeTime),
        [designChangeTime],
    );

    const [publications, publicationFetchStatus] = useLoaderWithStatus(
        () => getLatestPublications(MAX_LISTED_PUBLICATIONS * pageCount, 'DESIGN'),
        [publicationChangeTime, pageCount],
    );
    const reachedLastPublication =
        !publications || publications?.items?.length === publications?.totalCount;

    const allPublications =
        publications?.items
            ?.sort((i1, i2) => compareTimestamps(i1.publicationTime, i2.publicationTime))
            ?.reverse() ?? [];

    return (
        <Card
            className={styles['publication-card']}
            content={
                <React.Fragment>
                    <h2 className={styles['publication-card__title']}>
                        {t('publication-card.designs-title')}
                    </h2>
                    <ProgressIndicatorWrapper
                        indicator={ProgressIndicatorType.Area}
                        inProgress={
                            publicationFetchStatus !== LoaderStatus.Ready &&
                            designFetchStatus !== LoaderStatus.Ready
                        }>
                        {(allPublications.length > 0 || reachedLastPublication) && !!designs && (
                            <section>
                                <h3 className={styles['publication-card__subsection-title']}>
                                    {t('publication-card.designs-latest')}
                                </h3>
                                <DesignPublicationList
                                    publications={allPublications}
                                    designs={designs}
                                />
                            </section>
                        )}
                        {allPublications.length === 0 && reachedLastPublication && (
                            <div className={styles['publication-card__no-publications']}>
                                {t('publication-card.designs-no-publications')}
                            </div>
                        )}
                        {!reachedLastPublication && (
                            <div className={styles['publication-card__show-more']}>
                                <AnchorLink onClick={() => setPageCount(pageCount + 1)}>
                                    {t('publication-card.show-more')}
                                </AnchorLink>
                            </div>
                        )}
                        <br />
                    </ProgressIndicatorWrapper>
                </React.Fragment>
            }
        />
    );
};

export default DesignPublicationCard;
