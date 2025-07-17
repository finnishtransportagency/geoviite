import * as React from 'react';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import {
    PublicationChange,
    PublicationId,
    PublicationTableItem,
} from 'publication/publication-model';
import styles from './publication-table.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { PublicationTableDetails } from 'publication/table/publication-table-details';
import { first } from 'utils/array-utils';
import { SearchItemType, SearchItemValue } from 'tool-bar/search-dropdown';
import { SearchablePublicationLogItem } from 'publication/log/publication-log';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { PublishedAsset } from 'publication/publication-api';

type PublicationTableRowProps = {
    propChanges: PublicationChange[];
    detailsVisible: boolean;
    detailsVisibleToggle: (id: PublicationId) => void;
    displaySinglePublication: (id: PublicationId) => void;
    displayItemHistory: (item: SearchItemValue<SearchablePublicationLogItem>) => void;
} & PublicationTableItem;

const assetToSearchItem = (
    asset: PublishedAsset,
): SearchItemValue<SearchablePublicationLogItem> | undefined => {
    switch (asset.type) {
        case 'TRACK_NUMBER':
            return { trackNumber: asset.asset, type: SearchItemType.TRACK_NUMBER };
        case 'LOCATION_TRACK':
            return { locationTrack: asset.asset, type: SearchItemType.LOCATION_TRACK };
        case 'SWITCH':
            return { layoutSwitch: asset.asset, type: SearchItemType.SWITCH };
        default:
            return undefined;
    }
};

const PublicationTableRow: React.FC<PublicationTableRowProps> = ({
    id,
    asset,
    publicationId,
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
    displaySinglePublication,
    displayItemHistory,
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

    const assetAsSearchItem = assetToSearchItem(asset);
    const displaySingleItem =
        assetAsSearchItem === undefined
            ? undefined
            : () => {
                  displayItemHistory(assetAsSearchItem);
              };

    return (
        <React.Fragment>
            <tr className={rowClassNames}>
                <td>
                    <AccordionToggle
                        open={detailsVisible}
                        onToggle={() => detailsVisibleToggle(id)}
                    />
                </td>
                <td>
                    {displaySingleItem ? (
                        <AnchorLink onClick={displaySingleItem}>{name}</AnchorLink>
                    ) : (
                        name
                    )}
                </td>
                <td>{trackNumbers.sort().join(', ')}</td>
                <td>
                    {changedKmNumbers
                        .map((value) =>
                            value.min !== value.max ? `${value.min}-${value.max}` : `${value.min}`,
                        )
                        .join(', ')}
                </td>
                <td>{t(`enum.Operation.${operation}`)}</td>
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
                        <AnchorLink onClick={() => displaySinglePublication(publicationId)}>
                            {messageExpanded ? message : first(messageRows)}
                        </AnchorLink>
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

export default React.memo(PublicationTableRow);
