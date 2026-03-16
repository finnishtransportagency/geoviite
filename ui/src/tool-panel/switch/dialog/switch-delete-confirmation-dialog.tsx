import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import * as React from 'react';
import { Trans, useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { LoaderStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';

type SwitchDeleteConfirmationDialogProps = {
    linkedLocationTracks: LayoutLocationTrack[];
    linkedTracksLoaderStatus: LoaderStatus;
    onConfirm: (deleteSwitchLinking: boolean) => void;
    onClose: () => void;
    isSaving: boolean;
};

const SwitchDeleteConfirmationDialog: React.FC<SwitchDeleteConfirmationDialogProps> = ({
    linkedLocationTracks,
    linkedTracksLoaderStatus,
    onConfirm,
    onClose,
    isSaving,
}) => {
    const { t } = useTranslation();

    const [deleteSwitchLinking, setDeleteSwitchLinking] = React.useState(true);

    return (
        <Dialog
            title={t('switch-dialog.confirmation-delete-title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY} disabled={isSaving}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        disabled={isSaving || linkedTracksLoaderStatus !== LoaderStatus.Ready}
                        isProcessing={isSaving}
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={() => onConfirm(deleteSwitchLinking)}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            {linkedTracksLoaderStatus !== LoaderStatus.Ready && <Spinner />}
            {linkedTracksLoaderStatus === LoaderStatus.Ready && linkedLocationTracks.length > 0 ? (
                <React.Fragment>
                    <p>{t('switch-dialog.deleted-state-warning')}</p>
                    <div>
                        <div className={'dialog__text'}>
                            <p>
                                <Trans i18nKey={'switch-dialog.switch-links-list'} />
                            </p>
                            <ul>
                                {linkedLocationTracks.map((lt) => (
                                    <li key={lt.id}>{lt.name}</li>
                                ))}
                            </ul>
                            <p>
                                <Trans i18nKey={'switch-dialog.switch-links-options'} />
                            </p>
                        </div>
                    </div>
                    <Checkbox
                        checked={deleteSwitchLinking}
                        onChange={(e) => setDeleteSwitchLinking(e.target.checked)}>
                        {t('switch-dialog.delete-switch-links')}
                    </Checkbox>
                </React.Fragment>
            ) : (
                <div>
                    <p>{t('switch-dialog.deleted-state-warning')}</p>
                    <div className={'dialog__text'}>{t('switch-dialog.confirm-switch-delete')}</div>
                </div>
            )}
        </Dialog>
    );
};

export default SwitchDeleteConfirmationDialog;
