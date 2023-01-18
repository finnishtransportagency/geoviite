import * as React from 'react';
import { KmNumber, TimeStamp, TrackNumber } from 'common/common-model';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { Operation } from 'publication/publication-model';

export type PublicationTableRowProps = {
    name: string;
    trackNumbers: TrackNumber[];
    changedKmNumbers?: KmNumber[];
    operation: Operation;
    publicationTime: TimeStamp;
    publicationUser: string;
    definition: string;
    ratkoPushTime: TimeStamp | null;
};

const formatKmNumber = (kmNumbers: KmNumber[]) => {
    return kmNumbers
        .sort()
        .reduce((acc, value) => {
            if (acc.length == 0) acc.push([value]);
            else {
                const previousArr = acc[acc.length - 1];
                const previousValue = previousArr[previousArr.length - 1];
                const currentIntValue = parseInt(value);
                const previousIntValue = parseInt(previousValue);

                //Group kilometer numbers with extensions
                //For example, [0001, 0001A, 0001B, 0003] => 0001 - 0001B, 0003
                if (
                    previousIntValue === currentIntValue ||
                    currentIntValue === previousIntValue + 1
                ) {
                    previousArr[1] = value;
                } else {
                    acc.push([value]);
                }
            }

            return acc;
        }, [] as KmNumber[][])
        .map((kmNumbers) => kmNumbers.join('–'))
        .map((kmNumbers) => (
            <span key={kmNumbers}>
                {kmNumbers}
                <br />
            </span>
        ));
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
            <td>{changedKmNumbers ? formatKmNumber(changedKmNumbers) : ''}</td>
            <td>{t(`enum.publish-operation.${operation}`)}</td>
            <td>{formatDateFull(publicationTime)}</td>
            <td>{publicationUser}</td>
            <td>{definition}</td>
            <td>{ratkoPushTime ? formatDateFull(ratkoPushTime) : t('no')}</td>
        </tr>
    );
};
