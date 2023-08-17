import * as React from 'react';
import { useEffect, useState } from 'react';
import { InfraModelListState } from 'infra-model/list/infra-model-list-store';
import './infra-model-list.module.scss';
import { useTranslation } from 'react-i18next';
import { formatDateFull, formatDateShort } from 'utils/date-utils';

import {
    GeometryPlanHeader,
    GeometryPlanId,
    GeometryPlanSearchParams,
    SortByValue,
    SortOrderValue,
} from 'geometry/geometry-model';
import { IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Table, Th } from 'vayla-design-lib/table/table';
import DecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import { GeometryPlanLinkingSummary, getGeometryPlanLinkingSummaries } from 'geometry/geometry-api';
import { UnreliableInfraModelDownloadConfirmDialog } from 'infra-model/list/unreliable-infra-model-download-confirm-dialog';

export type InfraModelSearchResultProps = Pick<
    InfraModelListState,
    'searchState' | 'searchParams' | 'plans' | 'page' | 'pageSize' | 'totalCount'
> & {
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
    onNextPage: () => void;
    onPrevPage: () => void;
};

function setSortOrder(
    currentSortByValue: SortByValue,
    sortByValue: SortByValue,
    sortOrderValue: SortOrderValue,
): SortOrderValue {
    if (sortByValue === currentSortByValue) {
        return sortOrderValue === SortOrderValue.ASCENDING
            ? SortOrderValue.DESCENDING
            : SortOrderValue.ASCENDING;
    } else {
        return SortOrderValue.ASCENDING;
    }
}

