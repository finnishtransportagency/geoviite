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
import { TrackMeter } from 'common/common-model';
import {
    formatTrackDescription,
    LayoutSwitch,
    SplitPoint,
} from 'track-layout/track-layout-model';
import { formatTrackMeter } from 'utils/geography-utils';

type ConfirmSplitDialogProps = {
    sourceTrackName: string;
    allSplits: (FirstSplitTargetCandidate | SplitTargetCandidate)[];
    switches: LayoutSwitch[];
    endSplitPoint: SplitPoint;
    onConfirm: () => void;
    onCancel: () => void;
};

export const ConfirmSplitDialog: React.FC<ConfirmSplitDialogProps> = ({
    sourceTrackName,
    allSplits,
    switches,
    endSplitPoint,
    onConfirm,
    onCancel,
}) => {
    const { t } = useTranslation();

    function getSwitchNameParts(splitPoint: SplitPoint) {
        if (splitPoint.type === 'SWITCH_SPLIT_POINT') {
            return switches.find((s) => s.id === splitPoint.switchId)?.nameParts;
        }
        return undefined;
    }

    function getDescription(
        target: FirstSplitTargetCandidate | SplitTargetCandidate,
        index: number,
    ): string {
        if (target.suffixMode === 'NONE') return target.descriptionBase;
        const startSwitchNameParts = getSwitchNameParts(target.splitPoint);
        const endSplitPointForTarget = allSplits[index + 1]?.splitPoint ?? endSplitPoint;
        const endSwitchNameParts = getSwitchNameParts(endSplitPointForTarget);
        return formatTrackDescription(
            target.descriptionBase,
            target.suffixMode,
            startSwitchNameParts,
            endSwitchNameParts,
            t,
        );
    }

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
                                    <Th>{t('tool-panel.location-track.splitting.full-description')}</Th>
                                    <Th>{t('split-details-dialog.operation')}</Th>
                                    <Th>{t('split-details-dialog.start-address')}</Th>
                                    <Th>{t('split-details-dialog.end-address')}</Th>
                                </tr>
                            </thead>
                            <tbody>
                                {allSplits.map((target, index) => (
                                    <tr key={target.id}>
                                        <td>{target.name}</td>
                                        <td>{getDescription(target, index)}</td>
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
