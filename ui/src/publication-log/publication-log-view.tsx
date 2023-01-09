import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import PublicationLogTable from 'publication-log/publication-log-table';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { currentDay, getTomorrow } from 'utils/date-utils';

export type PublicationLogViewProps = {
    onLogUnselected: () => void;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onLogUnselected }) => {
    const { t } = useTranslation();
    const [startDate, setStartDate] = React.useState<Date>(currentDay);
    const [endDate, setEndDate] = React.useState<Date>(getTomorrow(currentDay));

    return (
        <div className={styles['publication-log__view']}>
            <div className={styles['publication-log__title']}>
                <Link
                    onClick={() => {
                        onLogUnselected();
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span className={styles['publication-log__breadcrumbs']}>
                    {t('publication-log.breadcrumbs-text')}
                </span>
            </div>
            <div className={styles['publication-log__date-pickers']}>
                <div className={styles['publication-log__flex-child']}>
                    {t('publication-log.start-date')}
                    <DatePicker
                        value={startDate}
                        onChange={(startDate) => setStartDate(startDate)}
                    />
                </div>
                <div className={styles['publication-log__flex-child']}>
                    {t('publication-log.end-date')}
                    <DatePicker value={endDate} onChange={(endDate) => setEndDate(endDate)} />
                </div>
            </div>
            <div className={styles['publication-log__content']}>
                <PublicationLogTable startDate={startDate} endDate={endDate} />
            </div>
        </div>
    );
};

export default PublicationLogView;
