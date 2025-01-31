import * as React from 'react';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { AlignmentPlanSection } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { useTrackLayoutAppSelector } from 'store/hooks';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';

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

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections, onHighlightSection }) => {
    const { t } = useTranslation();

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

    const errorFragment = (errorMessage = '') => (
        <span
            title={errorMessage}
            className={styles['alignment-plan-section-infobox__no-plan-icon']}>
            <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
        </span>
    );

    return (
        <React.Fragment>
            <div className="infobox__list">
                {sections.map((section: AlignmentPlanSection) => (
                    <div
                        className="infobox__list-row"
                        key={section.id}
                        onMouseOver={() => {
                            section.start &&
                                section.end &&
                                onHighlightSection({
                                    startM: section.start.m,
                                    endM: section.end.m,
                                });
                        }}
                        onMouseOut={() => {
                            onHighlightSection(undefined);
                        }}>
                        {section.planName && !section.isLinked && (
                            <div className="infobox__list-cell">{errorFragment()}</div>
                        )}
                        <div className="infobox__list-cell infobox__list-cell--stretch infobox__list-cell--label">
                            <div className={styles['alignment-plan-section-infobox__plan-name']}>
                                {section.planName ? (
                                    section.planId ? (
                                        <React.Fragment>
                                            <Link
                                                onClick={() => {
                                                    if (section.planId) {
                                                        delegates.onSelect({
                                                            geometryPlans: [section.planId],
                                                        });
                                                        delegates.setToolPanelTab({
                                                            id: section.planId,
                                                            type: 'GEOMETRY_PLAN',
                                                        });
                                                    }
                                                }}
                                                title={`${section.planName}, ${section.alignmentName}`}>
                                                <span
                                                    className={
                                                        styles[
                                                            'alignment-plan-section-infobox__plan-link-content'
                                                        ]
                                                    }>
                                                    {section.planName}
                                                </span>
                                            </Link>
                                        </React.Fragment>
                                    ) : (
                                        <span
                                            title={`${section.planName}, ${section.alignmentName}`}>
                                            {section.planName}
                                        </span>
                                    )
                                ) : (
                                    t('tool-panel.alignment-plan-sections.no-plan')
                                )}
                            </div>
                        </div>
                        <div className="infobox__list-cell">
                            {section.planId && section.isLinked && (
                                <div
                                    onClick={() =>
                                        section.planId &&
                                        togglePlanVisibility(section.planId, section.alignmentId)
                                    }
                                    className="alignment-plan-section-infobox__show-plan-icon"
                                    title={`${section.planName}, ${section.alignmentName}`}>
                                    {isVisible(section.planId) ? (
                                        <Icons.Eye />
                                    ) : (
                                        <Icons.Eye color={IconColor.INHERIT} />
                                    )}
                                </div>
                            )}
                        </div>
                        <div className="infobox__list-cell">
                            <div className={styles['alignment-plan-section-infobox__meters']}>
                                <span>
                                    {section.start ? (
                                        <NavigableTrackMeter
                                            trackMeter={section?.start?.address}
                                            location={section?.start?.location}
                                            displayDecimals={false}
                                        />
                                    ) : (
                                        errorFragment(
                                            t(
                                                'tool-panel.alignment-plan-sections.geocoding-failed',
                                            ),
                                        )
                                    )}
                                </span>{' '}
                                <span>
                                    {section.end ? (
                                        <NavigableTrackMeter
                                            trackMeter={section?.end?.address}
                                            location={section?.end?.location}
                                            displayDecimals={false}
                                        />
                                    ) : (
                                        errorFragment(
                                            t(
                                                'tool-panel.alignment-plan-sections.geocoding-failed',
                                            ),
                                        )
                                    )}
                                </span>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </React.Fragment>
    );
};
