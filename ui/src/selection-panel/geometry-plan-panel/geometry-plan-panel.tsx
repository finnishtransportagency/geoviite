import * as React from 'react';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutSwitch,
    PlanLayoutAlignment,
} from 'track-layout/track-layout-model';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { Accordion } from 'geoviite-design-lib/accordion/accordion';
import {
    OnSelectOptions,
    OpenPlanLayout,
    OptionalItemCollections,
    VisiblePlanLayout,
} from 'selection/selection-model';
import styles from './geometry-plan-panel.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import {
    aggregateVisibility,
    createEmptyItemCollections,
    isGeometryForcedVisible,
    isPlanFullyVisible,
    mergeVisiblePlans,
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
    wholePlanVisibility,
} from 'selection/selection-store';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { useTranslation } from 'react-i18next';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { AlignmentHeader, GeometryAlignmentHeader } from 'track-layout/layout-map-api';
import { ChangeTimes } from 'common/common-slice';
import { GeometryPlanLayoutResult } from 'geometry/geometry-api';
import { CustomGeometryValidationIssue } from 'infra-model/infra-model-slice';
import { first } from 'utils/array-utils';

type GeometryPlanProps = {
    planHeader: GeometryPlanHeader;
    onSelect: (options: OnSelectOptions) => void;
    changeTimes: ChangeTimes;
    onSetPlanVisibility: (payload: { plan: VisiblePlanLayout; visible: boolean }) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    selectedItems: OptionalItemCollections;
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    forcedVisiblePlan?: VisiblePlanLayout;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
    fetchPlanLayouts: (ids: GeometryPlanId[]) => Promise<GeometryPlanLayoutResult[]>;
    planLayout?: GeometryPlanLayout;
    planLayoutError?: CustomGeometryValidationIssue;
    linkStatus?: GeometryPlanLinkStatus;
    planBeingLoaded: boolean;
};

