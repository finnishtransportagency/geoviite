import * as React from 'react';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import {
    PublicationChange,
    PublicationId,
    PublicationTableItem,
} from 'publication/publication-model';
import styles from './publication-table.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import {
    PublicationTableDetails,
    PublicationTableWithoutDetails,
} from 'publication/table/publication-table-details';
import { SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { SearchablePublicationLogItem } from 'publication/log/publication-log';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { PublishedAsset } from 'publication/publication-api';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { PublicationDisplayMode } from 'publication/table/publication-table-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type PublicationTableRowProps = {
    publicationDisplayMode: PublicationDisplayMode;
    propChanges: PublicationChange[];
    detailsVisible: boolean;
    detailsVisibleToggle: (id: PublicationId) => void;
    displaySinglePublication: (id: PublicationId) => void;
    displayItemHistory: (item: SearchItemValue<SearchablePublicationLogItem>) => void;
    allLayoutTrackNumbers: LayoutTrackNumber[];
} & PublicationTableItem;

type PublicationTableMessageProps = {
    mode: PublicationDisplayMode;
    message: string;
    navigateToPublication: () => void;
};

const PublicationTableMessage: React.FC<PublicationTableMessageProps> = ({
    mode,
    message,
    navigateToPublication,
}) => {
    switch (mode) {
        case 'PUBLICATION_LOG':
        case 'SINGLE_ASSET':
            return <AnchorLink onClick={navigateToPublication}>{message}</AnchorLink>;
        case 'SINGLE_PUBLICATION_DETAILS':
            return message;
        default:
            return exhaustiveMatchingGuard(mode);
    }
};

const getTrackNumberForReferenceLine = (
    asset: PublishedAsset,
    trackNumbers: LayoutTrackNumber[],
): SearchItemValue<SearchablePublicationLogItem> | undefined => {
    if (asset.type !== 'REFERENCE_LINE') {
        return undefined;
    } else {
        const trackNumber = trackNumbers.find((tn) => tn.id === asset.asset.trackNumberId);

        return trackNumber ? { trackNumber, type: SearchItemType.TRACK_NUMBER } : undefined;
    }
};

const assetToSearchItem = (
    asset: PublishedAsset,
    trackNumbers: LayoutTrackNumber[],
): SearchItemValue<SearchablePublicationLogItem> | undefined => {
    switch (asset.type) {
        case 'TRACK_NUMBER':
            return { trackNumber: asset.asset, type: SearchItemType.TRACK_NUMBER };
        case 'REFERENCE_LINE':
            return getTrackNumberForReferenceLine(asset, trackNumbers);
        case 'LOCATION_TRACK':
            return { locationTrack: asset.asset, type: SearchItemType.LOCATION_TRACK };
        case 'SWITCH':
            return { layoutSwitch: asset.asset, type: SearchItemType.SWITCH };
        case 'KM_POST':
            return { kmPost: asset.asset, type: SearchItemType.KM_POST };
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
    allLayoutTrackNumbers,
    publicationTime,
    publicationUser,
    message,
    ratkoPushTime,
    propChanges,
    detailsVisible,
    detailsVisibleToggle,
    displaySinglePublication,
    displayItemHistory,
    publicationDisplayMode,
}) => {
    const { t } = useTranslation();
    const rowClassNames = createClassName(
        styles['publication-table__row'],
        detailsVisible && styles['publication-table__row--details-are-visible'],
    );

    const assetAsSearchItem = assetToSearchItem(asset, allLayoutTrackNumbers);
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
                    <PublicationTableMessage
                        mode={publicationDisplayMode}
                        message={message}
                        navigateToPublication={() => displaySinglePublication(publicationId)}
                    />
                </td>
                <td>{ratkoPushTime ? formatDateFull(ratkoPushTime) : t('no')}</td>
            </tr>
            {detailsVisible && (
                <tr className={styles['publication-table__details-row']}>
                    <td className={styles['publication-table__details-left-bar-container']}>
                        <span className={styles['publication-table__details-left-bar']}></span>
                    </td>
                    <td colSpan={8}>
                        {propChanges.length > 0 ? (
                            <PublicationTableDetails id={id} changes={propChanges} />
                        ) : (
                            <PublicationTableWithoutDetails />
                        )}
                    </td>
                </tr>
            )}
        </React.Fragment>
    );
};

export default React.memo(PublicationTableRow);
