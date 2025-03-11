import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanId, highestApplicability, PlanApplicability } from 'geometry/geometry-model';
import { DownloadablePlan } from 'map/plan-download/plan-download-store';
import { inframodelBatchDownloadUri } from 'infra-model/infra-model-api';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';

type PlanItemProps = {
    id: GeometryPlanId;
    name: string;
    checked: boolean;
    applicability: PlanApplicability | undefined;
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
    selectPlan: (planId: GeometryPlanId) => void;
    disabled: boolean;
};

const PlanItem: React.FC<PlanItemProps> = ({
    id,
    checked,
    name,
    applicability,
    setPlanSelected,
    selectPlan,
    disabled,
}) => {
    const classNames = createClassName(
        styles['plan-download-popup__plan-row'],
        disabled && styles['plan-download-popup__plan-row--disabled'],
    );
    return (
        <li className={classNames}>
            <Checkbox
                checked={checked}
                onChange={() => setPlanSelected(id, !checked)}
                disabled={disabled}
            />
            <span
                className={styles['plan-download-popup__plan-name']}
                onClick={() => !disabled && selectPlan(id)}>
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
};

type PlanDownloadPlanSectionProps = {
    disabled: boolean;
    plans: DownloadablePlan[];
    trackNumberId: LayoutTrackNumberId | undefined;
    locationTrackId: LocationTrackId | undefined;
    startKm: KmNumber | undefined;
    endKm: KmNumber | undefined;
    selectedApplicabilities: PlanApplicability[];
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
    setAllPlansSelected: (selected: boolean) => void;
    selectPlan: (planId: GeometryPlanId) => void;
};

export const PlanDownloadPlanSection: React.FC<PlanDownloadPlanSectionProps> = ({
    disabled,
    plans,
    trackNumberId,
    locationTrackId,
    startKm,
    endKm,
    selectedApplicabilities,
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
                        disabled={disabled}
                    />
                ))}
            </ul>
            <div className={styles['plan-download-popup__buttons']}>
                <Button
                    disabled={disabled || plans.length === 0}
                    size={ButtonSize.SMALL}
                    variant={ButtonVariant.SECONDARY}
                    onClick={() => setAllPlansSelected(true)}>
                    {t('plan-download.select-all')}
                </Button>
                <Button
                    size={ButtonSize.SMALL}
                    disabled={disabled || plans.every((p) => !p.selected)}
                    onClick={() => {
                        location.href = inframodelBatchDownloadUri(
                            plans.filter((p) => p.selected).map((plan) => plan.id),
                            highestApplicability(selectedApplicabilities),
                            trackNumberId,
                            locationTrackId,
                            startKm || undefined,
                            endKm || undefined,
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
