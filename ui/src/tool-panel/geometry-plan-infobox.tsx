import * as React from 'react';
import styles from './geometry-plan-infobox.scss';

import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { formatDateShort, toDateOrUndefined } from 'utils/date-utils';
import PlanPhase from 'geoviite-design-lib/plan-phase/plan-phase';
import PlanDecisionPhase from 'geoviite-design-lib/plan-decision/plan-decision-phase';
import MeasurementMethod from 'geoviite-design-lib/measurement-method/measurement-method';
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';
import { GeometryPlanLink } from './geometry-plan-link';
import { differenceInYears } from 'date-fns';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useAppNavigate } from 'common/navigate';
import { Link } from 'vayla-design-lib/link/link';
import { INFRAMODEL_URI } from 'infra-model/infra-model-api';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';

type GeometryPlanInfoboxProps = {
    planHeader: GeometryPlanHeader;
};

const ageInYears = (date: Date): string => {
    const diff = differenceInYears(new Date(), date);
    return (diff === 0 ? 'alle 1' : diff) + ' v sitten';
};

const GeometryPlanInfobox: React.FC<GeometryPlanInfoboxProps> = ({
    planHeader,
}: GeometryPlanInfoboxProps) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const planTime = toDateOrUndefined(planHeader.planTime);
    const age = planTime && ageInYears(planTime);

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
                <InfoboxField label={t('tool-panel.geometry-plan.plan-age')} value={age} />
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
                <InfoboxField
                    label={t('tool-panel.geometry-plan.has-vertical-geometry')}
                    value={planHeader.hasProfile ? t('yes') : t('no')}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.has-cant')}
                    value={planHeader.hasCant ? t('yes') : t('no')}
                />
                <InfoboxButtons>
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => navigate('inframodel-edit', planHeader.id)}>
                        {t('tool-panel.geometry-plan.open-inframodel')}
                    </Button>
                    {planHeader.source !== 'PAIKANNUSPALVELU' && (
                        <Link
                            className={styles['geometry-plan-tool-panel__download-link']}
                            href={`${INFRAMODEL_URI}/${planHeader.id}/file`}>
                            <Button size={ButtonSize.SMALL} variant={ButtonVariant.SECONDARY}>
                                <Icons.Download size={IconSize.SMALL} />{' '}
                                {t('tool-panel.geometry-plan.download-file')}
                            </Button>
                        </Link>
                    )}
                </InfoboxButtons>
            </InfoboxContent>
        </Infobox>
    );
};

export default React.memo(GeometryPlanInfobox);
