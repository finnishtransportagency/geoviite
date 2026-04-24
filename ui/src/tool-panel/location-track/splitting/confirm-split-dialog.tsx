import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
import { FieldLayout, FieldLayoutVariant } from 'vayla-design-lib/field-layout/field-layout';
import { Table, TableVariant, Th } from 'vayla-design-lib/table/table';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import splitDetailsStyles from 'publication/split/split-details-dialog.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import {
    FirstSplitTargetCandidate,
    SplitTargetCandidate,
} from 'tool-panel/location-track/split-store';
import { Oid, TrackMeter } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { LocationTrackId, SplitPoint } from 'track-layout/track-layout-model';
import { formatTrackMeter } from 'utils/geography-utils';
import { getLocationTrackOids } from 'track-layout/layout-location-track-api';
import { useLoader } from 'utils/react-utils';
import { filterNotEmpty } from 'utils/array-utils';

type ConfirmSplitDialogProps = {
    sourceTrackName: string;
    sourceTrackId: LocationTrackId;
    changeTimes: ChangeTimes;
    allSplits: (FirstSplitTargetCandidate | SplitTargetCandidate)[];
    endSplitPoint: SplitPoint;
    onConfirm: () => void;
    onCancel: () => void;
};

export const ConfirmSplitDialog: React.FC<ConfirmSplitDialogProps> = ({
    sourceTrackName,
    sourceTrackId,
    changeTimes,
    allSplits,
    endSplitPoint,
    onConfirm,
    onCancel,
}) => {
    const { t } = useTranslation();
    const oidPlaceholder = t('tool-panel.location-track.splitting.oid-set-on-publish');

    const sourceOid = useLoader(
        () => getLocationTrackOids(sourceTrackId, changeTimes.layoutLocationTrackExtId),
        [sourceTrackId, changeTimes.layoutLocationTrackExtId],
    );

    const duplicateTrackIds = allSplits
        .filter((s) => s.duplicateTrackId && s.operation !== 'CREATE')
        .map((s) => s.duplicateTrackId)
        .filter(filterNotEmpty);

    const duplicateOids = useLoader(
        () =>
            Promise.all(
                duplicateTrackIds.map((id) =>
                    getLocationTrackOids(id, changeTimes.layoutLocationTrackExtId).then(
                        (oids) => [id, oids['MAIN']] as [LocationTrackId, Oid | undefined],
                    ),
                ),
            ).then((entries) => new Map(entries)),
        [duplicateTrackIds.join(','), changeTimes.layoutLocationTrackExtId],
    );

    const getTargetOid = (target: FirstSplitTargetCandidate | SplitTargetCandidate): string => {
        if (target.duplicateTrackId && target.operation !== 'CREATE') {
            return duplicateOids?.get(target.duplicateTrackId) ?? '';
        }
        return oidPlaceholder;
    };

    return (
        <Dialog
            qaId={'confirm-split-dialog'}
            title={t('tool-panel.location-track.splitting.confirm-split-title')}
            allowClose={false}
            variant={DialogVariant.DARK}
            width={DialogWidth.THREE_COLUMNS}
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
                <FieldLayout label={t('split-details-dialog.source-name')} variant={FieldLayoutVariant.DARK}>
                    <div>{sourceTrackName}</div>
                </FieldLayout>
                <FieldLayout label={t('split-details-dialog.source-oid')} variant={FieldLayoutVariant.DARK}>
                    <div>{sourceOid?.['MAIN'] ?? ''}</div>
                </FieldLayout>
                <FieldLayout
                    variant={FieldLayoutVariant.DARK}
                    label={t('tool-panel.location-track.splitting.confirm-split-targets', {
                        trackCount: allSplits.length,
                    })}>
                    <div className={splitDetailsStyles['split-details-dialog__table']}>
                        <Table wide variant={TableVariant.DARK}>
                            <thead
                                className={
                                    splitDetailsStyles['split-details-dialog__table-header']
                                }>
                                <tr>
                                    <Th>{t('split-details-dialog.target-name')}</Th>
                                    <Th>{t('split-details-dialog.target-oid')}</Th>
                                    <Th>{t('split-details-dialog.operation')}</Th>
                                    <Th>{t('split-details-dialog.start-address')}</Th>
                                    <Th>{t('split-details-dialog.end-address')}</Th>
                                </tr>
                            </thead>
                            <tbody>
                                {allSplits.map((target, index) => (
                                    <tr key={target.id}>
                                        <td>{target.name}</td>
                                        <td>{getTargetOid(target)}</td>
                                        <td>
                                            {t(`tool-panel.location-track.splitting.operation.${target.operation}`)}
                                        </td>
                                        <td>
                                            {formatAddress(target.splitPoint.address)}
                                        </td>
                                        <td>
                                            {formatAddress(
                                                (allSplits[index + 1]?.splitPoint ??
                                                    endSplitPoint
                                                ).address,
                                            )}
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

function formatAddress(address: TrackMeter | undefined): string | undefined {
    return address ? formatTrackMeter(address) : undefined;
}
