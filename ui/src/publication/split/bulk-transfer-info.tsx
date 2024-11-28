import * as React from 'react';

import { Table, Th } from 'vayla-design-lib/table/table';
import { BulkTransfer } from 'publication/publication-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { useTranslation } from 'react-i18next';
import { enumTranslation } from 'utils/translation-utils';

export type BulkTransferInfoProps = {
    bulkTransfer: BulkTransfer;
};

function calcTrexAssetsMoved(bulkTransfer: BulkTransfer): number | undefined {
    return bulkTransfer.trexAssetsRemaining && bulkTransfer.trexAssetsTotal
        ? bulkTransfer.trexAssetsTotal - bulkTransfer.trexAssetsRemaining
        : undefined;
}

export const BulkTransferInfo: React.FC<BulkTransferInfoProps> = ({ bulkTransfer }) => {
    const { t } = useTranslation();

    const state = enumTranslation(t, 'BulkTransferState', bulkTransfer.state);
    const expeditedStart = bulkTransfer.expeditedStart
        ? t('split-details-dialog.bulk-transfer.expedited-start-enabled')
        : t('split-details-dialog.bulk-transfer.expedited-start-disabled');

    const temporaryFailure = bulkTransfer.temporaryFailure
        ? t('split-details-dialog.bulk-transfer.temporary-failure-yes')
        : t('split-details-dialog.bulk-transfer.temporary-failure-no');

    const noValue = t('split-details-dialog.bulk-transfer.no-value');

    const ratkoStartTime = bulkTransfer.ratkoStartTime ?? noValue;
    const ratkoEndTime = bulkTransfer.ratkoEndTime ?? noValue;

    const assetsMoved = bulkTransfer.assetsMoved ?? '-';
    const assetsTotal = bulkTransfer.assetsTotal ?? '-';
    const assetAmounts = `${assetsMoved} / ${assetsTotal}`;

    const trexAssetsMoved = calcTrexAssetsMoved(bulkTransfer) ?? '-';
    const trexAssetsTotal = bulkTransfer.trexAssetsTotal ?? '-';
    const trexAssetAmounts = `${trexAssetsMoved} / ${trexAssetsTotal}`;

    return (
        <FieldLayout label={t('split-details-dialog.bulk-transfer.title')}>
            <Table wide>
                <thead>
                    <tr>
                        <Th>{t('split-details-dialog.bulk-transfer.state')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.expedited-start')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.temporary-failure')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.ratko-start-time')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.ratko-end-time')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.assets')}</Th>
                        <Th>{t('split-details-dialog.bulk-transfer.trex-assets')}</Th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>{state}</td>
                        <td>{expeditedStart}</td>
                        <td>{temporaryFailure}</td>
                        <td>{ratkoStartTime}</td>
                        <td>{ratkoEndTime}</td>
                        <td>{assetAmounts}</td>
                        <td>{trexAssetAmounts}</td>
                    </tr>
                </tbody>
            </Table>
        </FieldLayout>
    );
};
