import styles from 'selection-panel/selection-panel.scss';
import { Eye, VisibilityState } from 'geoviite-design-lib/eye/eye';
import { createClassName } from 'vayla-design-lib/utils';
import { GeometryPlanPanel } from 'selection-panel/geometry-plan-panel/geometry-plan-panel';
import {
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
    aggregateVisibility,
    isPlanFullyVisible,
    mergeVisiblePlans,
    wholePlanVisibility,
} from 'selection/selection-store';
import * as React from 'react';
import { useMemo } from 'react';
import {
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySortBy,
    GeometrySortOrder,
    PlanSource,
    ProjectId,
} from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { useMapState, useRateLimitedEffect, useSetState } from 'utils/react-utils';
import {
    GeometryPlanLayoutResult,
    getGeometryPlanHeadersBySearchTerms,
    getTrackLayoutPlans,
} from 'geometry/geometry-api';
import { GeometryPlanLayout, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { getPlanLinkStatuses } from 'linking/linking-api';
import { MapViewport } from 'map/map-model';
import {
    OnSelectOptions,
    OpenPlanLayout,
    OptionalItemCollections,
    VisiblePlanLayout,
} from 'selection/selection-model';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext, officialMainLayoutContext } from 'common/common-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { filterNotEmpty, reuseListElements } from 'utils/array-utils';
import { GeometryPlanFilterMenuContainer } from 'selection-panel/geometry-plan-panel/geometry-plan-filter-menu-container';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';
import { CustomGeometryValidationIssue } from 'infra-model/infra-model-slice';

type GeometryPlansPanelProps = {
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    selectedItems: OptionalItemCollections;
    viewport: MapViewport;
    selectedTrackNumberIds: LayoutTrackNumberId[];
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    planDownloadPopupOpen: boolean;
    onSetPlanVisibility: (payload: { plan: VisiblePlanLayout; visible: boolean }) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanDownloadPopupOpen: (payload: boolean) => void;
    onSelect: (options: OnSelectOptions) => void;
    forcedVisiblePlan?: VisiblePlanLayout;
    grouping: GeometryPlanGrouping;
    visibleSources: PlanSource[];
};
const MAX_PLAN_HEADERS = 50;

type FetchedGeometryPlan = {
    planLayout: GeometryPlanLayout | undefined;
    planLayoutError: CustomGeometryValidationIssue | undefined;
    linkStatus: GeometryPlanLinkStatus | undefined;
};

