import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import PublicationLogTable from 'publication-log/publication-log-table';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';

export type PublicationLogViewProps = {
    onLogUnselected: () => void;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onLogUnselected }) => {
    const { t } = useTranslation();
    const [startDate, setStartDate] = React.useState<Date>();
    const [endDate, setEndDate] = React.useState<Date>();
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
            <div>
                StartDate:
                <DatePicker value={startDate} onChange={(date) => setStartDate(date)} />
                EndDate:
                <DatePicker value={endDate} onChange={(date) => setEndDate(date)} />
            </div>
            <div className={styles['publication-log__content']}>
                <PublicationLogTable startDate={startDate} endDate={endDate} />
            </div>
        </div>
    );
};

export default PublicationLogView;
