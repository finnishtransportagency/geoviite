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
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';

type GeometryPlansPanelProps = {
    changeTimes: ChangeTimes;
    publishType: PublishType;
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
};
const MAX_PLAN_HEADERS = 50;

type LoadedGeometryPlan = {
    planLayout: GeometryPlanLayout;
    linkStatus: GeometryPlanLinkStatus;
};

const SelectionPanelGeometrySection: React.FC<GeometryPlansPanelProps> = ({
    changeTimes,
    publishType,
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
}) => {
    const { t } = useTranslation();
    const [planHeadersInView, setPlanHeadersInView] = React.useState<GeometryPlanHeader[]>([]);
    const [planHeaderCount, setPlanHeaderCount] = React.useState<number>(0);
    const [loadedPlans, setLoadedPlan, _, setLoadedPlanMap] = useMapState<
        GeometryPlanId,
        LoadedGeometryPlan
    >();
    const [plansBeingLoaded, startLoadingPlan, finishLoadingPlan] = useSetState<GeometryPlanId>();

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
                ).then((page) => {
                    setPlanHeadersInView(page.items);
                    setPlanHeaderCount(page.totalCount);
                });
            }
        },
        1000,
        [viewport.area, changeTimes.geometryPlan, selectedTrackNumbers.sort().join('')],
    );

    React.useEffect(
        () => [...loadedPlans.keys()].forEach(loadPlanLayout),
        [
            publishType,
            changeTimes.geometryPlan,
            changeTimes.layoutReferenceLine,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutSwitch,
            changeTimes.layoutKmPost,
        ],
    );

    const loadPlanLayout = (id: GeometryPlanId) => {
        startLoadingPlan(id);
        const rv = Promise.all([
            getTrackLayoutPlan(id, changeTimes.geometryPlan, false),
            getPlanLinkStatus(id, publishType),
        ]).then(([planLayout, linkStatus]) => {
            if (planLayout) {
                setLoadedPlan(id, { planLayout, linkStatus });
            }
            return planLayout;
        });
        rv.finally(() => finishLoadingPlan(id));
        return rv;
    };

    const selectedPlanHeadersInView = planHeadersInView.filter((headerInView) =>
        visiblePlans.some((p) => p.id === headerInView.id),
    );
    const visiblePlansInView = visiblePlans.filter((p) =>
        selectedPlanHeadersInView.some((ph) => ph.id === p.id),
    );

    const toggleAllPlanVisibilities = () => {
        if (visiblePlansInView.length > 0) {
            visiblePlansInView.forEach(onTogglePlanVisibility);
        } else {
            planHeadersInView.forEach((h) => startLoadingPlan(h.id));
            getTrackLayoutPlans(
                planHeadersInView.map((h) => h.id),
                changeTimes.geometryPlan,
            )
                .then((plans) => {
                    getPlanLinkStatuses(
                        plans.map((p) => p.id),
                        publishType,
                    ).then((statuses) => {
                        const map = new Map(loadedPlans);
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
                        setLoadedPlanMap(map);
                    });
                })
                .finally(() => {
                    planHeadersInView.forEach((h) => finishLoadingPlan(h.id));
                });
        }
    };

    return (
        <section>
            <h3 className={styles['selection-panel__title']}>
                {`${t('selection-panel.geometries-title')} (${
                    planHeadersInView.length
                }/${planHeaderCount})`}{' '}
                {planHeadersInView.length > 1 && planHeadersInView.length === planHeaderCount && (
                    <Eye
                        onVisibilityToggle={toggleAllPlanVisibilities}
                        visibility={visiblePlans.length > 0}
                    />
                )}
            </h3>
            <div
                className={createClassName(
                    styles['selection-panel__content'],
                    styles['selection-panel__content--unpadded'],
                )}>
                {planHeadersInView.length == planHeaderCount &&
                    planHeadersInView.map((h) => {
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
                                publishType={publishType}
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
                                planLayout={loadedPlans.get(h.id)?.planLayout}
                                linkStatus={loadedPlans.get(h.id)?.linkStatus}
                                planBeingLoaded={plansBeingLoaded.has(h.id)}
                                loadPlanLayout={() => loadPlanLayout(h.id)}
                            />
                        );
                    })}
                {planHeadersInView.length < planHeaderCount && (
                    <span className={styles['selection-panel__subtitle']}>{`${t(
                        'selection-panel.zoom-closer',
                    )}`}</span>
                )}

                {planHeadersInView.length === 0 && (
                    <span className={styles['selection-panel__subtitle']}>
                        {`${t('selection-panel.no-results')}`}{' '}
                    </span>
                )}
            </div>
        </section>
    );
};

export default SelectionPanelGeometrySection;
