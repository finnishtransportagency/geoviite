import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { currentDay } from 'utils/date-utils';
import { endOfDay, startOfDay, subMonths } from 'date-fns';
import { getPublicationsAsTableItems, getPublicationsCsvUri } from 'publication/publication-api';
import PublicationTable from 'publication/table/publication-table';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    InitiallyUnsorted,
    PublicationDetailsTableSortInformation,
} from 'publication/table/publication-table-utils';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { PublicationTableItem } from 'publication/publication-model';
import { Page } from 'api/api-fetch';

export type PublicationLogProps = {
    onClose: () => void;
};

const PublicationLog: React.FC<PublicationLogProps> = ({ onClose }) => {
    const { t } = useTranslation();

    const [startDate, setStartDate] = React.useState<Date>(subMonths(currentDay, 1));
    const [endDate, setEndDate] = React.useState<Date>(currentDay);
    const [sortInfo, setSortInfo] =
        React.useState<PublicationDetailsTableSortInformation>(InitiallyUnsorted);
    const [isLoading, setIsLoading] = React.useState(true);
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationTableItem>>();

    React.useEffect(() => {
        setIsLoading(true);

        getPublicationsAsTableItems(
            startOfDay(startDate),
            endOfDay(endDate),
            sortInfo.propName,
            sortInfo.direction,
        ).then((r) => {
            r && setPagedPublications(r);
            setIsLoading(false);
        });
    }, [startDate, endDate, sortInfo]);

    const endDateErrors =
        endDate && startDate > endDate ? [t('publication-log.end-before-start')] : [];

    return (
        <div className={styles['publication-log']}>
            <div className={styles['publication-log__title']}>
                <Link onClick={onClose}>{t('frontpage.frontpage-link')}</Link>
                <span className={styles['publication-log__breadcrumbs']}>
                    {' > ' + t('publication-log.breadcrumbs-text')}
                </span>
            </div>
            <div className={styles['publication-log__actions']}>
                <FieldLayout
                    label={t('publication-log.start-date')}
                    value={
                        <DatePicker
                            value={startDate}
                            onChange={(startDate) => setStartDate(startDate)}
                        />
                    }
                />
                <FieldLayout
                    label={t('publication-log.end-date')}
                    value={
                        <DatePicker value={endDate} onChange={(endDate) => setEndDate(endDate)} />
                    }
                    errors={endDateErrors}
                />
                <div className={styles['publication-log__export_button']}>
                    <Button
                        icon={Icons.Download}
                        onClick={() =>
                            (location.href = getPublicationsCsvUri(
                                startDate,
                                endOfDay(endDate),
                                sortInfo?.propName,
                                sortInfo?.direction,
                            ))
                        }>
                        {t('publication-log.export-csv')}
                    </Button>
                </div>
            </div>
            <div className={styles['publication-log__table']}>
                <PublicationTable
                    isLoading={isLoading}
                    truncated={pagedPublications?.totalCount != pagedPublications?.items.length}
                    items={pagedPublications?.items || []}
                    sortInfo={sortInfo}
                    onSortChange={setSortInfo}
                />
            </div>
        </div>
    );
};

export default PublicationLog;
