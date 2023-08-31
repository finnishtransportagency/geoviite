import * as React from 'react';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { hidePlan } from 'infra-model/infra-model-api';

type ConfirmHideInfraModelProps = {
    onClose: () => void;
    plan: GeometryPlanHeader;
};

export const ConfirmHideInfraModel: React.FC<ConfirmHideInfraModelProps> = ({
    onClose,
    plan,
}: ConfirmHideInfraModelProps) => {
    const { t } = useTranslation();
    return (
        <Dialog
            className={dialogStyles['dialog--wide']}
            title={t(`infra-model-hide.title`)}
            onClose={onClose}
            footerContent={
                <React.Fragment>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            hidePlan(plan.id).then((id) => id && onClose());
                        }}
                        icon={Icons.Delete}>
                        {t('infra-model-hide.hide')}
                    </Button>
                </React.Fragment>
            }>
            <p>
                {t(`infra-model-hide.content`, {
                    planFileName: plan.fileName,
                })}
            </p>
        </Dialog>
    );
};
