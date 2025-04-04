import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader, PlanApplicability } from 'geometry/geometry-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { updatePlanApplicability } from 'infra-model/infra-model-api';
import { createClassName } from 'vayla-design-lib/utils';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { planApplicabilities } from 'utils/enum-localization-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

const PLAN_APPLICABIILITY_TRANSLATION_BASE = 'enum.PlanApplicability';
export const getPlanApplicabilityTranslationKey = (
    applicability: PlanApplicability | undefined,
) => {
    switch (applicability) {
        case 'MAINTENANCE':
        case 'PLANNING':
        case 'STATISTICS':
            return `${PLAN_APPLICABIILITY_TRANSLATION_BASE}.${applicability}`;
        case undefined:
            return `${PLAN_APPLICABIILITY_TRANSLATION_BASE}.UNKNOWN`;
        default:
            return exhaustiveMatchingGuard(applicability);
    }
};

type PlanApplicabilitySelectorProps = {
    planHeader: GeometryPlanHeader;
    endEditing: () => void;
};
export const PlanApplicabilitySelector: React.FC<PlanApplicabilitySelectorProps> = ({
    planHeader,
    endEditing,
}) => {
    const { t } = useTranslation();
    const [saving, setSaving] = React.useState(false);
    const [selectedApplicability, setSelectedApplicability] = React.useState(
        planHeader.planApplicability,
    );

    const save = () => {
        setSaving(true);
        updatePlanApplicability(planHeader.id, selectedApplicability).finally(() => {
            setSaving(false);
            endEditing();
        });
    };

    const buttonContainerClasses = createClassName(
        infoboxStyles['infobox__buttons'],
        infoboxStyles['infobox__buttons--right'],
    );

    return (
        <div>
            <Dropdown
                placeholder={t(getPlanApplicabilityTranslationKey(undefined))}
                options={planApplicabilities}
                canUnselect={true}
                unselectText={t(getPlanApplicabilityTranslationKey(undefined))}
                disabled={saving}
                value={selectedApplicability}
                onChange={(val) => setSelectedApplicability(val)}
            />
            <div className={buttonContainerClasses}>
                <Button
                    size={ButtonSize.SMALL}
                    variant={ButtonVariant.SECONDARY}
                    onClick={() => endEditing()}>
                    {t('button.cancel')}
                </Button>
                <Button
                    onClick={save}
                    variant={ButtonVariant.PRIMARY}
                    size={ButtonSize.SMALL}
                    disabled={saving}
                    isProcessing={saving}>
                    {t('button.save')}
                </Button>
            </div>
        </div>
    );
};
