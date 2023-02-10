import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { currentDay } from 'utils/date-utils';
import { endOfDay, startOfDay, subMonths } from 'date-fns';
import { getPublicationsAsTableRows, getPublicationsCsvUri } from 'publication/publication-api';
import PublicationTable from 'publication/table/publication-table';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { InitiallyUnsorted, SortInformation } from 'publication/table/publication-table-utils';
import { useLoaderWithStatus } from 'utils/react-utils';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';

export type PublicationLogViewProps = {
    onClose: () => void;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onClose }) => {
    const { t } = useTranslation();

    const [startDate, setStartDate] = React.useState<Date>(subMonths(currentDay, 1));
    const [endDate, setEndDate] = React.useState<Date>(currentDay);
    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);

    const [publications, _] = useLoaderWithStatus(
        () =>
            getPublicationsAsTableRows(
                startOfDay(startDate),
                endOfDay(endDate),
                sortInfo.propName,
                sortInfo.direction,
            ),
        [startDate, endDate, sortInfo],
    );

    const endDateErrors =
        endDate && startDate > endDate ? [t('publication-log.end-before-start')] : [];

    return (
        <div className={styles['publication-log__view']}>
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
            <div className={styles['publication-log__content']}>
                {publications && (
                    <PublicationTable
                        truncated={publications.totalCount != publications.items.length}
                        items={publications.items}
                        sortInfo={sortInfo}
                        onSortChange={setSortInfo}
                    />
                )}
            </div>
        </div>
    );
};

export default PublicationLogView;
