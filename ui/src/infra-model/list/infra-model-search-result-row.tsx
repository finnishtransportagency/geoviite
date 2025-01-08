import React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { formatDateFull, formatDateShort } from 'utils/date-utils';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import DecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY, EDIT_GEOMETRY_FILE } from 'user/user-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanLinkingSummary } from 'geometry/geometry-api';

type InfraModelSearchResultRowProps = {
    plan: GeometryPlanHeader;
    linkingSummaries: Map<GeometryPlanId, GeometryPlanLinkingSummary | undefined>;
    setConfirmDownloadPlan: (planId: GeometryPlanHeader | undefined) => void;
    setConfirmHidePlan: (planId: GeometryPlanHeader | undefined) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
};

export const InfraModelSearchResultRow: React.FC<InfraModelSearchResultRowProps> = ({
    plan,
    linkingSummaries,
    setConfirmDownloadPlan,
    setConfirmHidePlan,
    onSelectPlan,
}) => {
    const { t } = useTranslation();

    const downloadButtonRef = React.useRef<HTMLButtonElement>(null);
    const hideButtonRef = React.useRef<HTMLButtonElement>(null);

    function linkingSummaryDate(planId: GeometryPlanId) {
        const linkingSummary = linkingSummaries.get(planId);
        return linkingSummary?.linkedAt == undefined ? '' : formatDateFull(linkingSummary.linkedAt);
    }

    const linkingSummaryUsers = (planId: GeometryPlanId) =>
        linkingSummaries.get(planId)?.linkedByUsers.join(', ') ?? '';

    const isCurrentlyLinked = (planId: GeometryPlanId) =>
        linkingSummaries.get(planId)?.currentlyLinked;

    const downloadPlan = (plan: GeometryPlanHeader) => {
        if (plan.source === 'PAIKANNUSPALVELU') {
            setConfirmDownloadPlan(plan);
        } else {
            location.href = inframodelDownloadUri(plan.id);
        }
    };

    const targetWithinButton = (target: EventTarget) =>
        downloadButtonRef.current?.contains(target as HTMLElement) ||
        hideButtonRef.current?.contains(target as HTMLElement);

    return (
        <tr
            key={plan.id}
            className="infra-model-list-search-result__plan"
            onClick={(e) => {
                if (!targetWithinButton(e.target)) {
                    onSelectPlan(plan.id);
                }
            }}>
            <td>{plan.name}</td>
            <td>
                {plan.project.name}
                {plan.source == 'PAIKANNUSPALVELU' && (
                    <div className="infra-model-list-search-result__plan-paikannuspalvelu">
                        {t(`enum.PlanSource.${plan.source}`)}
                    </div>
                )}
            </td>
            <td>{plan.fileName}</td>
            <td>{plan.trackNumber}</td>
            <td>{plan.kmNumberRange?.min?.padStart(3, '0') || ''}</td>
            <td>{plan.kmNumberRange?.max?.padStart(3, '0') || ''}</td>
            <td>
                <PlanPhase phase={plan.planPhase} />
            </td>
            <td>
                <DecisionPhase decision={plan.decisionPhase} />
            </td>
            <td>{plan.planTime && formatDateShort(plan.planTime)}</td>
            <td>{plan.uploadTime && formatDateFull(plan.uploadTime)}</td>
            <td>{linkingSummaryDate(plan.id)}</td>
            <td>{linkingSummaryUsers(plan.id)}</td>
            <td>
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <Button
                        title={t('im-form.download-file')}
                        onClick={() => downloadPlan(plan)}
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        icon={Icons.Download}
                        ref={downloadButtonRef}
                    />
                </PrivilegeRequired>
            </td>
            <td>
                <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                    <Button
                        title={
                            isCurrentlyLinked(plan.id)
                                ? t('im-form.cannot-hide-file')
                                : t('im-form.hide-file')
                        }
                        onClick={() => setConfirmHidePlan(plan)}
                        disabled={isCurrentlyLinked(plan.id) ?? true}
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        icon={Icons.Delete}
                        ref={hideButtonRef}
                    />
                </PrivilegeRequired>
            </td>
            <td>
                {plan.message ? (
                    <span title={plan.message}>
                        <Icons.Info size={IconSize.SMALL} />
                    </span>
                ) : (
                    <React.Fragment />
                )}
            </td>
        </tr>
    );
};
