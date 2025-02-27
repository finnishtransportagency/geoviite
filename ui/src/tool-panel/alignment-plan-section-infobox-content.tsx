import * as React from 'react';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { AlignmentPlanSection, PlanSectionPoint } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { useTrackLayoutAppSelector } from 'store/hooks';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { Eye } from 'geoviite-design-lib/eye/eye';
import { createClassName } from 'vayla-design-lib/utils';
import { InfoboxList, InfoboxListRow } from 'tool-panel/infobox/infobox-list';

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
};

const GeometryPlanLabel: React.FC<GeometryPlanLabelProps> = ({
    planId,
    planName,
    alignmentName,
    onGeometryClick,
}) => {
    const { t } = useTranslation();

    return (
        <div className={styles['alignment-plan-section-infobox__plan-name']}>
            {planName ? (
                planId ? (
                    <Link
                        title={`${planName}, ${alignmentName}`}
                        className={styles['alignment-plan-section-infobox__plan-link-content']}
                        onClick={onGeometryClick}>
                        {planName}
                    </Link>
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

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections, onHighlightSection }) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const visiblePlans = useTrackLayoutAppSelector((state) => state.selection.visiblePlans);

    function isVisible(planId: GeometryPlanId) {
        return visiblePlans.some((plan) => plan.id === planId);
    }

    function togglePlanVisibility(
        planId: GeometryPlanId,
        alignmentId: GeometryAlignmentId | undefined,
    ) {
        delegates.togglePlanVisibility({
            id: planId,
            switches: [],
            kmPosts: [],
            alignments: alignmentId ? [alignmentId] : [],
        });
    }

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

    const PlanVisibilityToggle: React.FC<{
        section: AlignmentPlanSection;
    }> = ({ section }) => {
        const planId = section.planId;

        return (
            <div
                className={
                    styles['alignment-plan-section-infobox__navigation-plan-visibility-toggle']
                }>
                {planId && section.isLinked && (
                    <Eye
                        visibility={isVisible(planId)}
                        onVisibilityToggle={() => {
                            togglePlanVisibility(planId, section.alignmentId);
                        }}
                    />
                )}
            </div>
        );
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
                                <PlanVisibilityToggle section={section} />
                                <TrackMeterRange start={section.start} end={section.end} />
                            </div>
                        }
                    />
                ))}
            </InfoboxList>
        </React.Fragment>
    );
};
