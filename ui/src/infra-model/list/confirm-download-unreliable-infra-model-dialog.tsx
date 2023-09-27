import * as React from 'react';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';

type ConfirmDownloadUnreliableInfraModelDialogProps = {
    onClose: () => void;
    plan: GeometryPlanHeader;
};

export const ConfirmDownloadUnreliableInfraModelDialog: React.FC<
    ConfirmDownloadUnreliableInfraModelDialogProps
> = ({ onClose, plan }: ConfirmDownloadUnreliableInfraModelDialogProps) => {
    const { t } = useTranslation();
    return (
        <Dialog
            title={t(`infra-model-download.unreliable-plan`)}
            onClose={onClose}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} icon={Icons.Delete} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            location.href = inframodelDownloadUri(plan.id);
                            onClose();
                        }}
                        icon={Icons.Download}>
                        {t('infra-model-download.download')}
                    </Button>
                </div>
            }>
            <p>
                {t(`infra-model-download.warning-content`, {
                    planFileName: plan.fileName,
                })}
            </p>
        </Dialog>
    );
};