export const InfraModelSearchResult: React.FC<InfraModelSearchResultProps> = (
    props: InfraModelSearchResultProps,
) => {
    const trackNumbers = useTrackNumbers('DRAFT');

    const [linkingSummaries, setLinkingSummaries] = useState<
        Map<GeometryPlanId, GeometryPlanLinkingSummary | null>
    >(() => new Map());
    const [downloadConfirmPlan, setDownloadConfirmPlan] = React.useState<GeometryPlanHeader>();

    useEffect(() => {
        const newPlans: GeometryPlanId[] = [];
        for (const plan of props.plans) {
            if (!linkingSummaries.has(plan.id)) {
                newPlans.push(plan.id);
            }
        }
        if (newPlans.length === 0) {
            return;
        }
        getGeometryPlanLinkingSummaries(newPlans).then((plansAndSummaries) => {
            if (plansAndSummaries == null) {
                return;
            }
            setLinkingSummaries((currentLinkingSummaries) => {
                const newLinkingSummaries = new Map(currentLinkingSummaries);
                for (const [planId, summary] of Object.entries(plansAndSummaries)) {
                    newLinkingSummaries.set(planId, summary);
                }
                return newLinkingSummaries;
            });
        });
    }, [props.plans]);

    function setFilter(sortByValue: SortByValue) {
        props.onSearchParamsChange({
            ...props.searchParams,
            sortBy: sortByValue,
            sortOrder: setSortOrder(
                props.searchParams.sortBy,
                sortByValue,
                props.searchParams.sortOrder,
            ),
        });
    }

    function getSortingIcon(): IconComponent {
        return props.searchParams.sortOrder == 0 ? Icons.Ascending : Icons.Descending;
    }

    function linkingSummaryDate(planId: GeometryPlanId) {
        const linkingSummary = linkingSummaries.get(planId);
        return linkingSummary == null ? '' : formatDateFull(linkingSummary.linkedAt);
    }

    const linkingSummaryUsers = (planId: GeometryPlanId) =>
        linkingSummaries.get(planId)?.linkedByUsers ?? '';

    const { t } = useTranslation();
    const firstItem = props.page * props.pageSize + (props.plans.length > 0 ? 1 : 0);
    const lastItem = props.page * props.pageSize + props.plans.length;
    return (
        <div className="infra-model-list-search-result">
            <div className="infra-model-list-search-result__status-row">
                <div className="infra-model-list-search-result__count">
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        size={ButtonSize.SMALL}
                        disabled={props.page <= 0}
                        onClick={props.onPrevPage}>
                        {'<'}
                    </Button>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        size={ButtonSize.SMALL}
                        disabled={(props.page + 1) * props.pageSize >= props.totalCount}
                        onClick={props.onNextPage}>
                        {'>'}
                    </Button>
                    {`Tiedostot ${firstItem}-${lastItem} (yht. ${props.totalCount})`}
                </div>
            </div>
            <div className="infra-model-list-search-result__table">
                <Table wide>
                    <thead>
                        <tr>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.PROJECT_NAME
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.PROJECT_NAME)}
                                qa-id="im-form.name-header">
                                {t('im-form.name-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.FILE_NAME
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.FILE_NAME)}
                                qa-id="im-form.file-name-header">
                                {t('im-form.file-name')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.TRACK_NUMBER
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.TRACK_NUMBER)}
                                qa-id="im-form.track-number-header">
                                {t('im-form.tracknumberfield')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.KM_START
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.KM_START)}
                                qa-id="im-form.km-start-header">
                                {t('im-form.km-start-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.KM_END
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.KM_END)}
                                qa-id="im-form.km-end-header">
                                {t('im-form.km-end-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.PLAN_PHASE
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.PLAN_PHASE)}
                                qa-id="im-form.plan-phase-header">
                                {t('im-form.plan-phase-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.DECISION_PHASE
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.DECISION_PHASE)}
                                qa-id="im-form.decision-phase-header">
                                {t('im-form.decision-phase-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.CREATED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.CREATED_AT)}
                                qa-id="im-form.created-at-header">
                                {t('im-form.plan-time-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.UPLOADED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.UPLOADED_AT)}
                                qa-id="im-form.uploaded-at-header">
                                {t('im-form.created-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.LINKED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.LINKED_AT)}
                                qa-id="im-form.linked-at-header">
                                {t('im-form.linked-at-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == SortByValue.LINKED_BY
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(SortByValue.LINKED_BY)}
                                qa-id="im-form.linked-by-header">
                                {t('im-form.linked-by-users-field')}
                            </Th>
                            <Th />
                            <Th />
                        </tr>
                    </thead>
                    <tbody id="infra-model-list-search-result__table-body">
                        {trackNumbers &&
                            props.plans.map((plan) => {
                                return (
                                    <tr
                                        key={plan.id}
                                        className="infra-model-list-search-result__plan"
                                        onClick={() => props.onSelectPlan(plan.id)}>
                                        <td>
                                            {plan.project.name}
                                            {plan.source == 'PAIKANNUSPALVELU' && (
                                                <div className="infra-model-list-search-result__plan-paikannuspalvelu">
                                                    {t('enum.plan-source.PAIKANNUSPALVELU')}
                                                </div>
                                            )}
                                        </td>
                                        <td>{plan.fileName}</td>
                                        <td>
                                            {
                                                trackNumbers.find(
                                                    (trackNumber) =>
                                                        trackNumber.id == plan.trackNumberId,
                                                )?.number
                                            }
                                        </td>
                                        <td>{plan.kmNumberRange?.min?.padStart(3, '0') || ''}</td>
                                        <td>{plan.kmNumberRange?.max?.padStart(3, '0') || ''}</td>
                                        <td>
                                            <PlanPhase phase={plan.planPhase} />
                                        </td>
                                        <td>
                                            <DecisionPhase decision={plan.decisionPhase} />
                                        </td>
                                        <td>{plan.planTime && formatDateShort(plan.planTime)}</td>
                                        <td>
                                            {plan.uploadTime && formatDateFull(plan.uploadTime)}
                                        </td>
                                        <td>{linkingSummaryDate(plan.id)}</td>
                                        <td>{linkingSummaryUsers(plan.id)}</td>
                                        <td onClick={(e) => e.stopPropagation()}>
                                            <Button
                                                title={t('im-form.download-file')}
                                                onClick={() => {
                                                    if (plan.source === 'PAIKANNUSPALVELU') {
                                                        setDownloadConfirmPlan(plan);
                                                    } else {
                                                        location.href = inframodelDownloadUri(
                                                            plan.id,
                                                        );
                                                    }
                                                }}
                                                variant={ButtonVariant.GHOST}
                                                size={ButtonSize.SMALL}
                                                icon={Icons.Download}
                                            />
                                        </td>
                                        <td>
                                            {plan.message ? (
                                                <span title={plan.message}>
                                                    <Icons.Info size={IconSize.SMALL} />
                                                </span>
                                            ) : null}
                                        </td>
                                    </tr>
                                );
                            })}
                    </tbody>
                </Table>
            </div>
            {downloadConfirmPlan && (
                <UnreliableInfraModelDownloadConfirmDialog
                    onClose={() => setDownloadConfirmPlan(undefined)}
                    plan={downloadConfirmPlan}
                />
            )}
        </div>
    );
};
