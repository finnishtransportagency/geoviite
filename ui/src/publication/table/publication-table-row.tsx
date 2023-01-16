import * as React from 'react';
import { KmNumber, TimeStamp, TrackNumber } from 'common/common-model';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { Operation } from 'publication/publication-model';

export type PublicationTableRowProps = {
    name: string;
    trackNumbers: TrackNumber[];
    changedKmNumbers: KmNumber[];
    operation: Operation;
    publicationTime: TimeStamp;
    publicationUser: string;
    definition: string;
    ratkoPushTime: TimeStamp | null;
};

export const PublicationTableRow: React.FC<PublicationTableRowProps> = ({
    name,
    trackNumbers,
    changedKmNumbers,
    operation,
    publicationTime,
    publicationUser,
    definition,
    ratkoPushTime,
}) => {
    const { t } = useTranslation();

    return (
        <tr className={'publication-table__row'}>
            <td>{name}</td>
            <td>{trackNumbers.sort().join(', ')}</td>
            <td>{changedKmNumbers.sort().join(', ')}</td>
            <td>{t(`enum.publish-operation.${operation}`)}</td>
            <td>{formatDateFull(publicationTime)}</td>
            <td>{publicationUser}</td>
            <td>{definition}</td>
            <td>{ratkoPushTime ? formatDateFull(ratkoPushTime) : t('no')}</td>
        </tr>
    );
};
