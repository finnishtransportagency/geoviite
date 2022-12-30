import * as React from 'react';
import { TimeStamp } from 'common/common-model';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { Operation } from 'publication/publication-model';

export type PreviewTableItemProps = {
    itemName: string;
    trackNumber?: string;
    changeTime: TimeStamp;
    ratkoPushDate: TimeStamp | null;
    operation: Operation | null;
    userName: string;
};

export const PublicationTableItem: React.FC<PreviewTableItemProps> = ({
    itemName,
    trackNumber,
    changeTime,
    ratkoPushDate,
    operation,
    userName,
}) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <tr className={'preview-table-item'}>
                <td>{itemName}</td>
                <td>{trackNumber ? trackNumber : ''}</td>
                <td>{operation ? t(`enum.publish-operation.${operation}`) : ''}</td>
                <td>{formatDateFull(changeTime)}</td>
                <td>{userName}</td>
                <td>{ratkoPushDate ? formatDateFull(ratkoPushDate) : t('no')}</td>
            </tr>
        </React.Fragment>
    );
};
