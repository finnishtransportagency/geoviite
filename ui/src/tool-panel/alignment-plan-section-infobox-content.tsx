import * as React from 'react';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { AlignmentPlanSection, PlanSectionPoint } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import {
    GeometryPlanLayout,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { Eye, VisibilityState } from 'geoviite-design-lib/eye/eye';
import { createClassName } from 'vayla-design-lib/utils';
import { InfoboxList, InfoboxListRow } from 'tool-panel/infobox/infobox-list';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { getTrackLayoutPlans } from 'geometry/geometry-api';
import {
    aggregateVisibility,
    isPlanFullyVisible,
    wholePlanVisibility,
} from 'selection/selection-store';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';

const ErrorFragment: React.FC<{ message?: string }> = ({ message = '' }) => (
    <span title={message} className={styles['alignment-plan-section-infobox__no-plan-icon']}>
        <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
    </span>
);

type HighlightedItemBase = {
    startM: number;
    endM: number;
};

export type HighlightedLocationTrack = {
    type: 'LOCATION_TRACK';
    id: LocationTrackId;
} & HighlightedItemBase;

export type HighlightedReferenceLine = {
    type: 'REFERENCE_LINE';
    id: LayoutTrackNumberId;
} & HighlightedItemBase;

export type HighlightedAlignment = HighlightedLocationTrack | HighlightedReferenceLine;

export type OnHighlightSection = (section: undefined | { startM: number; endM: number }) => void;

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentPlanSection[];
    onHighlightSection: OnHighlightSection;
};

type GeometryPlanLabelProps = {
    planId: GeometryPlanId | undefined;
    planName: string | undefined;
    alignmentName: string | undefined;
    onGeometryClick: () => void;
    disabled?: boolean;
};

const GeometryPlanLabel: React.FC<GeometryPlanLabelProps> = ({
    planId,
    planName,
    alignmentName,
    onGeometryClick,
    disabled,
}) => {
    const { t } = useTranslation();

    return (
        <div className={styles['alignment-plan-section-infobox__plan-name']}>
            {planName ? (
                planId ? (
                    <AnchorLink
                        title={`${planName}, ${alignmentName}`}
                        className={styles['alignment-plan-section-infobox__plan-link-content']}
                        disabled={disabled}
                        onClick={onGeometryClick}>
                        {planName}
                    </AnchorLink>
                ) : (
                    <span title={`${planName}, ${alignmentName}`}>{planName}</span>
                )
            ) : (
                t('tool-panel.alignment-plan-sections.no-plan')
            )}
        </div>
    );
};

type TrackMeterRangeProps = {
    start: PlanSectionPoint | undefined;
    end: PlanSectionPoint | undefined;
};

const TrackMeterRange: React.FC<TrackMeterRangeProps> = ({ start, end }) => {
    const { t } = useTranslation();

    const TrackMeterOrError: React.FC<{
        point: PlanSectionPoint | undefined;
    }> = ({ point }) => {
        return (
            <React.Fragment>
                {point ? (
                    <NavigableTrackMeter
                        trackMeter={point.address}
                        location={point.location}
                        displayDecimals={false}
                    />
                ) : (
                    <ErrorFragment
                        message={t('tool-panel.alignment-plan-sections.geocoding-failed')}
                    />
                )}
            </React.Fragment>
        );
    };

    return (
        <div className={styles['alignment-plan-section-infobox__meters']}>
            <TrackMeterOrError point={start} />
            <TrackMeterOrError point={end} />
        </div>
    );
};

const PlanVisibilityToggle: React.FC<{
    visibility: VisibilityState;
    disabled?: boolean;
    onVisibilityToggle: () => void;
}> = ({ visibility, disabled, onVisibilityToggle }) => (
    <div className={styles['alignment-plan-section-infobox__navigation-plan-visibility-toggle']}>
        <Eye visibility={visibility} disabled={disabled} onVisibilityToggle={onVisibilityToggle} />
    </div>
);

