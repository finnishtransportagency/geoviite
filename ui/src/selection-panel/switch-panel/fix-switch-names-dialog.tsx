import * as React from 'react';
import { Dialog, DialogContentPadding, DialogContentSpread, } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { fixSwitchNames, SwitchNameFixPreview } from 'track-layout/layout-switch-api';
import styles from './fix-switch-names-dialog.module.scss';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LayoutBranch } from 'common/common-model';

type FixSwitchNamesDialogProps = {
    isOpen: boolean;
    onClose: () => void;
    previews: SwitchNameFixPreview[];
    layoutBranch: LayoutBranch;
};

function SpaceCharacter() {
    return <span className={styles['fix-switch-names-dialog__space-char']}>&nbsp;</span>;
}

function visualizeSwitchNameCharacters(name: string): React.ReactElement[] {
    return name.split('').map((char) => {
        switch (char) {
            case ' ':
                return <SpaceCharacter />;
            default:
                return <React.Fragment key={char}>{char}</React.Fragment>;
        }
    });
}

export const FixSwitchNamesDialog: React.FC<FixSwitchNamesDialogProps> = ({
    isOpen,
    onClose,
    previews,
    layoutBranch,
}) => {
    const { t } = useTranslation();
    const [isFixingNames, setIsFixingNames] = React.useState(false);

    const handleConfirm = async () => {
        setIsFixingNames(true);
        try {
            const switchIds = previews.map((p) => p.switchId);
            await fixSwitchNames(layoutBranch, switchIds);
            Snackbar.success(t('fix-switch-names.success-message', { count: switchIds.length }));
            onClose();
        } finally {
            setIsFixingNames(false);
        }
    };

    if (!isOpen) {
        return null;
    }

    return (
        <Dialog
            title={t('fix-switch-names.dialog-title')}
            onClose={onClose}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        onClick={onClose}
                        disabled={isFixingNames}>
                        {t('fix-switch-names.cancel-button')}
                    </Button>
                    <Button
                        onClick={handleConfirm}
                        icon={Icons.Tick}
                        disabled={isFixingNames}
                        isProcessing={isFixingNames}>
                        {t('fix-switch-names.confirm-button')}
                    </Button>
                </div>
            }>
            <div className={styles['fix-switch-names-dialog']}>
                <p className={styles['fix-switch-names-dialog__message']}>
                    {t('fix-switch-names.count-message', { count: previews.length })}
                </p>
                <DialogContentSpread>
                    <div className={styles['fix-switch-names-dialog__list-container']}>
                        <DialogContentPadding>
                            <div className={styles['fix-switch-names-dialog__list']}>
                                {previews.map((preview) => (
                                    <React.Fragment key={preview.switchId}>
                                        <span>
                                            {visualizeSwitchNameCharacters(preview.currentName)}
                                        </span>
                                        <span className={styles['fix-switch-names-dialog__arrow']}>
                                            <Icons.Next
                                                color={IconColor.INHERIT}
                                                size={IconSize.SMALL}
                                            />
                                        </span>
                                        <span>
                                            {visualizeSwitchNameCharacters(preview.fixedName)}
                                        </span>
                                    </React.Fragment>
                                ))}
                            </div>
                        </DialogContentPadding>
                    </div>
                </DialogContentSpread>
            </div>
        </Dialog>
    );
};
