import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from 'tool-panel/track-number/alignment-plan-section-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { GeometryPlanId } from 'geometry/geometry-model';
import { AlignmentSectionByPlan } from 'track-layout/layout-location-track-api';
import { useAppNavigate } from 'common/navigate';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentSectionByPlan[];
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections }) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();
    const onSelectPlan = (planId: GeometryPlanId) => navigate('inframodel-edit', planId);

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
                                        onClick={() =>
                                            section.planId && onSelectPlan(section.planId)
                                        }
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
                    }
                    value={
                        <div>
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