const GeometryPlanPanelM: React.FC<GeometryPlanProps> = ({
    planHeader,
    onSelect,
    onSetPlanVisibility,
    onToggleAlignmentVisibility,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    selectedItems,
    openPlans,
    visiblePlans,
    forcedVisiblePlan,
    togglePlanOpen,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
    planLayout,
    planLayoutError,
    linkStatus,
    fetchPlanLayouts,
    planBeingLoaded,
}: GeometryPlanProps) => {
    const { t } = useTranslation();
    const openPlanLayout = openPlans.find((p) => p.id === planHeader.id);
    const isPlanOpen = !!openPlanLayout;
    const isKmPostsOpen = openPlanLayout ? openPlanLayout.isKmPostsOpen : false;
    const isAlignmentsOpen = openPlanLayout ? openPlanLayout.isAlignmentsOpen : false;
    const isSwitchesOpen = openPlanLayout ? openPlanLayout.isSwitchesOpen : false;
    const [openingAccordion, setOpeningAccordion] = React.useState(false);

    const effectiveVisiblePlans = React.useMemo(
        () => mergeVisiblePlans(visiblePlans, forcedVisiblePlan),
        [visiblePlans, forcedVisiblePlan],
    );

    const planEntry = visiblePlans.find((p) => p.id === planHeader.id);
    const effectivePlanEntry = effectiveVisiblePlans.find((p) => p.id === planHeader.id);

    const planVisibilityState = React.useMemo(
        () =>
            aggregateVisibility(
                !!effectivePlanEntry,
                isPlanFullyVisible(effectivePlanEntry, planLayout),
            ),
        [effectivePlanEntry, planLayout],
    );

    // Triggers the loading of a previously opened plan after a refresh. Otherwise, the plan's data would not be
    // displayed and the user would have to click twice to open the plan again.
    React.useEffect(() => {
        if (isPlanOpen) {
            loadPlan();
        }
    }, []);

    const loadPlanLayout = () =>
        fetchPlanLayouts([planHeader.id]).then((ps) => first(ps) ?? undefined);

    const loadPlan = () => {
        setOpeningAccordion(true);
        loadPlanLayout()
            .then((planLayout) => {
                if (planLayout?.layout) {
                    togglePlanOpen({
                        isKmPostsOpen: isKmPostsOpen,
                        isSwitchesOpen: isSwitchesOpen,
                        isAlignmentsOpen: isAlignmentsOpen,
                        id: planLayout?.layout?.id,
                        isOpen: true,
                    });
                }
            })
            .finally(() => setOpeningAccordion(false));
    };

    const onPlanToggle = () => {
        if (planLayout) {
            togglePlanOpen({
                isKmPostsOpen: isKmPostsOpen,
                isSwitchesOpen: isSwitchesOpen,
                isAlignmentsOpen: isAlignmentsOpen,
                id: planLayout.id,
                isOpen: !isPlanOpen,
            });
        } else {
            loadPlan();
        }
    };

    const onPlanVisibilityToggle = () => {
        const shouldShow = planVisibilityState !== 'visible';

        if (!shouldShow) {
            if (planEntry) {
                onSetPlanVisibility({ plan: planEntry, visible: false });
            }
            return;
        }

        if (planLayout) {
            onSetPlanVisibility({ plan: wholePlanVisibility(planLayout), visible: true });
        } else {
            loadPlanLayout().then((p) => {
                if (p?.layout) {
                    onSetPlanVisibility({ plan: wholePlanVisibility(p.layout), visible: true });
                }
            });
        }
    };

    const onPlanHeaderSelection = () =>
        onSelect({
            ...createEmptyItemCollections(),
            geometryPlans: [planHeader.id],
            isToggle: true,
        });

    const onToggleAlignmentSelection = (alignment: GeometryAlignmentHeader) =>
        onSelect({
            ...createEmptyItemCollections(),
            geometryAlignmentIds: [
                {
                    geometryId: alignment.id,
                    planId: planHeader.id,
                },
            ],
            isToggle: true,
        });

    const onToggleSwitchSelection = (switchItem: LayoutSwitch) =>
        onSelect({
            ...createEmptyItemCollections(),
            geometrySwitchIds: switchItem.sourceId
                ? [
                      {
                          geometryId: switchItem.sourceId,
                          planId: planHeader.id,
                      },
                  ]
                : [],
            isToggle: true,
        });

    const onToggleKmPostSelection = (kmPost: LayoutKmPost) =>
        onSelect({
            ...createEmptyItemCollections(),
            geometryKmPostIds: kmPost.sourceId
                ? [
                      {
                          geometryId: kmPost.sourceId,
                          planId: planHeader.id,
                      },
                  ]
                : [],
            isToggle: true,
        });

    const onAlignmentSelect = (alignment: GeometryAlignmentHeader) => {
        if (planLayout) {
            onToggleAlignmentSelection(alignment);
            onToggleAlignmentVisibility({
                alignmentId: alignment.id,
                planId: planLayout.id,
                keepAlignmentVisible: true,
            });
        }
    };

    const onSwitchSelect = (switchItem: LayoutSwitch) => {
        if (planLayout && switchItem.sourceId) {
            onToggleSwitchSelection(switchItem);
            onToggleSwitchVisibility({
                switchId: switchItem.sourceId,
                planId: planLayout.id,
                keepSwitchesVisible: true,
            });
        }
    };

    const onKmPostSelect = (kmPostItem: LayoutKmPost) => {
        if (planLayout && kmPostItem.sourceId) {
            onToggleKmPostSelection(kmPostItem);
            onToggleKmPostVisibility({
                kmPostId: kmPostItem.sourceId,
                planId: planLayout.id,
                keepKmPostsVisible: true,
            });
        }
    };

    const subHeader =
        planHeader.source === 'PAIKANNUSPALVELU'
            ? t(`enum.PlanSource.${planHeader.source}`)
            : undefined;
    return (
        <div className={styles['geometry-plan-panel']}>
            <Accordion
                header={planHeader.name}
                subheader={subHeader}
                onToggle={onPlanToggle}
                open={isPlanOpen || openingAccordion}
                onVisibilityToggle={onPlanVisibilityToggle}
                visibility={planVisibilityState}
                onHeaderClick={onPlanHeaderSelection}
                headerSelected={selectedItems.geometryPlans?.some((id) => id === planHeader.id)}
                fetchingContent={openingAccordion || planBeingLoaded}
                eyeHidden={!!planLayoutError}
                error={
                    planLayoutError
                        ? t(planLayoutError.localizationKey, planLayoutError.params)
                        : undefined
                }>
                {!planLayoutError && planLayout && (
                    <div className={styles['geometry-plan-panel__alignments']}>
                        <Accordion
                            qaId={'geometry-plan-panel-km-posts'}
                            header={`${t('selection-panel.km-posts-title')}`}
                            onToggle={() => {
                                togglePlanKmPostsOpen({
                                    id: planHeader.id,
                                    isOpen: !isKmPostsOpen,
                                });
                            }}
                            open={isKmPostsOpen}>
                            <ul>
                                {planLayout.kmPosts.map((planKmPost) =>
                                    createKmPostRow(
                                        planLayout,
                                        planKmPost,
                                        selectedItems,
                                        effectiveVisiblePlans,
                                        forcedVisiblePlan,
                                        linkStatus,
                                        onKmPostSelect,
                                        onToggleKmPostVisibility,
                                    ),
                                )}
                                {planLayout.kmPosts.length === 0 && (
                                    <span className={styles['geometry-plan-panel__no-entities']}>
                                        {t('selection-panel.no-kmposts')}
                                    </span>
                                )}
                            </ul>
                        </Accordion>
                        <Accordion
                            qaId={'geometry-plan-panel-alignments'}
                            header={t('selection-panel.geometry-alignments-title')}
                            onToggle={() => {
                                togglePlanAlignmentsOpen({
                                    id: planHeader.id,
                                    isOpen: !isAlignmentsOpen,
                                });
                            }}
                            open={isAlignmentsOpen}>
                            <ul>
                                {planLayout.alignments.map((alignment) =>
                                    createAlignmentRow(
                                        planLayout,
                                        alignment,
                                        selectedItems,
                                        effectiveVisiblePlans,
                                        forcedVisiblePlan,
                                        linkStatus,
                                        onAlignmentSelect,
                                        onToggleAlignmentVisibility,
                                    ),
                                )}
                                {planLayout.alignments.length === 0 && (
                                    <span className={styles['geometry-plan-panel__no-entities']}>
                                        {t('selection-panel.no-alignments')}
                                    </span>
                                )}
                            </ul>
                        </Accordion>
                        <Accordion
                            qaId={'geometry-plan-panel-switches'}
                            header={t('selection-panel.switches-title')}
                            onToggle={() => {
                                togglePlanSwitchesOpen({
                                    id: planHeader.id,
                                    isOpen: !isSwitchesOpen,
                                });
                            }}
                            open={isSwitchesOpen}>
                            <ul>
                                {planLayout.switches.map((planSwitch) =>
                                    createSwitchRow(
                                        planLayout,
                                        planSwitch,
                                        selectedItems,
                                        effectiveVisiblePlans,
                                        forcedVisiblePlan,
                                        linkStatus,
                                        onSwitchSelect,
                                        onToggleSwitchVisibility,
                                    ),
                                )}
                                {planLayout.switches.length === 0 && (
                                    <span className={styles['geometry-plan-panel__no-entities']}>
                                        {t('selection-panel.no-switches')}
                                    </span>
                                )}
                            </ul>
                        </Accordion>
                    </div>
                )}
            </Accordion>
        </div>
    );
};

