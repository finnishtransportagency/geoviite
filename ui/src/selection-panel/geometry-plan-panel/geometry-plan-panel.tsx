import * as React from 'react';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { getTrackLayoutPlan } from 'geometry/geometry-api';
import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutSwitch,
    MapAlignment,
} from 'track-layout/track-layout-model';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { Accordion } from 'geoviite-design-lib/accordion/accordion';
import { OpenedPlanLayout, OptionalItemCollections } from 'selection/selection-model';
import styles from './geometry-plan-panel.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import {
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
} from 'selection/selection-store';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { getPlanLinkStatus } from 'linking/linking-api';
import { GeometryPlanLinkStatus } from 'linking/linking-model';
import { useTranslation } from 'react-i18next';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { PublishType } from 'common/common-model';

type GeometryPlanProps = {
    planHeader: GeometryPlanHeader;
    onPlanHeaderSelection: (planHeader: GeometryPlanHeader) => void;
    publishType: PublishType;
    changeTimes: ChangeTimes;
    onTogglePlanVisibility: (payload: GeometryPlanLayout | null) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleAlignmentSelection: (alignment: MapAlignment) => void;
    onToggleSwitchSelection: (switchItem: LayoutSwitch) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostSelection: (kmPost: LayoutKmPost) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    selectedItems?: OptionalItemCollections;
    selectedPlanLayouts?: GeometryPlanLayout[];
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    openedPlanLayouts: OpenedPlanLayout[];
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
};

type Visibilities = {
    planHeader: boolean;
    alignments: boolean;
    switches: boolean;
    kmPosts: boolean;
};

async function fetchTrackLayoutPlan(
    planId: GeometryPlanId,
    publishType: PublishType,
    changeTimes: ChangeTimes,
) {
    return Promise.all([
        getTrackLayoutPlan(planId, changeTimes.geometryPlan, false),
        getPlanLinkStatus(planId, publishType),
    ]).then((r) => {
        return {
            planLayout: r[0],
            linkStatus: r[1],
        };
    });
}