const SelectionPanelGeometrySection: React.FC<GeometryPlansPanelProps> = ({
    changeTimes,
    layoutContext,
    selectedItems,
    viewport,
    selectedTrackNumberIds,
    openPlans,
    visiblePlans,
    planDownloadPopupOpen,
    onSetPlanVisibility,
    onToggleAlignmentVisibility,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    togglePlanOpen,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
    togglePlanDownloadPopupOpen,
    onSelect,
    grouping,
    visibleSources,
    forcedVisiblePlan,
}) => {
    const { t } = useTranslation();
    const [planHeadersDisplayableInPanel, setPlanHeadersDisplayableInPanel] = React.useState<
        GeometryPlanHeader[]
    >([]);
    const [planIdsInViewport, setPlanIdsInViewport] = React.useState<GeometryPlanId[]>([]);
    const [planHeaderCount, setPlanHeaderCount] = React.useState<number>(0);
    const [fetchedPlans, setSingleFetchedPlan] = useMapState<GeometryPlanId, FetchedGeometryPlan>();
    const [plansBeingFetched, startFetchingPlan, finishFetchingPlan] =
        useSetState<GeometryPlanId>();
    const trackNumbers = useTrackNumbers(
        officialMainLayoutContext(),
        changeTimes.layoutTrackNumber,
    );
    const selectedTrackNumbers = useMemo(
        () =>
            trackNumbers &&
            trackNumbers
                .filter((tn) => selectedTrackNumberIds.includes(tn.id))
                .map((tn) => tn.number)
                .sort(),
        [trackNumbers, selectedTrackNumberIds],
    );

    useRateLimitedEffect(
        () => {
            if (viewport.area !== undefined) {
                getGeometryPlanHeadersBySearchTerms(
                    MAX_PLAN_HEADERS,
                    0,
                    viewport.area,
                    visibleSources,
                    selectedTrackNumbers,
                    undefined,
                    grouping === GeometryPlanGrouping.ByProject
                        ? GeometrySortBy.PROJECT_NAME
                        : GeometrySortBy.NAME,
                    GeometrySortOrder.ASCENDING,
                ).then((result) => {
                    setPlanHeadersDisplayableInPanel((planHeadersDisplayableInPanel) =>
                        reuseListElements(
                            result.planHeaders.items,
                            planHeadersDisplayableInPanel,
                            (header) => header.id,
                        ),
                    );
                    setPlanHeaderCount(result.planHeaders.totalCount);
                    setPlanIdsInViewport(
                        result.planHeaders.items.map(({ id }) => id).concat(result.remainingIds),
                    );
                });
            }
        },
        1000,
        [viewport.area, changeTimes.geometryPlan, selectedTrackNumbers, visibleSources, grouping],
    );

    React.useEffect(
        () => void fetchPlanLayouts([...fetchedPlans.keys()]),
        [
            layoutContext,
            changeTimes.geometryPlan,
            changeTimes.layoutTrackNumber,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutSwitch,
            changeTimes.layoutKmPost,
        ],
    );

    const fetchPlanLayouts = React.useCallback(
        (ids: GeometryPlanId[]): Promise<GeometryPlanLayoutResult[]> => {
            ids.forEach(startFetchingPlan);
            return Promise.all([
                getTrackLayoutPlans(ids, changeTimes.geometryPlan),
                getPlanLinkStatuses(ids, layoutContext),
            ])
                .then(([layouts, linkStatuses]) => {
                    ids.forEach((id, index) => {
                        const layout = layouts[index];
                        const linkStatus = linkStatuses[index];
                        setSingleFetchedPlan(id, {
                            planLayout: layout?.layout,
                            planLayoutError: layout?.error,
                            linkStatus,
                        });
                    });
                    return layouts;
                })
                .finally(() => ids.forEach(finishFetchingPlan));
        },
        [changeTimes.geometryPlan, layoutContext],
    );

    const visiblePlansInView = visiblePlans.filter((p) =>
        planIdsInViewport.some((planId) => planId === p.id),
    );

    // Only used to compute the aggregate (hidden/partial/visible) display state, so that a plan
    // whose only visible item is the actively-linked (forced) one is shown as partial rather than
    // looking indistinguishable from fully hidden.
    const effectiveVisiblePlansInView = mergeVisiblePlans(visiblePlansInView, forcedVisiblePlan);

    const isPlanFullyVisibleInView = (planId: GeometryPlanId): boolean =>
        isPlanFullyVisible(
            visiblePlansInView.find((p) => p.id === planId),
            fetchedPlans.get(planId)?.planLayout,
        );

    function fetchPlansAndSetVisible(planIds: GeometryPlanId[]) {
        fetchPlanLayouts(planIds).then((plans) =>
            plans
                .map((p) => p.layout)
                .filter(filterNotEmpty)
                .forEach((plan) =>
                    onSetPlanVisibility({ plan: wholePlanVisibility(plan), visible: true }),
                ),
        );
    }

    const allPlansVisibility = aggregateVisibility(
        effectiveVisiblePlansInView.length > 0,
        planHeadersDisplayableInPanel.length > 0 &&
            planHeadersDisplayableInPanel.every((h) => isPlanFullyVisibleInView(h.id)),
    );

    const toggleAllPlanVisibilities = () => {
        if (allPlansVisibility === 'visible') {
            visiblePlansInView.forEach((plan) => onSetPlanVisibility({ plan, visible: false }));
        } else if (planHeadersDisplayableInPanel.length === planHeaderCount) {
            const notFullyVisiblePlanIds = planHeadersDisplayableInPanel
                .filter((h) => !isPlanFullyVisibleInView(h.id))
                .map((h) => h.id);
            fetchPlansAndSetVisible(notFullyVisiblePlanIds);
        }
    };

    function projectVisibility(projectId: ProjectId): VisibilityState {
        const projectPlans = planHeadersDisplayableInPanel.filter(
            (plan) => plan.project.id === projectId,
        );
        const anyVisible = projectPlans.some((plan) =>
            effectiveVisiblePlansInView.some((visiblePlan) => visiblePlan.id === plan.id),
        );
        const allVisible =
            projectPlans.length > 0 &&
            projectPlans.every((plan) => isPlanFullyVisibleInView(plan.id));
        return aggregateVisibility(anyVisible, allVisible);
    }

    function setProjectVisibility(projectId: ProjectId, newVisibility: boolean) {
        const projectPlans = planHeadersDisplayableInPanel.filter(
            (plan) => plan.project.id === projectId,
        );
        if (!newVisibility) {
            const visibleProjectPlans = visiblePlansInView.filter((visiblePlan) =>
                projectPlans.some((projectPlan) => projectPlan.id === visiblePlan.id),
            );
            visibleProjectPlans.forEach((plan) => onSetPlanVisibility({ plan, visible: false }));
        } else {
            const notFullyVisiblePlanIds = projectPlans
                .filter((plan) => !isPlanFullyVisibleInView(plan.id))
                .map((plan) => plan.id);
            fetchPlansAndSetVisible(notFullyVisiblePlanIds);
        }
    }

    return (
        <section>
            <h3 className={styles['selection-panel__title']}>
                <span className={styles['selection-panel__title-text']}>
                    {`${t('selection-panel.geometries.title')} (${
                        planHeadersDisplayableInPanel.length
                    }/${planHeaderCount})`}{' '}
                </span>

                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <div>
                        <Button
                            qa-id="plan-download-open"
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.GHOST}
                            isPressed={planDownloadPopupOpen}
                            onClick={() => togglePlanDownloadPopupOpen(!planDownloadPopupOpen)}
                            icon={Icons.Download}
                        />
                    </div>
                </PrivilegeRequired>
                <GeometryPlanFilterMenuContainer />
                <Eye
                    disabled={planHeadersDisplayableInPanel.length === 0}
                    onVisibilityToggle={toggleAllPlanVisibilities}
                    visibility={allPlansVisibility}
                />
            </h3>
            <div
                className={createClassName(
                    styles['selection-panel__content'],
                    styles['selection-panel__content--unpadded'],
                )}>
                {planHeadersDisplayableInPanel.length === planHeaderCount &&
                    planHeadersDisplayableInPanel.map((h, index, allPlans) => {
                        const isSameAsPrevProject =
                            h.project.id === allPlans[index - 1]?.project?.id;
                        const showProjectRow =
                            grouping === GeometryPlanGrouping.ByProject && !isSameAsPrevProject;
                        const projectVisibilityState = projectVisibility(h.project.id);
                        return (
                            <React.Fragment key={h.id}>
                                {showProjectRow && (
                                    <div className={styles['selection-panel__project']}>
                                        <span className={styles['selection-panel__project-title']}>
                                            {h.project.name}
                                        </span>
                                        <Eye
                                            onVisibilityToggle={() =>
                                                setProjectVisibility(
                                                    h.project.id,
                                                    projectVisibilityState !== 'visible',
                                                )
                                            }
                                            visibility={projectVisibilityState}
                                        />
                                    </div>
                                )}
                                <GeometryPlanPanel
                                    key={h.id}
                                    planHeader={h}
                                    onSelect={onSelect}
                                    changeTimes={changeTimes}
                                    onSetPlanVisibility={onSetPlanVisibility}
                                    onToggleAlignmentVisibility={onToggleAlignmentVisibility}
                                    onToggleSwitchVisibility={onToggleSwitchVisibility}
                                    onToggleKmPostVisibility={onToggleKmPostVisibility}
                                    selectedItems={selectedItems}
                                    visiblePlans={visiblePlans}
                                    forcedVisiblePlan={forcedVisiblePlan}
                                    togglePlanOpen={togglePlanOpen}
                                    openPlans={openPlans}
                                    togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                                    togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                                    togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                                    planLayout={fetchedPlans.get(h.id)?.planLayout}
                                    planLayoutError={fetchedPlans.get(h.id)?.planLayoutError}
                                    linkStatus={fetchedPlans.get(h.id)?.linkStatus}
                                    planBeingLoaded={plansBeingFetched.has(h.id)}
                                    fetchPlanLayouts={fetchPlanLayouts}
                                />
                            </React.Fragment>
                        );
                    })}
                {planHeadersDisplayableInPanel.length < planHeaderCount && (
                    <span className={styles['selection-panel__subtitle']}>{`${t(
                        'selection-panel.zoom-closer',
                    )}`}</span>
                )}

                {planHeadersDisplayableInPanel.length === 0 && (
                    <span className={styles['selection-panel__subtitle']}>
                        {`${t('selection-panel.no-results')}`}{' '}
                    </span>
                )}
            </div>
        </section>
    );
};

export default SelectionPanelGeometrySection;
