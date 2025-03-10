import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
import { DownloadablePlan } from 'map/plan-download/plan-download-store';
import { inframodelBatchDownloadUri } from 'infra-model/infra-model-api';

type PlanItemProps = {
    id: GeometryPlanId;
    name: string;
    checked: boolean;
    applicability: PlanApplicability | undefined;
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
    selectPlan: (planId: GeometryPlanId) => void;
};

const PlanItem: React.FC<PlanItemProps> = ({
    id,
    checked,
    name,
    applicability,
    setPlanSelected,
    selectPlan,
}) => (
    <li className={styles['plan-download-popup__plan-row']}>
        <Checkbox checked={checked} onChange={() => setPlanSelected(id, !checked)} />
        <span className={styles['plan-download-popup__plan-name']} onClick={() => selectPlan(id)}>
            {name}
        </span>
        <span className={styles['plan-download-popup__plan-icon']}>
            {!applicability && '?'}
            {applicability === 'STATISTICS' && (
                <Icons.BarsI size={IconSize.SMALL} color={IconColor.ORIGINAL} />
            )}
            {applicability === 'MAINTENANCE' && (
                <Icons.BarsII size={IconSize.SMALL} color={IconColor.ORIGINAL} />
            )}
            {applicability === 'PLANNING' && (
                <Icons.BarsIII size={IconSize.SMALL} color={IconColor.ORIGINAL} />
            )}
        </span>
    </li>
);

type PlanDownloadPlanSectionProps = {
    plans: DownloadablePlan[];
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
    setAllPlansSelected: (selected: boolean) => void;
    selectPlan: (planId: GeometryPlanId) => void;
};

export const PlanDownloadPlanSection: React.FC<PlanDownloadPlanSectionProps> = ({
    plans,
    setPlanSelected,
    setAllPlansSelected,
    selectPlan,
}) => {
    const { t } = useTranslation();

    return (
        <div>
            <ul className={styles['plan-download-popup__plans-container']}>
                {plans.map((plan) => (
                    <PlanItem
                        key={plan.id}
                        id={plan.id}
                        name={plan.name}
                        checked={plan.selected}
                        applicability={plan.applicability}
                        setPlanSelected={setPlanSelected}
                        selectPlan={selectPlan}
                    />
                ))}
            </ul>
            <div className={styles['plan-download-popup__buttons']}>
                <Button
                    size={ButtonSize.SMALL}
                    variant={ButtonVariant.SECONDARY}
                    onClick={() => setAllPlansSelected(true)}>
                    {t('plan-download.select-all')}
                </Button>
                <Button
                    size={ButtonSize.SMALL}
                    disabled={plans.every((p) => !p.selected)}
                    onClick={() => {
                        location.href = inframodelBatchDownloadUri(
                            plans.filter((p) => p.selected).map((plan) => plan.id),
                        );
                    }}>
                    {t('plan-download.download-selected', {
                        amount: plans.filter((p) => p.selected).length,
                    })}
                </Button>
            </div>
        </div>
    );
};
