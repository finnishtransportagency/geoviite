import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { GeometryPlanId } from 'geometry/geometry-model';
import { AlignmentSectionByPlan } from 'track-layout/layout-location-track-api';
import { useAppNavigate } from 'common/navigate';

type AlignmentPlanSectionInfoboxContentProps = {
    sections: AlignmentSectionByPlan[];
};

export const AlignmentPlanSectionInfoboxContent: React.FC<
    AlignmentPlanSectionInfoboxContentProps
> = ({ sections }) => {
    const navigate = useAppNavigate();
    const onSelectPlan = (planId: GeometryPlanId) => navigate('inframodel-edit', planId);

    return (
        <React.Fragment>
            {sections.map((section) => (
                <InfoboxField
                    key={formatTrackMeter(section.startAddress)}
                    label={
                        <span className={styles['location-track-geometry-infobox__plan-name']}>
                            {section.planId ? (
                                <Link
                                    onClick={() => section.planId && onSelectPlan(section.planId)}
                                    title={section.planName}>
                                    {section.planName}
                                </Link>
                            ) : (
                                'Ei suunnitelmaa'
                            )}
                        </span>
                    }
                    value={
                        <div>
                            <span>{formatTrackMeterWithoutMeters(section.startAddress)}</span>{' '}
                            <span>{formatTrackMeterWithoutMeters(section.endAddress)}</span>
                        </div>
                    }
                />
            ))}
        </React.Fragment>
    );
};
