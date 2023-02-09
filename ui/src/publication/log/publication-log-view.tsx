import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { currentDay } from 'utils/date-utils';
import { endOfDay, startOfDay, subMonths } from 'date-fns';
import { getPublications, getPublicationsCsvUri } from 'publication/publication-api';
import PublicationTable from 'publication/table/publication-table';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { PublicationDetails } from 'publication/publication-model';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { SortInformation } from 'publication/table/publication-table-utils';
import { Page } from 'api/api-fetch';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';

export type PublicationLogViewProps = {
    onClose: () => void;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onClose }) => {
    const { t } = useTranslation();

    const [startDate, setStartDate] = React.useState<Date>(subMonths(currentDay, 1));
    const [endDate, setEndDate] = React.useState<Date>(currentDay);
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationDetails>>();
    const [sortInfo, setSortInfo] = React.useState<SortInformation>();

    React.useEffect(() => {
        if (startDate) {
            setPagedPublications(undefined);

            getPublications(startOfDay(startDate), endOfDay(endDate)).then((p) => {
                setPagedPublications(p ?? undefined);
            });
        }
    }, [startDate, endDate]);

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
                {pagedPublications && (
                    <PublicationTable
                        truncated={pagedPublications?.totalCount != pagedPublications?.items.length}
                        publications={pagedPublications.items}
                        onSortChange={setSortInfo}
                    />
                )}
                {!pagedPublications && <Spinner />}
            </div>
        </div>
    );
};

export default PublicationLogView;
