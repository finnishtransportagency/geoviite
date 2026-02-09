import React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { formatDateFull, formatDateShort } from 'utils/date-utils';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import DecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_GEOMETRY_FILE } from 'user/user-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { GeometryPlanLinkingSummary } from 'geometry/geometry-api';
import { InfraModelDownloadButton } from 'geoviite-design-lib/infra-model-download/infra-model-download-button';

type InfraModelSearchResultRowProps = {
    plan: GeometryPlanHeader;
    linkingSummary: GeometryPlanLinkingSummary | undefined;
    setConfirmHidePlan: (planId: GeometryPlanHeader | undefined) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
};

const InfraModelSearchResultRowM: React.FC<InfraModelSearchResultRowProps> = ({
    plan,
    linkingSummary,
    setConfirmHidePlan,
    onSelectPlan,
}) => {
    const { t } = useTranslation();

    const downloadButtonRef = React.useRef<HTMLButtonElement>(null);
    const hideButtonRef = React.useRef<HTMLButtonElement>(null);

    const isCurrentlyLinked = linkingSummary?.currentlyLinked;

    const targetWithinButton = (target: EventTarget) =>
        downloadButtonRef.current?.contains(target as HTMLElement) ||
        hideButtonRef.current?.contains(target as HTMLElement);

    const linkedAtTitle = linkingSummary?.linkedAt
        ? t('im-form.linking-details', {
              linkedAt: formatDateFull(linkingSummary?.linkedAt),
              linkedByUsers: linkingSummary?.linkedByUsers?.join('\n'),
          })
        : undefined;

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
                {plan.source === 'PAIKANNUSPALVELU' && (
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
            <td title={plan.uploadTime ? formatDateFull(plan.uploadTime) : undefined}>
                {plan.uploadTime && formatDateShort(plan.uploadTime)}
            </td>
            <td title={linkedAtTitle}>
                {linkingSummary?.linkedAt === undefined
                    ? ''
                    : formatDateShort(linkingSummary.linkedAt)}
            </td>
            <td>
                <InfraModelDownloadButton
                    planHeader={plan}
                    title={t('im-form.download-file')}
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    ref={downloadButtonRef}
                />
            </td>
            <td>
                <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                    <Button
                        title={
                            isCurrentlyLinked
                                ? t('im-form.cannot-hide-file')
                                : t('im-form.hide-file')
                        }
                        onClick={() => setConfirmHidePlan(plan)}
                        disabled={isCurrentlyLinked ?? true}
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

export const InfraModelSearchResultRow: React.FC<InfraModelSearchResultRowProps> = React.memo(
    InfraModelSearchResultRowM,
);