export const GeometryPlanPanel: React.FC<GeometryPlanProps> = ({
    planHeader,
    onPlanHeaderSelection,
    publishType,
    changeTimes,
    onTogglePlanVisibility,
    onToggleAlignmentVisibility,
    onToggleAlignmentSelection,
    onToggleSwitchSelection,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    onToggleKmPostSelection,
    selectedItems,
    selectedPlanLayouts,
    togglePlanOpen,
    openedPlanLayouts,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
}: GeometryPlanProps) => {
    const { t } = useTranslation();
    const openedPlanLayout = openedPlanLayouts.find((p) => p.id === planHeader.id);
    const isPlanOpen = !!openedPlanLayout;
    const isKmPostsOpen = openedPlanLayout ? openedPlanLayout.isKmPostsOpen : false;
    const isAlignmentsOpen = openedPlanLayout ? openedPlanLayout.isAlignmentsOpen : false;
    const isSwitchesOpen = openedPlanLayout ? openedPlanLayout.isSwitchesOpen : false;
    const [planLayout, setPlanLayout] = React.useState<GeometryPlanLayout | null>();
    const [linkStatus, setLinkStatus] = React.useState<GeometryPlanLinkStatus>();
    const [openingAccordion, setOpeningAccordion] = React.useState(false);
    const [planVisible, setPlanVisible] = React.useState(false);
    const [visibilities, setVisibilities] = React.useState<Visibilities>({
        planHeader: false,
        alignments: false,
        switches: false,
        kmPosts: false,
    });

    React.useEffect(() => {
        const planHeaderSelected = !!selectedPlanLayouts?.some((p) => p.planId == planHeader.id);

        const allAlignmentsSelected = !!planLayout?.alignments.every((a) =>
            selectedPlanLayouts?.some((p) => p.alignments.some((pa) => pa.id === a.id)),
        );

        const allSwitchesSelected = !!planLayout?.switches.every((s) =>
            selectedPlanLayouts?.some((p) => p.switches.some((pa) => pa.id === s.id)),
        );

        const allKmPostsSelected = !!planLayout?.kmPosts.every((k) =>
            selectedPlanLayouts?.some((p) => p.kmPosts.some((pa) => pa.id === k.id)),
        );

        setVisibilities({
            planHeader: planHeaderSelected,
            alignments: allAlignmentsSelected,
            switches: allSwitchesSelected,
            kmPosts: allKmPostsSelected,
        });
    }, [planLayout, selectedPlanLayouts]);

    React.useEffect(() => {
        if (isPlanOpen) {
            fetchTrackLayoutPlan(planHeader.id, publishType, changeTimes).then(
                ({ planLayout, linkStatus }) => {
                    setPlanLayout(planLayout);
                    setLinkStatus(linkStatus);
                },
            );
        }
    }, [
        publishType,
        changeTimes.geometryPlan,
        changeTimes.layoutReferenceLine,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutKmPost,
    ]);

    const onPlanToggle = () => {
        if (planLayout) {
            togglePlanOpen({
                isKmPostsOpen: isKmPostsOpen,
                isSwitchesOpen: isSwitchesOpen,
                isAlignmentsOpen: isAlignmentsOpen,
                id: planLayout.planId,
                isOpen: !isPlanOpen,
            });
        } else {
            setOpeningAccordion(true);
            fetchTrackLayoutPlan(planHeader.id, publishType, changeTimes)
                .then(({ planLayout, linkStatus }) => {
                    setPlanLayout(planLayout);
                    if (planLayout) {
                        togglePlanOpen({
                            isKmPostsOpen: isKmPostsOpen,
                            isSwitchesOpen: isSwitchesOpen,
                            isAlignmentsOpen: isAlignmentsOpen,
                            id: planLayout.planId,
                            isOpen: true,
                        });
                    }
                    setLinkStatus(linkStatus);
                })
                .finally(() => setOpeningAccordion(false));
        }
    };

    const onPlanVisibilityToggle = () => {
        if (planLayout) {
            onTogglePlanVisibility(planLayout);
        } else {
            setPlanVisible(true);
            fetchTrackLayoutPlan(planHeader.id, publishType, changeTimes)
                .then(({ planLayout, linkStatus }) => {
                    setPlanLayout(planLayout);
                    setLinkStatus(linkStatus);
                    onTogglePlanVisibility(planLayout);
                })
                .finally(() => setPlanVisible(false));
        }
    };

    const onAlignmentSelect = (
        alignment: MapAlignment,
        alignmentStatus: LocationTrackBadgeStatus,
    ) => {
        if (planLayout) {
            onToggleAlignmentSelection(alignment);
            onToggleAlignmentVisibility({
                alignment: alignment,
                status: alignmentStatus,
                planLayout,
                keepAlignmentVisible: true,
            });
        }
    };

    const onSwitchSelect = (switchItem: LayoutSwitch, switchStatus: SwitchBadgeStatus) => {
        if (planLayout) {
            onToggleSwitchSelection(switchItem);
            onToggleSwitchVisibility({
                switch: switchItem,
                status: switchStatus,
                planLayout,
                keepSwitchesVisible: true,
            });
        }
    };

    const onKmPostSelect = (kmPostItem: LayoutKmPost, kmPostStatus: KmPostBadgeStatus) => {
        if (planLayout) {
            onToggleKmPostSelection(kmPostItem);
            onToggleKmPostVisibility({
                kmPost: kmPostItem,
                status: kmPostStatus,
                planLayout,
                keepKmPostsVisible: true,
            });
        }
    };

    const subHeader =
        planHeader.source == 'PAIKANNUSPALVELU'
            ? t(`enum.plan-source.${planHeader.source}`)
            : undefined;
    return (
        <div className={styles['geometry-plan-panel']}>
            <Accordion
                header={planHeader.project.name}
                subheader={subHeader}
                onToggle={onPlanToggle}
                open={isPlanOpen || openingAccordion}
                onVisibilityToggle={onPlanVisibilityToggle}
                visibility={visibilities.planHeader}
                onHeaderClick={() => onPlanHeaderSelection(planHeader)}
                headerSelected={selectedItems?.geometryPlans?.some((p) => p.id === planHeader.id)}
                fetchingContent={openingAccordion || planVisible}>
                {planLayout && (
                    <div className={styles['geometry-plan-panel__alignments']}>
                        <Accordion
                            header={`${t('selection-panel.km-posts-title')}`}
                            onToggle={() => {
                                togglePlanKmPostsOpen({
                                    id: planHeader.id,
                                    isOpen: !isKmPostsOpen,
                                });
                            }}
                            open={isKmPostsOpen}>
                            <ul>
                                {planLayout.kmPosts?.length > 0 &&
                                    planLayout.kmPosts.map((planKmPost) => {
                                        const isKmPostSelected =
                                            selectedItems?.geometryKmPosts?.some(
                                                (k) => k.geometryItem.id === planKmPost.id,
                                            );
                                        const isKmPostVisible = selectedPlanLayouts?.some((p) =>
                                            p.kmPosts.some((k) => k.id === planKmPost.id),
                                        );

                                        const kmPostStatus = linkStatus?.kmPosts?.some(
                                            (k) =>
                                                k.id == planKmPost.sourceId &&
                                                k.linkedKmPosts?.length > 0,
                                        )
                                            ? KmPostBadgeStatus.LINKED
                                            : KmPostBadgeStatus.UNLINKED;

                                        return (
                                            <li
                                                key={planKmPost.id}
                                                className={createClassName(
                                                    styles['geometry-plan-panel__kmpost'],
                                                    isKmPostSelected &&
                                                        styles[
                                                            'geometry-plan-panel__kmpost--selected'
                                                        ],
                                                )}>
                                                <span
                                                    className={
                                                        styles[
                                                            'geometry-plan-panel__list-item-main-content'
                                                        ]
                                                    }
                                                    onClick={() =>
                                                        onKmPostSelect(planKmPost, kmPostStatus)
                                                    }>
                                                    <KmPostBadge
                                                        kmPost={planKmPost}
                                                        status={kmPostStatus}
                                                    />
                                                </span>

                                                <span
                                                    className={createClassName(
                                                        styles[
                                                            'geometry-plan-panel__kmpost-visibility'
                                                        ],
                                                        isKmPostVisible &&
                                                            styles[
                                                                'geometry-plan-panel__kmpost-visibility--visible'
                                                            ],
                                                    )}>
                                                    <Icons.Eye
                                                        color={IconColor.INHERIT}
                                                        onClick={() =>
                                                            isKmPostSelected ||
                                                            onToggleKmPostVisibility({
                                                                kmPost: planKmPost,
                                                                status: kmPostStatus,
                                                                planLayout: planLayout,
                                                            })
                                                        }
                                                    />
                                                </span>
                                            </li>
                                        );
                                    })}
                                {planLayout.kmPosts.length == 0 && (
                                    <span className={styles['geometry-plan-panel__no-entities']}>
                                        {t('selection-panel.no-kmposts')}
                                    </span>
                                )}
                            </ul>
                        </Accordion>
                        <Accordion
                            header={t('selection-panel.geometry-alignments-title')}
                            onToggle={() => {
                                togglePlanAlignmentsOpen({
                                    id: planHeader.id,
                                    isOpen: !isAlignmentsOpen,
                                });
                            }}
                            open={isAlignmentsOpen}>
                            <ul>
                                {planLayout.alignments.length > 0 &&
                                    planLayout.alignments.map((alignment) => {
                                        const alignmentStatus = linkStatus?.alignments?.some(
                                            (s) => s.id == alignment.sourceId && s.isLinked,
                                        )
                                            ? LocationTrackBadgeStatus.LINKED
                                            : LocationTrackBadgeStatus.UNLINKED;

                                        const isAlignmentSelected =
                                            selectedItems?.geometryAlignments?.some(
                                                (a) => a.geometryItem.id === alignment.id,
                                            );
                                        const isAlignmentVisible = selectedPlanLayouts?.some((p) =>
                                            p.alignments.some((a) => a.id === alignment.id),
                                        );

                                        return (
                                            <li
                                                key={alignment.id}
                                                className={createClassName(
                                                    styles['geometry-plan-panel__alignment'],
                                                    isAlignmentSelected &&
                                                        styles[
                                                            'geometry-plan-panel__alignment--selected'
                                                        ],
                                                )}>
                                                <span
                                                    className={
                                                        styles[
                                                            'geometry-plan-panel__list-item-main-content'
                                                        ]
                                                    }
                                                    onClick={() =>
                                                        onAlignmentSelect(
                                                            alignment,
                                                            alignmentStatus,
                                                        )
                                                    }>
                                                    <LocationTrackBadge
                                                        locationTrack={alignment}
                                                        status={alignmentStatus}
                                                    />
                                                </span>

                                                <span
                                                    className={createClassName(
                                                        styles[
                                                            'geometry-plan-panel__alignment-visibility'
                                                        ],
                                                        isAlignmentVisible &&
                                                            styles[
                                                                'geometry-plan-panel__alignment-visibility--visible'
                                                            ],
                                                    )}>
                                                    <Icons.Eye
                                                        color={IconColor.INHERIT}
                                                        onClick={() =>
                                                            onToggleAlignmentVisibility({
                                                                alignment: alignment,
                                                                status: alignmentStatus,
                                                                planLayout: planLayout,
                                                            })
                                                        }
                                                    />
                                                </span>
                                            </li>
                                        );
                                    })}
                                {planLayout.alignments.length == 0 && (
                                    <span className={styles['geometry-plan-panel__no-entities']}>
                                        {t('selection-panel.no-alignments')}
                                    </span>
                                )}
                            </ul>
                        </Accordion>
                        <Accordion
                            header={t('selection-panel.switches-title')}
                            onToggle={() => {
                                togglePlanSwitchesOpen({
                                    id: planHeader.id,
                                    isOpen: !isSwitchesOpen,
                                });
                            }}
                            open={isSwitchesOpen}>
                            <ul>
                                {planLayout.switches.length > 0 &&
                                    planLayout.switches.map((planSwitch) => {
                                        const switchStatus = linkStatus?.switches?.some(
                                            (s) => s.id == planSwitch.sourceId && s.isLinked,
                                        )
                                            ? SwitchBadgeStatus.LINKED
                                            : SwitchBadgeStatus.UNLINKED;

                                        const isSwitchSelected =
                                            selectedItems?.geometrySwitches?.some(
                                                (s) => s.geometryItem.id === planSwitch.id,
                                            );
                                        const isSwitchVisible = selectedPlanLayouts?.some((p) =>
                                            p.switches.some((a) => a.id === planSwitch.id),
                                        );

                                        return (
                                            <li
                                                key={planSwitch.id}
                                                className={createClassName(
                                                    styles['geometry-plan-panel__switch'],
                                                    isSwitchSelected &&
                                                        styles[
                                                            'geometry-plan-panel__switch--selected'
                                                        ],
                                                )}>
                                                <span
                                                    className={
                                                        styles[
                                                            'geometry-plan-panel__list-item-main-content'
                                                        ]
                                                    }
                                                    onClick={() =>
                                                        onSwitchSelect(planSwitch, switchStatus)
                                                    }>
                                                    <SwitchBadge
                                                        switchItem={planSwitch}
                                                        status={switchStatus}
                                                    />
                                                </span>

                                                <span
                                                    className={createClassName(
                                                        styles[
                                                            'geometry-plan-panel__switch-visibility'
                                                        ],
                                                        isSwitchVisible &&
                                                            styles[
                                                                'geometry-plan-panel__switch-visibility--visible'
                                                            ],
                                                    )}>
                                                    <Icons.Eye
                                                        color={IconColor.INHERIT}
                                                        onClick={() =>
                                                            isSwitchSelected ||
                                                            onToggleSwitchVisibility({
                                                                switch: planSwitch,
                                                                status: switchStatus,
                                                                planLayout: planLayout,
                                                            })
                                                        }
                                                    />
                                                </span>
                                            </li>
                                        );
                                    })}
                                {planLayout.switches.length == 0 && (
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
