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
    GeometrySortBy,
    GeometrySortOrder,
} from 'geometry/geometry-model';
import { IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Table, Th } from 'vayla-design-lib/table/table';
import DecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import { GeometryPlanLinkingSummary, getGeometryPlanLinkingSummaries } from 'geometry/geometry-api';
import { ConfirmHideInfraModel } from './confirm-hide-infra-model-dialog';
import { ConfirmDownloadUnreliableInfraModelDialog } from './confirm-download-unreliable-infra-model-dialog';
import { PrivilegeRequired } from 'user/privilege-required';
import {
    DOWNLOAD_GEOMETRY,
    EDIT_GEOMETRY_FILE,
    userHasPrivilege,
    VIEW_LAYOUT_DRAFT,
} from 'user/user-model';
import { useCommonDataAppSelector } from 'store/hooks';

export type InfraModelSearchResultProps = Pick<
    InfraModelListState,
    'searchState' | 'searchParams' | 'plans' | 'page' | 'pageSize' | 'totalCount'
> & {
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
    onSelectPlan: (planId: GeometryPlanId) => void;
    onNextPage: () => void;
    onPrevPage: () => void;
};

function toggleSortOrder(
    currentSortBy: GeometrySortBy,
    sortBy: GeometrySortBy,
    sortOrder: GeometrySortOrder | undefined,
): { sortOrder: GeometrySortOrder | undefined; sortBy: GeometrySortBy } {
    if (sortBy === currentSortBy) {
        const o =
            sortOrder === undefined
                ? GeometrySortOrder.ASCENDING
                : sortOrder === GeometrySortOrder.ASCENDING
                  ? GeometrySortOrder.DESCENDING
                  : undefined;

        return {
            sortBy: o ? sortBy : GeometrySortBy.NO_SORTING,
            sortOrder: o,
        };
    } else {
        return {
            sortBy: sortBy,
            sortOrder: GeometrySortOrder.ASCENDING,
        };
    }
}

export const InfraModelSearchResult: React.FC<InfraModelSearchResultProps> = (
    props: InfraModelSearchResultProps,
) => {
    const privileges = useCommonDataAppSelector((state) => state.userPrivileges).map((p) => p.code);
    const trackNumbers = useTrackNumbers(
        userHasPrivilege(privileges, VIEW_LAYOUT_DRAFT) ? 'DRAFT' : 'OFFICIAL',
    );

    const [linkingSummaries, setLinkingSummaries] = useState<
        Map<GeometryPlanId, GeometryPlanLinkingSummary | undefined>
    >(() => new Map());
    const [confirmDownloadPlan, setConfirmDownloadPlan] = React.useState<
        GeometryPlanHeader | undefined
    >();
    const [confirmHidePlan, setConfirmHidePlan] = React.useState<GeometryPlanHeader | undefined>();

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
            if (plansAndSummaries == undefined) {
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

    function setFilter(sortByValue: GeometrySortBy) {
        const sort = toggleSortOrder(
            props.searchParams.sortBy,
            sortByValue,
            props.searchParams.sortOrder,
        );

        props.onSearchParamsChange({
            ...props.searchParams,
            sortBy: sort.sortBy,
            sortOrder: sort.sortOrder,
        });
    }

    function getSortingIcon(): IconComponent {
        return props.searchParams.sortOrder == GeometrySortOrder.ASCENDING
            ? Icons.Ascending
            : Icons.Descending;
    }

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
                <Table wide isLoading={props.searchState != 'idle'}>
                    <thead>
                        <tr>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.PROJECT_NAME
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.PROJECT_NAME)}
                                className={'infra-model-list-search-result__project-name'}
                                qa-id="im-form.name-header">
                                {t('im-form.name-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.FILE_NAME
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.FILE_NAME)}
                                className={'infra-model-list-search-result__file-name'}
                                qa-id="im-form.file-name-header">
                                {t('im-form.file-name')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.TRACK_NUMBER
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.TRACK_NUMBER)}
                                className={'infra-model-list-search-result__track-number'}
                                qa-id="im-form.track-number-header">
                                {t('im-form.tracknumberfield')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.KM_START
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.KM_START)}
                                className={'infra-model-list-search-result__kilometer'}
                                qa-id="im-form.km-start-header">
                                {t('im-form.km-start-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.KM_END
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.KM_END)}
                                className={'infra-model-list-search-result__kilometer'}
                                qa-id="im-form.km-end-header">
                                {t('im-form.km-end-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.PLAN_PHASE
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.PLAN_PHASE)}
                                className={'infra-model-list-search-result__plan-phase'}
                                qa-id="im-form.plan-phase-header">
                                {t('im-form.plan-phase-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.DECISION_PHASE
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.DECISION_PHASE)}
                                className={'infra-model-list-search-result__decision-phase'}
                                qa-id="im-form.decision-phase-header">
                                {t('im-form.decision-phase-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.CREATED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.CREATED_AT)}
                                className={'infra-model-list-search-result__date-without-time'}
                                qa-id="im-form.created-at-header">
                                {t('im-form.plan-time-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.UPLOADED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.UPLOADED_AT)}
                                className={'infra-model-list-search-result__date-with-time'}
                                qa-id="im-form.uploaded-at-header">
                                {t('im-form.created-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.LINKED_AT
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.LINKED_AT)}
                                className={'infra-model-list-search-result__date-with-time'}
                                qa-id="im-form.linked-at-header">
                                {t('im-form.linked-at-field')}
                            </Th>
                            <Th
                                icon={
                                    props.searchParams.sortBy == GeometrySortBy.LINKED_BY
                                        ? getSortingIcon()
                                        : undefined
                                }
                                onClick={() => setFilter(GeometrySortBy.LINKED_BY)}
                                className={'infra-model-list-search-result__linked-by-users'}
                                qa-id="im-form.linked-by-header">
                                {t('im-form.linked-by-users-field')}
                            </Th>
                            <Th />
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
                                            <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                                                <Button
                                                    title={t('im-form.download-file')}
                                                    onClick={() => downloadPlan(plan)}
                                                    variant={ButtonVariant.GHOST}
                                                    size={ButtonSize.SMALL}
                                                    icon={Icons.Download}
                                                />
                                            </PrivilegeRequired>
                                        </td>
                                        <td onClick={(e) => e.stopPropagation()}>
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
                            })}
                    </tbody>
                </Table>
            </div>
            {confirmDownloadPlan && (
                <ConfirmDownloadUnreliableInfraModelDialog
                    onClose={() => setConfirmDownloadPlan(undefined)}
                    plan={confirmDownloadPlan}
                />
            )}
            {confirmHidePlan && (
                <ConfirmHideInfraModel
                    onClose={() => setConfirmHidePlan(undefined)}
                    plan={confirmHidePlan}
                />
            )}
        </div>
    );
};
