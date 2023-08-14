import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { AlignmentPlanSection } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';

type HighlightedItemBase = {
    startM: number;
    endM: number;
};

type HighlightedLocationTrack = {
    type: 'LOCATION_TRACK';
    id: LocationTrackId;
} & HighlightedItemBase;

type HighlightedReferenceLine = {
    type: 'REFERENCE_LINE';
    id: LayoutTrackNumberId;
} & HighlightedItemBase;

export type HighlightedAlignment = HighlightedLocationTrack | HighlightedReferenceLine;

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentPlanSection[];
    onHighlightItem: (item: HighlightedAlignment | undefined) => void;
    id: LocationTrackId | LayoutTrackNumberId;
    type: 'LOCATION_TRACK' | 'REFERENCE_LINE';
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections, type, id, onHighlightItem }) => {
    const { t } = useTranslation();

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    const errorFragment = (errorMessage = '') => (
        <span
            title={errorMessage}
            className={styles['alignment-plan-section-infobox__no-plan-icon']}>
            <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
        </span>
    );

    return (
        <React.Fragment>
            {sections.map((section) => (
                <span
                    key={section.id}
                    onMouseOver={() => {
                        section.start &&
                            section.end &&
                            onHighlightItem({
                                id,
                                type,
                                startM: section.start?.m,
                                endM: section.end?.m,
                            });
                    }}
                    onMouseOut={() => {
                        onHighlightItem(undefined);
                    }}>
                    <InfoboxField
                        label={
                            <span className={styles['alignment-plan-section-infobox__plan-name']}>
                                {section.planName ? (
                                    section.planId ? (
                                        <React.Fragment>
                                            {!section.isLinked && errorFragment()}{' '}
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
                                                title={`${section.planName} (${section.alignmentName})`}>
                                                {section.planName}
                                            </Link>
                                        </React.Fragment>
                                    ) : (
                                        <span
                                            title={`${section.planName} (${section.alignmentName})`}>
                                            {errorFragment()} {section.planName}
                                        </span>
                                    )
                                ) : (
                                    t('tool-panel.alignment-plan-sections.no-plan')
                                )}
                            </span>
                        }
                        value={
                            <div className={styles['alignment-plan-section-infobox__meters']}>
                                <span>
                                    {section.start?.address
                                        ? formatTrackMeterWithoutMeters(section.start.address)
                                        : errorFragment(
                                              t(
                                                  'tool-panel.alignment-plan-sections.geocoding-failed',
                                              ),
                                          )}
                                </span>{' '}
                                <span>
                                    {section.end?.address
                                        ? formatTrackMeterWithoutMeters(section.end.address)
                                        : errorFragment(
                                              t(
                                                  'tool-panel.alignment-plan-sections.geocoding-failed',
                                              ),
                                          )}
                                </span>
                            </div>
                        }
                    />
                </span>
            ))}
        </React.Fragment>
    );
};
