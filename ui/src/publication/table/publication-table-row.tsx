import * as React from 'react';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { PublicationChange, PublicationTableItem } from 'publication/publication-model';
import styles from './publication-table.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { PublicationTableDetails } from 'publication/table/publication-table-details';
import { first } from 'utils/array-utils';

type PublicationTableRowProps = {
    propChanges: PublicationChange[];
    detailsVisible: boolean;
    detailsVisibleToggle: () => void;
} & PublicationTableItem;

export const PublicationTableRow: React.FC<PublicationTableRowProps> = ({
    id,
    name,
    trackNumbers,
    changedKmNumbers,
    operation,
    publicationTime,
    publicationUser,
    message,
    ratkoPushTime,
    propChanges,
    detailsVisible,
    detailsVisibleToggle,
}) => {
    const { t } = useTranslation();
    const messageRows = message.split('\n');
    const [messageExpanded, setMessageExpanded] = React.useState(false);
    const contentClassNames = createClassName(
        styles['publication-table__message-content'],
        messageExpanded
            ? styles['publication-table__message-content--expanded']
            : styles['publication-table__message-content--collapsed'],
    );
    const chevronClassNames = createClassName(
        styles['publication-table__message-icon'],
        messageExpanded ? styles['publication-table__message-icon--open'] : undefined,
    );

    const rowClassNames = createClassName(
        'publication-table__row',
        detailsVisible && styles['publication-table__row-details--borderless'],
    );

    return (
        <React.Fragment>
            <tr className={rowClassNames}>
                <td className={styles['publication-table__accordion-column']}>
                    <AccordionToggle
                        open={detailsVisible}
                        onToggle={() => detailsVisibleToggle()}
                    />
                </td>
                <td>{name}</td>
                <td>{trackNumbers.sort().join(', ')}</td>
                <td>
                    {changedKmNumbers
                        .map((value) =>
                            value.min !== value.max ? `${value.min}-${value.max}` : `${value.min}`,
                        )
                        .join(', ')}
                </td>
                <td>{t(`enum.publish-operation.${operation}`)}</td>
                <td>{formatDateFull(publicationTime)}</td>
                <td>{publicationUser}</td>
                <td className={styles['publication-table__message-column']} title={message}>
                    <Button
                        className={chevronClassNames}
                        icon={Icons.Down}
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        onClick={() => setMessageExpanded(!messageExpanded)}
                    />
                    <div className={contentClassNames}>
                        {messageExpanded ? message : first(messageRows)}
                    </div>
                </td>
                <td>{ratkoPushTime ? formatDateFull(ratkoPushTime) : t('no')}</td>
            </tr>
            {detailsVisible && (
                <tr>
                    <td className={styles['publication-table__row-details-left-bar-container']}>
                        <span className={styles['publication-table__row-details-left-bar']}></span>
                    </td>
                    <td colSpan={8}>
                        <PublicationTableDetails id={id} changes={propChanges} />
                    </td>
                </tr>
            )}
        </React.Fragment>
    );
};
