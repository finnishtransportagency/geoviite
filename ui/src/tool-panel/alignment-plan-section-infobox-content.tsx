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

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentPlanSection[];
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections }) => {
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
                <InfoboxField
                    key={section.id}
                    label={
                        <span className={styles['alignment-plan-section-infobox__plan-name']}>
                            {section.planName ? (
                                section.planId ? (
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
                                        title={section.planName}>
                                        {!section.isLinked && errorFragment()}
                                        {section.planName}
                                    </Link>
                                ) : (
                                    <span title={section.planName}>
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
                                          t('tool-panel.alignment-plan-sections.geocoding-failed'),
                                      )}
                            </span>{' '}
                            <span>
                                {section.endAddress
                                    ? formatTrackMeterWithoutMeters(section.endAddress)
                                    : errorFragment(
                                          t('tool-panel.alignment-plan-sections.geocoding-failed'),
                                      )}
                            </span>
                        </div>
                    }
                />
            ))}
        </React.Fragment>
    );
};
