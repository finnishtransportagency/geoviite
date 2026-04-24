import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Table, Th } from 'vayla-design-lib/table/table';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import splitDetailsStyles from 'publication/split/split-details-dialog.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import {
    FirstSplitTargetCandidate,
    SplitTargetCandidate,
} from 'tool-panel/location-track/split-store';

type ConfirmSplitDialogProps = {
    sourceTrackName: string;
    allSplits: (FirstSplitTargetCandidate | SplitTargetCandidate)[];
    onConfirm: () => void;
    onCancel: () => void;
};

export const ConfirmSplitDialog: React.FC<ConfirmSplitDialogProps> = ({
    sourceTrackName,
    allSplits,
    onConfirm,
    onCancel,
}) => {
    const { t } = useTranslation();

    return (
        <Dialog
            qaId={'confirm-split-dialog'}
            title={t('tool-panel.location-track.splitting.confirm-split-title')}
            allowClose={false}
            variant={DialogVariant.DARK}
            width={DialogWidth.TWO_COLUMNS}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onCancel} variant={ButtonVariant.WARNING}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        variant={ButtonVariant.PRIMARY}
                        onClick={() => {
                            onConfirm();
                            onCancel();
                        }}
                        qa-id={'confirm-split-dialog-execute'}>
                        {t('tool-panel.location-track.splitting.confirm-split')}
                    </Button>
                </div>
            }>
            <div>
                <FieldLayout label={t('split-details-dialog.source-name')}>
                    <div>{sourceTrackName}</div>
                </FieldLayout>
                <FieldLayout
                    label={t('tool-panel.location-track.splitting.confirm-split-targets', {
                        trackCount: allSplits.length,
                    })}>
                    <div className={splitDetailsStyles['split-details-dialog__table']}>
                        <Table wide>
                            <thead
                                className={
                                    splitDetailsStyles['split-details-dialog__table-header']
                                }>
                                <tr>
                                    <Th>{t('split-details-dialog.target-name')}</Th>
                                    <Th>{t('split-details-dialog.operation')}</Th>
                                </tr>
                            </thead>
                            <tbody>
                                {allSplits.map((target) => (
                                    <tr key={target.id}>
                                        <td>{target.name}</td>
                                        <td>
                                            {t(`enum.SplitTargetOperation.${target.operation}`)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </Table>
                    </div>
                </FieldLayout>
            </div>
        </Dialog>
    );
};
