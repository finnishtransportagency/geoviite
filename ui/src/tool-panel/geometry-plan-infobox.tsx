import * as React from 'react';

import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { formatDateShort } from 'utils/date-utils';
import PlanPhase from 'geoviite-design-lib/plan-phase/plan-phase';
import PlanDecisionPhase from 'geoviite-design-lib/plan-decision/plan-decision-phase';
import MeasurementMethod from 'geoviite-design-lib/measurement-method/measurement-method';
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';
import { GeometryPlanLink } from './geometry-plan-link';

type GeometryPlanInfoboxProps = {
    planHeader: GeometryPlanHeader;
};

const GeometryPlanInfobox: React.FC<GeometryPlanInfoboxProps> = ({
    planHeader,
}: GeometryPlanInfoboxProps) => {
    const { t } = useTranslation();

    return (
        <Infobox title={t('tool-panel.geometry-plan.title')} qa-id="geometry-plan-infobox">
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.geometry-plan.project')}
                    value={planHeader.project.name}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.created')}
                    value={formatDateShort(planHeader.uploadTime)}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.source')}
                    value={t(`enum.plan-source.${planHeader.source}`)}
                />
                {planHeader.linkedAsPlanId && (
                    <InfoboxField
                        label={t('tool-panel.geometry-plan.linked-as')}
                        value={<GeometryPlanLink planId={planHeader.linkedAsPlanId} />}
                    />
                )}
                <InfoboxField
                    label={t('tool-panel.geometry-plan.measurement-method')}
                    value={<MeasurementMethod method={planHeader.measurementMethod} />}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.phase')}
                    value={<PlanPhase phase={planHeader.planPhase} />}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.decision')}
                    value={<PlanDecisionPhase decision={planHeader.decisionPhase} />}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.track-number')}
                    value={
                        planHeader.trackNumberId && (
                            <TrackNumberLink
                                publishType={'DRAFT'}
                                trackNumberId={planHeader.trackNumberId}
                            />
                        )
                    }
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.coordinate-system')}
                    value={planHeader.units.coordinateSystemSrid}
                />
            </InfoboxContent>
        </Infobox>
    );
};

export default React.memo(GeometryPlanInfobox);
