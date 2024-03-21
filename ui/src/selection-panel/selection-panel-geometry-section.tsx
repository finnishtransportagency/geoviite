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
import {
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySortBy,
    GeometrySortOrder,
} from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { useRateLimitedEffect, useMapState, useSetState } from 'utils/react-utils';
import {
    getGeometryPlanHeadersBySearchTerms,
    getTrackLayoutPlan,
    getTrackLayoutPlans,
} from 'geometry/geometry-api';
import { GeometryPlanLayout, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { getPlanLinkStatus, getPlanLinkStatuses } from 'linking/linking-api';
import { MapViewport } from 'map/map-model';
import {
    OnSelectOptions,
    OpenPlanLayout,
    OptionalItemCollections,
    VisiblePlanLayout,
} from 'selection/selection-model';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext } from 'common/common-model';

type GeometryPlansPanelProps = {
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    selectedItems: OptionalItemCollections;
    viewport: MapViewport;
    selectedTrackNumbers: LayoutTrackNumberId[];
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
    selectedTrackNumbers,
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
    disabled = false,
}) => {
    const { t } = useTranslation();
    const [planHeadersDisplayableInPanel, setPlanHeadersDisplayableInPanel] = React.useState<
        GeometryPlanHeader[]
    >([]);
    const [planIdsInViewport, setPlanIdsInViewport] = React.useState<GeometryPlanId[]>([]);
    const [planHeaderCount, setPlanHeaderCount] = React.useState<number>(0);
    const [fetchedPlans, setSingleFetchedPlan, _, setAllFetchedPlans] = useMapState<
        GeometryPlanId,
        FetchedGeometryPlan
    >();
    const [plansBeingFetched, startFetchingPlan, finishFetchingPlan] =
        useSetState<GeometryPlanId>();

    useRateLimitedEffect(
        () => {
            if (viewport.area !== undefined) {
                getGeometryPlanHeadersBySearchTerms(
                    MAX_PLAN_HEADERS,
                    0,
                    viewport.area,
                    ['GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'],
                    selectedTrackNumbers,
                    undefined,
                    GeometrySortBy.UPLOADED_AT,
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
        [viewport.area, changeTimes.geometryPlan, selectedTrackNumbers.sort().join('')],
    );

    React.useEffect(
        () => [...fetchedPlans.keys()].forEach(fetchPlanLayout),
        [
            layoutContext,
            changeTimes.geometryPlan,
            changeTimes.layoutReferenceLine,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutSwitch,
            changeTimes.layoutKmPost,
        ],
    );

    const fetchPlanLayout = (id: GeometryPlanId) => {
        startFetchingPlan(id);
        const rv = Promise.all([
            getTrackLayoutPlan(id, changeTimes.geometryPlan, false),
            getPlanLinkStatus(id, layoutContext),
        ]).then(([planLayout, linkStatus]) => {
            if (planLayout) {
                setSingleFetchedPlan(id, { planLayout, linkStatus });
            }
            return planLayout;
        });
        rv.finally(() => finishFetchingPlan(id));
        return rv;
    };

    const visiblePlansInView = visiblePlans.filter((p) =>
        planIdsInViewport.some((planId) => planId === p.id),
    );

    const toggleAllPlanVisibilities = () => {
        if (visiblePlansInView.length > 0) {
            visiblePlansInView.forEach(onTogglePlanVisibility);
        } else if (planHeadersDisplayableInPanel.length === planHeaderCount) {
            planHeadersDisplayableInPanel.forEach((h) => startFetchingPlan(h.id));
            getTrackLayoutPlans(
                planHeadersDisplayableInPanel.map((h) => h.id),
                changeTimes.geometryPlan,
            )
                .then((plans) =>
                    getPlanLinkStatuses(
                        plans.map((p) => p.id),
                        layoutContext,
                    ).then((statuses) => ({ plans: plans, statuses: statuses })),
                )
                .then(({ plans, statuses }) => {
                    const map = new Map(fetchedPlans);
                    plans.forEach((plan) => {
                        const status = statuses.find((s) => s.id === plan.id);
                        if (status) {
                            map.set(plan.id, {
                                planLayout: plan,
                                linkStatus: status,
                            });
                            onTogglePlanVisibility(wholePlanVisibility(plan));
                        }
                    });
                    setAllFetchedPlans(map);
                })
                .finally(() => {
                    planHeadersDisplayableInPanel.forEach((h) => finishFetchingPlan(h.id));
                });
        }
    };

    return (
        <section>
            <h3 className={styles['selection-panel__title']}>
                {`${t('selection-panel.geometries-title')} (${
                    planHeadersDisplayableInPanel.length
                }/${planHeaderCount})`}{' '}
                {planHeadersDisplayableInPanel.length > 1 && !disabled && (
                    <Eye
                        onVisibilityToggle={toggleAllPlanVisibilities}
                        visibility={visiblePlansInView.length > 0}
                    />
                )}
            </h3>
            <div
                className={createClassName(
                    styles['selection-panel__content'],
                    styles['selection-panel__content--unpadded'],
                )}>
                {planHeadersDisplayableInPanel.length == planHeaderCount &&
                    planHeadersDisplayableInPanel.map((h) => {
                        return (
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
                                loadPlanLayout={() => fetchPlanLayout(h.id)}
                                disabled={disabled}
                            />
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
