import styles from 'selection-panel/selection-panel.scss';
import { Eye } from 'geoviite-design-lib/eye/eye';
import { createClassName } from 'vayla-design-lib/utils';
import { GeometryPlanPanel } from 'selection-panel/geometry-plan-panel/geometry-plan-panel';
import {
    createEmptyItemCollections,
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
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
import { getGeometryPlanHeadersBySearchTerms, getTrackLayoutPlan } from 'geometry/geometry-api';
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
import { filterNotEmpty, filterUnique, first } from 'utils/array-utils';
import { GeometryPlanFilterMenuContainer } from 'selection-panel/geometry-plan-panel/geometry-plan-filter-menu-container';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';

type GeometryPlansPanelProps = {
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    selectedItems: OptionalItemCollections;
    viewport: MapViewport;
    selectedTrackNumberIds: LayoutTrackNumberId[];
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    onTogglePlanVisibility: (payload: VisiblePlanLayout) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
    onSelect: (options: OnSelectOptions) => void;
    disabled?: boolean;
    grouping: GeometryPlanGrouping;
    visibleSources: PlanSource[];
};
const MAX_PLAN_HEADERS = 50;

type FetchedGeometryPlan = {
    planLayout: GeometryPlanLayout;
    linkStatus: GeometryPlanLinkStatus;
};

const SelectionPanelGeometrySection: React.FC<GeometryPlansPanelProps> = ({
    changeTimes,
    layoutContext,
    selectedItems,
    viewport,
    selectedTrackNumberIds,
    openPlans,
    visiblePlans,
    onTogglePlanVisibility,
    onToggleAlignmentVisibility,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    togglePlanOpen,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
    onSelect,
    grouping,
    visibleSources,
    disabled = false,
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
                    grouping == GeometryPlanGrouping.ByProject
                        ? GeometrySortBy.PROJECT_NAME
                        : GeometrySortBy.NAME,
                    GeometrySortOrder.ASCENDING,
                ).then((result) => {
                    setPlanHeadersDisplayableInPanel(result.planHeaders.items);
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
            changeTimes.layoutReferenceLine,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutSwitch,
            changeTimes.layoutKmPost,
        ],
    );

    const fetchPlanLayouts = (
        ids: GeometryPlanId[],
    ): Promise<(GeometryPlanLayout | undefined)[]> => {
        ids.forEach(startFetchingPlan);
        return Promise.all([
            Promise.all(ids.map((id) => getTrackLayoutPlan(id, changeTimes.geometryPlan))),
            getPlanLinkStatuses(ids, layoutContext),
        ])
            .then(([planLayouts, linkStatuses]) =>
                ids.map((id, index) => {
                    const planLayout = planLayouts[index];
                    const linkStatus = linkStatuses[index];
                    if (planLayout && linkStatus) {
                        setSingleFetchedPlan(id, { planLayout, linkStatus });
                        return planLayout;
                    } else {
                        return undefined;
                    }
                }),
            )
            .finally(() => ids.forEach(finishFetchingPlan));
    };

    const visiblePlansInView = visiblePlans.filter((p) =>
        planIdsInViewport.some((planId) => planId === p.id),
    );

    function fetchPlansAndSetVisible(planIds: GeometryPlanId[]) {
        fetchPlanLayouts(planIds).then((plans) =>
            plans
                .filter(filterNotEmpty)
                .forEach((plan) => onTogglePlanVisibility(wholePlanVisibility(plan))),
        );
    }

    const toggleAllPlanVisibilities = () => {
        if (visiblePlansInView.length > 0) {
            visiblePlansInView.forEach(onTogglePlanVisibility);
        } else if (planHeadersDisplayableInPanel.length === planHeaderCount) {
            const allDisplayablePlanIds = planHeadersDisplayableInPanel.map((h) => h.id);
            fetchPlansAndSetVisible(allDisplayablePlanIds);
        }
    };

    const visibleProjectIds = planHeadersDisplayableInPanel
        .filter((plan) => visiblePlans.some((visiblePlan) => visiblePlan.id == plan.id))
        .map((plan) => plan.project.id)
        .filter(filterUnique);

    function setProjectVisibility(projectId: ProjectId, newVisibility: boolean) {
        const projectPlans = planHeadersDisplayableInPanel.filter(
            (plan) => plan.project.id == projectId,
        );
        if (!newVisibility) {
            const visibleProjectPlans = visiblePlansInView.filter((visiblePlan) =>
                projectPlans.some((projectPlan) => projectPlan.id == visiblePlan.id),
            );
            visibleProjectPlans.forEach(onTogglePlanVisibility);
        } else {
            const projectPlanIds = projectPlans.map((plan) => plan.id);
            fetchPlansAndSetVisible(projectPlanIds);
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

                <GeometryPlanFilterMenuContainer />
                <Eye
                    disabled={disabled || planHeadersDisplayableInPanel.length == 0}
                    onVisibilityToggle={toggleAllPlanVisibilities}
                    visibility={visiblePlansInView.length > 0}
                />
            </h3>
            <div
                className={createClassName(
                    styles['selection-panel__content'],
                    styles['selection-panel__content--unpadded'],
                )}>
                {planHeadersDisplayableInPanel.length == planHeaderCount &&
                    planHeadersDisplayableInPanel.map((h, index, allPlans) => {
                        const isSameAsPrevProject =
                            h.project.id === allPlans[index - 1]?.project?.id;
                        const showProjectRow =
                            grouping == GeometryPlanGrouping.ByProject && !isSameAsPrevProject;
                        const projectIsVisible = visibleProjectIds.includes(h.project.id);
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
                                                    !projectIsVisible,
                                                )
                                            }
                                            visibility={projectIsVisible}
                                        />
                                    </div>
                                )}
                                <GeometryPlanPanel
                                    key={h.id}
                                    planHeader={h}
                                    onPlanHeaderSelection={(header) =>
                                        onSelect({
                                            ...createEmptyItemCollections(),
                                            geometryPlans: [header.id],
                                            isToggle: true,
                                        })
                                    }
                                    changeTimes={changeTimes}
                                    onTogglePlanVisibility={onTogglePlanVisibility}
                                    onToggleAlignmentVisibility={onToggleAlignmentVisibility}
                                    onToggleAlignmentSelection={(alignment) =>
                                        onSelect({
                                            ...createEmptyItemCollections(),
                                            geometryAlignmentIds: [
                                                {
                                                    geometryId: alignment.id,
                                                    planId: h.id,
                                                },
                                            ],
                                            isToggle: true,
                                        })
                                    }
                                    onToggleSwitchVisibility={onToggleSwitchVisibility}
                                    onToggleSwitchSelection={(switchItem) =>
                                        onSelect({
                                            ...createEmptyItemCollections(),
                                            geometrySwitchIds: switchItem.sourceId
                                                ? [
                                                      {
                                                          geometryId: switchItem.sourceId,
                                                          planId: h.id,
                                                      },
                                                  ]
                                                : [],
                                            isToggle: true,
                                        })
                                    }
                                    onToggleKmPostVisibility={onToggleKmPostVisibility}
                                    onToggleKmPostSelection={(kmPost) =>
                                        onSelect({
                                            ...createEmptyItemCollections(),
                                            geometryKmPostIds: kmPost.sourceId
                                                ? [
                                                      {
                                                          geometryId: kmPost.sourceId,
                                                          planId: h.id,
                                                      },
                                                  ]
                                                : [],
                                            isToggle: true,
                                        })
                                    }
                                    selectedItems={selectedItems}
                                    visiblePlans={visiblePlans}
                                    togglePlanOpen={togglePlanOpen}
                                    openPlans={openPlans}
                                    togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                                    togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                                    togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                                    planLayout={fetchedPlans.get(h.id)?.planLayout}
                                    linkStatus={fetchedPlans.get(h.id)?.linkStatus}
                                    planBeingLoaded={plansBeingFetched.has(h.id)}
                                    loadPlanLayout={() =>
                                        fetchPlanLayouts([h.id]).then((ps) => first(ps))
                                    }
                                    disabled={disabled}
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
