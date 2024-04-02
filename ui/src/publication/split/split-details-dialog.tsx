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
            width={DialogWidth.THREE_COLUMNS}
            onClose={onClose}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <a href={splitDetailsCsvUri(publicationId)}>
                        <Button variant={ButtonVariant.SECONDARY} icon={Icons.Download}>
                            {t(`split-details-dialog.download-csv`)}
                        </Button>
                    </a>
                    <Button variant={ButtonVariant.PRIMARY} onClick={onClose}>
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
                        <div>{splitDetails?.locationTrack?.name}</div>
                    </FieldLayout>
                    <FieldLayout label={t('split-details-dialog.source-oid')}>
                        <div>{splitDetails?.locationTrack?.externalId}</div>
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
                                                {target.newlyCreated
                                                    ? t('split-details-dialog.newly-created')
                                                    : // TODO: GVT-2525 partial duplicate
                                                      t('split-details-dialog.replaces-duplicate')}
                                            </td>
                                            <td>
                                                {target.startAddress
                                                    ? formatTrackMeter(target.startAddress)
                                                    : undefined}
                                            </td>
                                            <td>
                                                {target.endAddress
                                                    ? formatTrackMeter(target.endAddress)
                                                    : undefined}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </Table>
                        </div>
                    </FieldLayout>
                </div>
            </ProgressIndicatorWrapper>
        </Dialog>
    );
};
