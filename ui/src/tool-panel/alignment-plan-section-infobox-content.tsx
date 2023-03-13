import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { AlignmentSectionByPlan } from 'track-layout/layout-location-track-api';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { toolPanelPlanTabId } from 'tool-panel/tool-panel';
import { GeometryPlanId } from 'geometry/geometry-model';
import { getTrackLayoutPlan } from 'geometry/geometry-api';

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentSectionByPlan[];
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections }) => {
    const { t } = useTranslation();

    const store = useTrackLayoutAppSelector((state) => state.trackLayout);
    const visiblePlans = store.selection.planLayouts;

    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);

    function isVisible(planId: GeometryPlanId) {
        return visiblePlans.some((plan) => plan.planId == planId);
    }

    function togglePlanVisibility(planId: GeometryPlanId) {
        getTrackLayoutPlan(planId, store.changeTimes.geometryPlan, false).then((planLayout) => {
            delegates.togglePlanVisibility(planLayout);
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
            {sections.map((section) => (
                <InfoboxField
                    key={section.id}
                    label={
                        <React.Fragment>
                            <span className={styles['alignment-plan-section-infobox__plan-name']}>
                                {section.planName ? (
                                    section.planId ? (
                                        <Link
                                            onClick={() => {
                                                if (section.planId) {
                                                    delegates.setToolPanelTab(
                                                        toolPanelPlanTabId(section.planId),
                                                    );
                                                    delegates.onSelect({
                                                        geometryPlans: [section.planId],
                                                    });
                                                }
                                            }}
                                            title={section.planName}>
                                            {section.planName}
                                        </Link>
                                    ) : (
                                        <span>
                                            {errorFragment()} {section.planName}
                                        </span>
                                    )
                                ) : (
                                    t('tool-panel.alignment-plan-sections.no-plan')
                                )}
                            </span>
                            {section.planId && (
                                <div
                                    onClick={() =>
                                        section.planId && togglePlanVisibility(section.planId)
                                    }
                                    className="alignment-plan-section-infobox__plan-tools">
                                    {isVisible(section.planId) ? (
                                        <Icons.Eye />
                                    ) : (
                                        <Icons.Eye color={IconColor.INHERIT} />
                                    )}
                                </div>
                            )}
                        </React.Fragment>
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