export const GeometryPlanPanel = React.memo(GeometryPlanPanelM);

function createKmPostRow(
    planLayout: GeometryPlanLayout,
    planKmPost: LayoutKmPost,
    selectedItems: OptionalItemCollections,
    visiblePlans: VisiblePlanLayout[],
    forcedVisiblePlan: VisiblePlanLayout | undefined,
    linkStatus: GeometryPlanLinkStatus | undefined,
    onKmPostSelect: (kmPostItem: LayoutKmPost, kmPostStatus: KmPostBadgeStatus) => void,
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void,
): React.ReactElement {
    const isKmPostSelected = selectedItems.geometryKmPostIds?.some(
        ({ geometryId }) => geometryId === planKmPost.sourceId,
    );
    const isKmPostVisible = visiblePlans.some((p) =>
        p.kmPosts.some((id) => id === planKmPost.sourceId),
    );
    const isKmPostForced =
        !!planKmPost.sourceId &&
        isGeometryForcedVisible(forcedVisiblePlan, planLayout.id, 'kmPosts', planKmPost.sourceId);

    const kmPostStatus = linkStatus?.kmPosts?.some(
        (k) => k.id === planKmPost.sourceId && k.linkedKmPosts?.length > 0,
    )
        ? KmPostBadgeStatus.LINKED
        : KmPostBadgeStatus.UNLINKED;
    return (
        <li
            key={planKmPost.id}
            className={createClassName(
                styles['geometry-plan-panel__kmpost'],
                isKmPostSelected && styles['geometry-plan-panel__kmpost--selected'],
            )}>
            <span
                className={styles['geometry-plan-panel__list-item-main-content']}
                onClick={() => onKmPostSelect(planKmPost, kmPostStatus)}>
                <KmPostBadge kmPost={planKmPost} status={kmPostStatus} />
            </span>
            <span
                className={createClassName(
                    styles['geometry-plan-panel__kmpost-visibility'],
                    isKmPostVisible && styles['geometry-plan-panel__kmpost-visibility--visible'],
                    isKmPostForced && styles['geometry-plan-panel__kmpost-visibility--forced'],
                )}>
                <Icons.Eye
                    color={IconColor.INHERIT}
                    onClick={() =>
                        isKmPostForced ||
                        (planKmPost.sourceId &&
                            onToggleKmPostVisibility({
                                kmPostId: planKmPost.sourceId,
                                planId: planLayout.id,
                            }))
                    }
                />
            </span>
        </li>
    );
}

