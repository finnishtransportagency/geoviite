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
} from 'selection/selection-store';
import * as React from 'react';
import {
    GeometryPlanHeader,
    GeometryPlanId,
    SortByValue,
    SortOrderValue,
} from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { useMapState, useSetState } from 'utils/react-utils';
import { getGeometryPlanHeadersBySearchTerms, getTrackLayoutPlan } from 'geometry/geometry-api';
import { GeometryPlanLayout, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { getPlanLinkStatus } from 'linking/linking-api';
import { MapViewport } from 'map/map-model';
import {
    OnSelectOptions,
    OpenedPlanLayout,
    OptionalItemCollections,
} from 'selection/selection-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';

type GeometryPlansPanelProps = {
    changeTimes: ChangeTimes;
    publishType: PublishType;
    selectedItems?: OptionalItemCollections;
    viewport: MapViewport;
    selectedPlanLayouts?: GeometryPlanLayout[];
    selectedTrackNumbers: LayoutTrackNumberId[];
    onTogglePlanVisibility: (payload: GeometryPlanLayout | null) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    openedPlanLayouts: OpenedPlanLayout[];
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
    selectedPlanLayouts,
    selectedTrackNumbers,
    onTogglePlanVisibility,
    onToggleAlignmentVisibility,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    togglePlanOpen,
    openedPlanLayouts,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
    onSelect,
}) => {
    const { t } = useTranslation();
    const [planHeadersInView, setPlanHeadersInView] = React.useState<GeometryPlanHeader[]>([]);
    const [planHeaderCount, setPlanHeaderCount] = React.useState<number>(0);
    const [loadedPlans, setLoadedPlan] = useMapState<GeometryPlanId, LoadedGeometryPlan>();
    const [plansBeingLoaded, startLoadingPlan, finishLoadingPlan] = useSetState<GeometryPlanId>();

    React.useEffect(() => {
        if (viewport.area) {
            getGeometryPlanHeadersBySearchTerms(
                MAX_PLAN_HEADERS,
                0,
                viewport.area,
                ['GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'],
                selectedTrackNumbers,
                undefined,
                SortByValue.UPLOADED_AT,
                SortOrderValue.ASCENDING,
            ).then((page) => {
                setPlanHeadersInView(page.items);
                setPlanHeaderCount(page.totalCount);
            });
        }
    }, [viewport.area, changeTimes.geometryPlan, selectedTrackNumbers.sort().join('')]);

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

    const planHeaderIdsInView = planHeadersInView
        .map((plan) => plan.id)
        .reduce((set, id) => set.add(id), new Set());
    const selectedPlansInView = (selectedPlanLayouts ?? []).filter((plan) =>
        planHeaderIdsInView.has(plan.planId),
    );

    const toggleAllPlanVisibilities = () => {
        if (selectedPlansInView.length > 0) {
            selectedPlansInView.forEach(onTogglePlanVisibility);
        } else {
            planHeadersInView.forEach((ph) => {
                loadPlanLayout(ph.id).then((loadedPlan) => {
                    onTogglePlanVisibility(loadedPlan);
                });
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
                        visibility={selectedPlansInView.length > 0}
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
                                        geometryAlignments: [
                                            {
                                                geometryItem: alignment,
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
                                        geometrySwitchIds: [
                                            {
                                                id: switchItem.id,
                                                planId: h.id,
                                            },
                                        ],
                                        isToggle: true,
                                    })
                                }
                                onToggleKmPostVisibility={onToggleKmPostVisibility}
                                onToggleKmPostSelection={(kmPost) =>
                                    onSelect({
                                        ...createEmptyItemCollections(),
                                        geometryKmPostIds: [
                                            {
                                                id: kmPost.id,
                                                planId: h.id,
                                            },
                                        ],
                                        isToggle: true,
                                    })
                                }
                                selectedItems={selectedItems}
                                selectedPlanLayouts={selectedPlanLayouts}
                                togglePlanOpen={togglePlanOpen}
                                openedPlanLayouts={openedPlanLayouts}
                                togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                                togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                                togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                                planLayout={loadedPlans.get(h.id)?.planLayout ?? null}
                                linkStatus={loadedPlans.get(h.id)?.linkStatus ?? null}
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
