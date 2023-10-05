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

    const [startDate, setStartDate] = React.useState<Date | undefined>(subMonths(currentDay, 1));
    const [endDate, setEndDate] = React.useState<Date | undefined>(currentDay);
    const [sortInfo, setSortInfo] =
        React.useState<PublicationDetailsTableSortInformation>(InitiallyUnsorted);
    const [isLoading, setIsLoading] = React.useState(true);
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationTableItem>>();

    React.useEffect(() => {
        setIsLoading(true);

        getPublicationsAsTableItems(
            startDate && startOfDay(startDate),
            endDate && endOfDay(endDate),
            sortInfo.propName,
            sortInfo.direction,
        ).then((r) => {
            r && setPagedPublications(r);
            setIsLoading(false);
        });
    }, [startDate, endDate, sortInfo]);

    const endDateErrors =
        startDate && endDate && startDate > endDate ? [t('publication-log.end-before-start')] : [];

    const truncated =
        pagedPublications !== undefined &&
        pagedPublications.totalCount !== pagedPublications.items.length;

    return (
        <div className={styles['publication-log']}>
            <div className={styles['publication-log__title']}>
                <Link onClick={onClose}>{t('frontpage.frontpage-link')}</Link>
                <span className={styles['publication-log__breadcrumbs']}>
                    {' > ' + t('publication-log.breadcrumbs-text')}
                </span>
            </div>
            <div className={styles['publication-log__content']}>
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
                            <DatePicker
                                value={endDate}
                                onChange={(endDate) => setEndDate(endDate)}
                            />
                        }
                        errors={endDateErrors}
                    />
                    <div className={styles['publication-log__export_button']}>
                        <Button
                            icon={Icons.Download}
                            onClick={() =>
                                (location.href = getPublicationsCsvUri(
                                    startDate,
                                    endDate && endOfDay(endDate),
                                    sortInfo?.propName,
                                    sortInfo?.direction,
                                ))
                            }>
                            {t('publication-log.export-csv')}
                        </Button>
                    </div>
                </div>
                <div className={styles['publication-log__count-header']}>
                    <span
                        title={
                            truncated
                                ? t('publication-table.truncated', {
                                      number: pagedPublications?.items?.length || 0,
                                  })
                                : ''
                        }>
                        {t('publication-table.count-header', {
                            number: pagedPublications?.items?.length || 0,
                            truncated: truncated ? '+' : '',
                        })}
                    </span>
                </div>
                <PublicationTable
                    isLoading={isLoading}
                    items={pagedPublications?.items || []}
                    sortInfo={sortInfo}
                    onSortChange={setSortInfo}
                />
            </div>
        </div>
    );
};

export default PublicationLog;
