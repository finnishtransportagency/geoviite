import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { currentDay } from 'utils/date-utils';
import { startOfMonth } from 'date-fns';

export type PublicationLogViewProps = {
    onClose: () => void;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onClose }) => {
    const { t } = useTranslation();
    const [startDate, setStartDate] = React.useState<Date>(startOfMonth(currentDay));
    const [endDate, setEndDate] = React.useState<Date>(currentDay);

    return (
        <div className={styles['publication-log__view']}>
            <div className={styles['publication-log__title']}>
                <Link onClick={onClose}>{t('frontpage.frontpage-link')}</Link>
                <span className={styles['publication-log__breadcrumbs']}>
                    {t('publication-log.breadcrumbs-text')}
                </span>
            </div>
            <div className={styles['publication-log__datepickers']}>
                <div>
                    {t('publication-log.start-date')}
                    <DatePicker
                        value={startDate}
                        onChange={(startDate) => setStartDate(startDate)}
                    />
                </div>
                <div>
                    {t('publication-log.end-date')}
                    <DatePicker value={endDate} onChange={(endDate) => setEndDate(endDate)} />
                </div>
            </div>
            <div className={styles['publication-log__content']}>
                {/*
                <PublicationLogTable startDate={startDate} endDate={endDate} />
                 */}
            </div>
        </div>
    );
};

export default PublicationLogView;