const AlignmentPlanSectionInfoboxContentM: React.FC<AlignmentPlanSectionInfoboxContentProps> = ({
    sections,
    onHighlightSection,
}) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const visiblePlans = useTrackLayoutAppSelector((state) => state.selection.visiblePlans);
    const linkingState = useTrackLayoutAppSelector((state) => state.linkingState);
    const splittingState = useTrackLayoutAppSelector((state) => state.splittingState);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const isLinkingOrSplitting = !!linkingState || !!splittingState;

    const [planLayouts, setPlanLayouts] = React.useState<Map<GeometryPlanId, GeometryPlanLayout>>(
        new Map(),
    );

    // A plan absent from visiblePlans is definitely fully hidden, so full layouts (needed to tell
    // "every item visible" from "some items visible") are only fetched for plans that have at
    // least one visible item. The fetch is cache-backed, so this is cheap if something else (e.g.
    // the plan selection panel) already loaded the same plan.
    React.useEffect(() => {
        const visiblePlanIds = deduplicate(
            sections
                .map((section) => section.planId)
                .filter(filterNotEmpty)
                .filter((planId) => visiblePlans.some((plan) => plan.id === planId)),
        );
        const missingPlanIds = visiblePlanIds.filter((planId) => !planLayouts.has(planId));
        if (missingPlanIds.length > 0) {
            getTrackLayoutPlans(missingPlanIds, changeTimes.geometryPlan).then((results) => {
                setPlanLayouts((previous) => {
                    const next = new Map(previous);
                    results.forEach((result) => {
                        if (result.layout) next.set(result.layout.id, result.layout);
                    });
                    return next;
                });
            });
        }
    }, [sections, visiblePlans, changeTimes.geometryPlan, planLayouts]);

    const planVisibility = (planId: GeometryPlanId | undefined): VisibilityState => {
        const entry = planId && visiblePlans.find((plan) => plan.id === planId);
        return entry
            ? aggregateVisibility(true, isPlanFullyVisible(entry, planLayouts.get(planId)))
            : 'hidden';
    };

    const setPlanVisible = (planId: GeometryPlanId) => {
        if (planVisibility(planId) === 'visible') {
            const entry = visiblePlans.find((plan) => plan.id === planId);
            if (entry) delegates.setPlanVisibility({ plan: entry, visible: false });
        } else {
            const cachedLayout = planLayouts.get(planId);
            if (cachedLayout) {
                delegates.setPlanVisibility({
                    plan: wholePlanVisibility(cachedLayout),
                    visible: true,
                });
            } else {
                getTrackLayoutPlans([planId], changeTimes.geometryPlan).then(([result]) => {
                    if (result?.layout) {
                        delegates.setPlanVisibility({
                            plan: wholePlanVisibility(result.layout),
                            visible: true,
                        });
                    }
                });
            }
        }
    };

    const startSectionHighlight = (section: AlignmentPlanSection) => {
        section.start &&
            section.end &&
            onHighlightSection({
                startM: section.start.m,
                endM: section.end.m,
            });
    };

    const endSectionHighlight = () => onHighlightSection(undefined);

    const selectGeometry = (planId: GeometryPlanId | undefined) => {
        if (planId) {
            delegates.onSelect({
                geometryPlans: [planId],
            });
            delegates.setToolPanelTab({
                id: planId,
                type: 'GEOMETRY_PLAN',
            });
        }
    };

    return (
        <React.Fragment>
            <InfoboxList>
                {sections.map((section: AlignmentPlanSection) => (
                    <InfoboxListRow
                        key={section.id}
                        onMouseOver={() => startSectionHighlight(section)}
                        onMouseOut={() => endSectionHighlight()}
                        label={
                            <div className="infobox__list-cell">
                                {section.planName && !section.isLinked && <ErrorFragment />}
                                <GeometryPlanLabel
                                    planId={section.planId}
                                    planName={section.planName}
                                    alignmentName={section.alignmentName}
                                    onGeometryClick={() => selectGeometry(section.planId)}
                                    disabled={isLinkingOrSplitting}
                                />
                            </div>
                        }
                        content={
                            <div
                                className={createClassName(
                                    'infobox__list-cell',
                                    'infobox__list-cell--strong',
                                    styles['alignment-plan-section-infobox__navigation'],
                                )}>
                                {section.planId && section.isLinked && (
                                    <PlanVisibilityToggle
                                        visibility={planVisibility(section.planId)}
                                        disabled={isLinkingOrSplitting}
                                        onVisibilityToggle={() => {
                                            const planId = section.planId;
                                            if (planId) setPlanVisible(planId);
                                        }}
                                    />
                                )}
                                <TrackMeterRange start={section.start} end={section.end} />
                            </div>
                        }
                    />
                ))}
            </InfoboxList>
        </React.Fragment>
    );
};

export const AlignmentPlanSectionInfoboxContent = React.memo(AlignmentPlanSectionInfoboxContentM);
