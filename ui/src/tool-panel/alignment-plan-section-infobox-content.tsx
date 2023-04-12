import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { AlignmentPlanSection } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { toolPanelPlanTabId } from 'tool-panel/tool-panel';
import { GeometryPlanId } from 'geometry/geometry-model';

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentPlanSection[];
    onHoverOverGeometryPlan: (planId: GeometryPlanId | undefined) => void;
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections, onHoverOverGeometryPlan }) => {
    const { t } = useTranslation();

    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);

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
                <div
                    key={section.id}
                    onMouseLeave={() => {
                        onHoverOverGeometryPlan(undefined);
                    }}
                    onMouseOver={() => {
                        section.planId && onHoverOverGeometryPlan(section.planId);
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
                                                        delegates.setToolPanelTab(
                                                            toolPanelPlanTabId(section.planId),
                                                        );
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
                                    {section.startAddress
                                        ? formatTrackMeterWithoutMeters(section.startAddress)
                                        : errorFragment(
                                              t(
                                                  'tool-panel.alignment-plan-sections.geocoding-failed',
                                              ),
                                          )}
                                </span>{' '}
                                <span>
                                    {section.endAddress
                                        ? formatTrackMeterWithoutMeters(section.endAddress)
                                        : errorFragment(
                                              t(
                                                  'tool-panel.alignment-plan-sections.geocoding-failed',
                                              ),
                                          )}
                                </span>
                            </div>
                        }
                    />
                </div>
            ))}
        </React.Fragment>
    );
};
