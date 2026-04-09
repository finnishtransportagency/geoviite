import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import styles from './operational-point-delete-confirmation-dialog.scss';
import { LoaderStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { LayoutLocationTrack, LayoutSwitch } from 'track-layout/track-layout-model';

type LinkedAssetsContentProps = {
    linkedLocationTracks: LayoutLocationTrack[];
    linkedSwitches: LayoutSwitch[];
    severLinks: boolean;
    onSeverLinksChange: (severLinks: boolean) => void;
};

const LinkedAssetsContent: React.FC<LinkedAssetsContentProps> = ({
    linkedLocationTracks,
    linkedSwitches,
    severLinks,
    onSeverLinksChange,
}) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            <p>{t('operational-point-dialog.delete-confirm-dialog.deleted-op-not-allowed')}</p>
            <div className={'dialog__text'}>
                <p>{t('operational-point-dialog.delete-confirm-dialog.linked-assets-warning')}</p>
                <div className={styles['op-delete-confirmation-dialog__linked-assets-list']}>
                    {linkedSwitches.length > 0 && (
                        <React.Fragment>
                            <strong>
                                {t(
                                    'operational-point-dialog.delete-confirm-dialog.linked-switches-heading',
                                )}
                            </strong>
                            <ul>
                                {linkedSwitches.map((sw) => (
                                    <li key={sw.id}>{sw.name}</li>
                                ))}
                            </ul>
                        </React.Fragment>
                    )}
                    {linkedLocationTracks.length > 0 && (
                        <React.Fragment>
                            <strong>
                                {t(
                                    'operational-point-dialog.delete-confirm-dialog.linked-location-tracks-heading',
                                )}
                            </strong>
                            <ul>
                                {linkedLocationTracks.map((lt) => (
                                    <li key={lt.id}>{lt.name}</li>
                                ))}
                            </ul>
                        </React.Fragment>
                    )}
                </div>
                <p>{t('operational-point-dialog.delete-confirm-dialog.sever-links-explanation')}</p>
            </div>
            <Checkbox checked={severLinks} onChange={(e) => onSeverLinksChange(e.target.checked)}>
                {t('operational-point-dialog.delete-confirm-dialog.sever-links-checkbox')}
            </Checkbox>
        </React.Fragment>
    );
};

const NoLinkedAssetsContent: React.FC = () => {
    const { t } = useTranslation();

    return (
        <div>
            <p>{t('operational-point-dialog.delete-confirm-dialog.deleted-op-not-allowed')}</p>
            <div className={'dialog__text'}>
                {t('operational-point-dialog.delete-confirm-dialog.confirm')}
            </div>
        </div>
    );
};

type OperationalPointDeleteConfirmationDialogProps = {
    linkedLocationTracks: LayoutLocationTrack[];
    linkedSwitches: LayoutSwitch[];
    linkedAssetsLoaderStatus: LoaderStatus;
    onConfirm: (severLinks: boolean) => void;
    onClose: () => void;
    isSaving: boolean;
};

export const OperationalPointDeleteConfirmationDialog: React.FC<
    OperationalPointDeleteConfirmationDialogProps
> = ({
    linkedLocationTracks,
    linkedSwitches,
    linkedAssetsLoaderStatus,
    onConfirm,
    onClose,
    isSaving,
}) => {
    const { t } = useTranslation();

    const [severLinks, setSeverLinks] = React.useState(true);

    const linkedAssetsLoaded = linkedAssetsLoaderStatus === LoaderStatus.Ready;
    const hasLinkedAssets = linkedLocationTracks.length > 0 || linkedSwitches.length > 0;

    return (
        <Dialog
            title={t('operational-point-dialog.delete-confirm-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY} disabled={isSaving}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        disabled={isSaving || !linkedAssetsLoaded}
                        isProcessing={isSaving}
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={() => onConfirm(severLinks)}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            {linkedAssetsLoaded ? (
                hasLinkedAssets ? (
                    <LinkedAssetsContent
                        linkedLocationTracks={linkedLocationTracks}
                        linkedSwitches={linkedSwitches}
                        severLinks={severLinks}
                        onSeverLinksChange={setSeverLinks}
                    />
                ) : (
                    <NoLinkedAssetsContent />
                )
            ) : (
                <Spinner />
            )}
        </Dialog>
    );
};
