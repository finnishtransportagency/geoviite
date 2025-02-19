import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Dialog, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Table, Th } from 'vayla-design-lib/table/table';
import { getSplitDetails, splitDetailsCsvUri } from 'publication/publication-api';
import { formatTrackMeter } from 'utils/geography-utils';
import { PublicationId } from 'publication/publication-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import styles from './split-details-dialog.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { TrackMeter } from 'common/common-model';
import { BulkTransferInfo } from 'publication/split/bulk-transfer-info';

export type SplitDetailsViewProps = {
    publicationId: PublicationId;
    onClose: () => void;
};

export const SplitDetailsDialog: React.FC<SplitDetailsViewProps> = ({ publicationId, onClose }) => {
    const { t } = useTranslation();
    const [splitDetails, splitDetailsStatus] = useLoaderWithStatus(
        () => getSplitDetails(publicationId),
        [publicationId],
    );

    return (
        <Dialog
            qaId={'split-details-dialog'}
            width={DialogWidth.THREE_COLUMNS}
            onClose={onClose}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <a href={splitDetailsCsvUri(publicationId)}>
                        <Button variant={ButtonVariant.SECONDARY} icon={Icons.Download}>
                            {t(`split-details-dialog.download-csv`)}
                        </Button>
                    </a>
                    <Button
                        variant={ButtonVariant.PRIMARY}
                        onClick={onClose}
                        qa-id={'split-details-dialog-close'}>
                        {t('button.close')}
                    </Button>
                </div>
            }
            title={t('split-details-dialog.title')}>
            <ProgressIndicatorWrapper
                inline={false}
                indicator={ProgressIndicatorType.Area}
                inProgress={splitDetailsStatus !== LoaderStatus.Ready}>
                <div>
                    <FieldLayout label={t('split-details-dialog.source-name')}>
                        <div qa-id={'split-source-track-name'}>
                            {splitDetails?.locationTrack?.name}
                        </div>
                    </FieldLayout>
                    <FieldLayout label={t('split-details-dialog.source-oid')}>
                        <div>{splitDetails?.locationTrackOid}</div>
                    </FieldLayout>
                    <FieldLayout
                        label={t('split-details-dialog.targets', {
                            trackCount: splitDetails?.targetLocationTracks?.length,
                        })}>
                        <div className={styles['split-details-dialog__table']}>
                            <Table wide>
                                <thead className={styles['split-details-dialog__table-header']}>
                                    <tr>
                                        <Th>{t('split-details-dialog.target-name')}</Th>
                                        <Th>{t('split-details-dialog.target-oid')}</Th>
                                        <Th>{t('split-details-dialog.operation')}</Th>
                                        <Th>{t('split-details-dialog.start-address')}</Th>
                                        <Th>{t('split-details-dialog.end-address')}</Th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {splitDetails?.targetLocationTracks.map((target) => (
                                        <tr key={target.id}>
                                            <td>{target.name}</td>
                                            <td>{target.oid}</td>
                                            <td>
                                                {t(`enum.SplitTargetOperation.${target.operation}`)}
                                            </td>
                                            <td>{formatAddress(target.startAddress)}</td>
                                            <td>{formatAddress(target.endAddress)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </Table>
                        </div>
                    </FieldLayout>
                    {splitDetails?.bulkTransfer && (
                        <BulkTransferInfo bulkTransfer={splitDetails.bulkTransfer} />
                    )}
                </div>
            </ProgressIndicatorWrapper>
        </Dialog>
    );
};

function formatAddress(address: TrackMeter | undefined): string | undefined {
    return address ? formatTrackMeter(address) : undefined;
}
