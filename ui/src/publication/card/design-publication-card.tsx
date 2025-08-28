import * as React from 'react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { compareTimestamps } from 'utils/date-utils';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { getLatestPublications } from 'publication/publication-api';
import { TimeStamp } from 'common/common-model';
import { DesignPublicationList } from 'publication/card/design-publication-list';
import { getLayoutDesigns } from 'track-layout/layout-design-api';
import PublicationCard, {
    NoPublicationsInfo,
    PUBLICATION_LIST_PAGE_SIZE,
    PublicationCardSection,
    ShowMorePublicationsLink,
} from 'publication/card/publication-card';

type DesignPublicationCardProps = {
    publicationChangeTime: TimeStamp;
    designChangeTime: TimeStamp;
};

const DesignPublicationCard: React.FC<DesignPublicationCardProps> = ({
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
        () => getLatestPublications(PUBLICATION_LIST_PAGE_SIZE * pageCount, 'DESIGN'),
        [publicationChangeTime, pageCount],
    );
    const reachedLastPublication =
        !publications || publications?.items?.length === publications?.totalCount;

    const allPublications =
        publications?.items
            ?.sort((i1, i2) => compareTimestamps(i1.publicationTime, i2.publicationTime))
            ?.reverse() ?? [];

    return (
        <PublicationCard
            title={t('publication-card.designs-title')}
            loading={
                publicationFetchStatus !== LoaderStatus.Ready &&
                designFetchStatus !== LoaderStatus.Ready
            }>
            <React.Fragment>
                {(allPublications.length > 0 || reachedLastPublication) && !!designs && (
                    <PublicationCardSection title={t('publication-card.designs-latest')}>
                        <DesignPublicationList publications={allPublications} designs={designs} />
                    </PublicationCardSection>
                )}
                {allPublications.length === 0 && reachedLastPublication && (
                    <NoPublicationsInfo title={t('publication-card.designs-no-publications')} />
                )}
                {!reachedLastPublication && (
                    <ShowMorePublicationsLink showMore={() => setPageCount(pageCount + 1)} />
                )}
            </React.Fragment>
        </PublicationCard>
    );
};

export default DesignPublicationCard;