function createAlignmentRow(
    planLayout: GeometryPlanLayout,
    alignment: PlanLayoutAlignment,
    selectedItems: OptionalItemCollections,
    visiblePlans: VisiblePlanLayout[],
    forcedVisiblePlan: VisiblePlanLayout | undefined,
    linkStatus: GeometryPlanLinkStatus | undefined,
    onAlignmentSelect: (alignment: AlignmentHeader, status: LocationTrackBadgeStatus) => void,
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void,
): React.ReactElement {
    const alignmentStatus = linkStatus?.alignments?.some(
        (s) => s.id === alignment.header.id && s.isLinked,
    )
        ? LocationTrackBadgeStatus.LINKED
        : LocationTrackBadgeStatus.UNLINKED;

    const isAlignmentSelected = selectedItems.geometryAlignmentIds?.some(
        (a) => a.geometryId === alignment.header.id,
    );
    const isAlignmentVisible = visiblePlans.some((p) => p.alignments.includes(alignment.header.id));
    const isAlignmentForced = isGeometryForcedVisible(
        forcedVisiblePlan,
        planLayout.id,
        'alignments',
        alignment.header.id,
    );

    return (
        <li
            key={alignment.header.id}
            className={createClassName(
                styles['geometry-plan-panel__alignment'],
                isAlignmentSelected && styles['geometry-plan-panel__alignment--selected'],
            )}>
            <span
                className={styles['geometry-plan-panel__list-item-main-content']}
                onClick={() => onAlignmentSelect(alignment.header, alignmentStatus)}>
                <LocationTrackBadge locationTrack={alignment.header} status={alignmentStatus} />
            </span>
            <span
                className={createClassName(
                    styles['geometry-plan-panel__alignment-visibility'],
                    isAlignmentVisible &&
                        styles['geometry-plan-panel__alignment-visibility--visible'],
                    isAlignmentForced &&
                        styles['geometry-plan-panel__alignment-visibility--forced'],
                )}>
                <Icons.Eye
                    color={IconColor.INHERIT}
                    onClick={() =>
                        isAlignmentForced ||
                        onToggleAlignmentVisibility({
                            alignmentId: alignment.header.id,
                            planId: planLayout.id,
                        })
                    }
                />
            </span>
        </li>
    );
}

function createSwitchRow(
    planLayout: GeometryPlanLayout,
    planSwitch: LayoutSwitch,
    selectedItems: OptionalItemCollections,
    visiblePlans: VisiblePlanLayout[],
    forcedVisiblePlan: VisiblePlanLayout | undefined,
    linkStatus: GeometryPlanLinkStatus | undefined,
    onSwitchSelect: (switchItem: LayoutSwitch, switchStatus: SwitchBadgeStatus) => void,
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void,
): React.ReactElement {
    const switchStatus = linkStatus?.switches?.some(
        (s) => s.id === planSwitch.sourceId && s.isLinked,
    )
        ? SwitchBadgeStatus.LINKED
        : SwitchBadgeStatus.UNLINKED;

    const isSwitchSelected = selectedItems.geometrySwitchIds?.some(
        (s) => s.geometryId === planSwitch.sourceId,
    );
    const isSwitchVisible = visiblePlans.some((p) =>
        p.switches.some((id) => id === planSwitch.sourceId),
    );
    const isSwitchForced =
        !!planSwitch.sourceId &&
        isGeometryForcedVisible(forcedVisiblePlan, planLayout.id, 'switches', planSwitch.sourceId);

    return (
        <li
            key={planSwitch.id}
            className={createClassName(
                styles['geometry-plan-panel__switch'],
                isSwitchSelected && styles['geometry-plan-panel__switch--selected'],
            )}>
            <span
                className={styles['geometry-plan-panel__list-item-main-content']}
                onClick={() => onSwitchSelect(planSwitch, switchStatus)}>
                <SwitchBadge switchItem={planSwitch} status={switchStatus} />
            </span>
            <span
                className={createClassName(
                    styles['geometry-plan-panel__switch-visibility'],
                    isSwitchVisible && styles['geometry-plan-panel__switch-visibility--visible'],
                    isSwitchForced && styles['geometry-plan-panel__switch-visibility--forced'],
                )}>
                <Icons.Eye
                    color={IconColor.INHERIT}
                    onClick={() =>
                        isSwitchForced ||
                        (planSwitch.sourceId &&
                            onToggleSwitchVisibility({
                                switchId: planSwitch.sourceId,
                                planId: planLayout.id,
                            }))
                    }
                />
            </span>
        </li>
    );
}
