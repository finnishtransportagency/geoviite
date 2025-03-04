import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
import { DownloadablePlan } from 'map/plan-download/plan-download-slice';

type PlanItemProps = {
    id: GeometryPlanId;
    name: string;
    checked: boolean;
    applicability: PlanApplicability | undefined;
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
};

const PlanItem: React.FC<PlanItemProps> = ({
    id,
    checked,
    name,
    applicability,
    setPlanSelected,
}) => {
    return (
        <li className={styles['plan-download-popup__plan-row']}>
            <Checkbox checked={checked} onChange={() => setPlanSelected(id, !checked)} />
            <span className={styles['plan-download-popup__plan-name']}>{name}</span>
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
};

type PlanDownloadPlanSectionProps = {
    plans: DownloadablePlan[];
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
};

export const PlanDownloadPlanSection: React.FC<PlanDownloadPlanSectionProps> = ({
    plans,
    setPlanSelected,
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
                    />
                ))}
            </ul>
            <div className={styles['plan-download-popup__buttons']}>
                <Button size={ButtonSize.SMALL} variant={ButtonVariant.SECONDARY}>
                    {t('plan-download.select-all')}
                </Button>
                <Button size={ButtonSize.SMALL}>
                    {t('plan-download.download-selected', { amount: plans.length })}
                </Button>
            </div>
        </div>
    );
};
