import React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader, PlanApplicability } from 'geometry/geometry-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { updatePlanApplicability } from 'infra-model/infra-model-api';
import { createClassName } from 'vayla-design-lib/utils';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { planApplicabilities } from 'utils/enum-localization-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from 'tool-panel/geometry-plan-infobox.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';

const getTranslationKey = (applicability: PlanApplicability | undefined) => {
    let key = 'UNKNOWN';
    switch (applicability) {
        case 'MAINTENANCE':
        case 'PLANNING':
        case 'STATISTICS':
            key = applicability;
            break;
        case undefined:
            key = 'UNKNOWN';
            break;
        default:
            return exhaustiveMatchingGuard(applicability);
    }

    return `enum.PlanApplicability.${key}`;
};

type GeometryPlanApplicabilityProps = {
    planHeader: GeometryPlanHeader;
};
export const GeometryPlanApplicability: React.FC<GeometryPlanApplicabilityProps> = ({
    planHeader,
}) => {
    const { t } = useTranslation();
    const [editing, setEditing] = React.useState(false);
    const [applicability, setApplicability] = React.useState<PlanApplicability | undefined>(
        planHeader.planApplicability,
    );
    const [saving, setSaving] = React.useState(false);
    const shave = () => {
        setSaving(true);
        updatePlanApplicability(planHeader.id, applicability).finally(() => {
            setSaving(false);
            setEditing(false);
        });
    };

    const buttonContainerClasses = createClassName(
        infoboxStyles['infobox__buttons'],
        infoboxStyles['infobox__buttons--right'],
    );

    return (
        <InfoboxField
            label={t('tool-panel.geometry-plan.applicability')}
            value={
                <React.Fragment>
                    {editing ? (
                        <div>
                            <Dropdown
                                placeholder={t(getTranslationKey(undefined))}
                                options={planApplicabilities}
                                canUnselect={true}
                                unselectText={t(getTranslationKey(undefined))}
                                disabled={saving}
                                value={applicability}
                                onChange={(val) => setApplicability(val)}
                            />
                            <div className={buttonContainerClasses}>
                                <Button size={ButtonSize.SMALL} variant={ButtonVariant.SECONDARY}>
                                    {t('button.cancel')}
                                </Button>
                                <Button
                                    onClick={shave}
                                    variant={ButtonVariant.PRIMARY}
                                    size={ButtonSize.SMALL}
                                    disabled={saving}
                                    isProcessing={saving}>
                                    {t('button.save')}
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <span className={styles['geometry-plan-tool-panel__applicability-value']}>
                            <span>{t(getTranslationKey(applicability))}</span>
                            <Button
                                icon={Icons.Edit}
                                onClick={() => setEditing(true)}
                                size={ButtonSize.X_SMALL}
                                variant={ButtonVariant.GHOST}
                            />
                        </span>
                    )}
                </React.Fragment>
            }
        />
    );
};
