import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { GeometryPlanId, highestApplicability, PlanApplicability } from 'geometry/geometry-model';
import { DownloadablePlan } from 'map/plan-download/plan-download-store';
import { inframodelBatchDownloadUri } from 'infra-model/infra-model-api';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { KmNumber } from 'common/common-model';
import { PlanDownloadRow } from 'map/plan-download/plan-download-row';

type PlanDownloadPlanSectionProps = {
    disabled: boolean;
    plans: DownloadablePlan[];
    selectedPlanIds: GeometryPlanId[];
    trackNumberId: LayoutTrackNumberId | undefined;
    locationTrackId: LocationTrackId | undefined;
    startKm: KmNumber | undefined;
    endKm: KmNumber | undefined;
    selectedApplicabilities: PlanApplicability[];
    togglePlanForDownload: (planId: GeometryPlanId, selected: boolean) => void;
    selectPlansForDownload: (planIds: GeometryPlanId[]) => void;
    unselectAllPlans: () => void;
    selectPlanInToolPanel: (planId: GeometryPlanId) => void;
};

export const PlanDownloadPlanSection: React.FC<PlanDownloadPlanSectionProps> = ({
    disabled,
    plans,
    selectedPlanIds,
    trackNumberId,
    locationTrackId,
    startKm,
    endKm,
    selectedApplicabilities,
    togglePlanForDownload,
    selectPlansForDownload,
    unselectAllPlans,
    selectPlanInToolPanel,
}) => {
    const { t } = useTranslation();
    const selectedPlans = plans
        .filter((p) => selectedPlanIds.includes(p.id))
        .map((plan) => plan.id);

    return (
        <div>
            <ul className={styles['plan-download-popup__plans-container']}>
                {plans.map((plan) => (
                    <PlanDownloadRow
                        key={plan.id}
                        id={plan.id}
                        name={plan.name}
                        checked={selectedPlanIds.includes(plan.id)}
                        applicability={plan.applicability}
                        source={plan.source}
                        setPlanSelected={togglePlanForDownload}
                        selectPlanInToolPanel={selectPlanInToolPanel}
                        disabled={disabled}
                    />
                ))}
            </ul>
            <div className={styles['plan-download-popup__buttons']}>
                {plans.length > 0 && plans.every((p) => selectedPlanIds.includes(p.id)) ? (
                    <Button
                        disabled={disabled || plans.length === 0}
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        onClick={unselectAllPlans}>
                        {t('plan-download.unselect-all')}
                    </Button>
                ) : (
                    <Button
                        disabled={disabled || plans.length === 0}
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => selectPlansForDownload(plans.map((p) => p.id))}>
                        {t('plan-download.select-all')}
                    </Button>
                )}
                <Button
                    size={ButtonSize.SMALL}
                    disabled={disabled || plans.every((p) => !selectedPlanIds.includes(p.id))}
                    onClick={() => {
                        location.href = inframodelBatchDownloadUri(
                            selectedPlans,
                            highestApplicability(selectedApplicabilities),
                            trackNumberId,
                            locationTrackId,
                            startKm || undefined,
                            endKm || undefined,
                        );
                    }}>
                    {t('plan-download.download-selected', {
                        amount: selectedPlans.length,
                    })}
                </Button>
            </div>
        </div>
    );
};
