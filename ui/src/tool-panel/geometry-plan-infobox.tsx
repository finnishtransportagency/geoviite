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
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';
import { differenceInYears } from 'date-fns';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useAppNavigate } from 'common/navigate';
import { Link } from 'vayla-design-lib/link/link';
import { INFRAMODEL_URI } from 'infra-model/infra-model-api';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getCoordinateSystem } from 'common/common-api';
import { useLoader } from 'utils/react-utils';
import CoordinateSystem from 'geoviite-design-lib/coordinate-system/coordinate-system';
import MeasurementMethod from 'geoviite-design-lib/measurement-method/measurement-method';

type GeometryPlanInfoboxProps = {
    planHeader: GeometryPlanHeader;
};

const GeometryPlanInfobox: React.FC<GeometryPlanInfoboxProps> = ({
    planHeader,
}: GeometryPlanInfoboxProps) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();
    const coordinateSystemModel = useLoader(
        () =>
            (planHeader.units.coordinateSystemSrid &&
                getCoordinateSystem(planHeader.units.coordinateSystemSrid)) ||
            undefined,
        [planHeader.units.coordinateSystemSrid],
    );

    const planTime = toDateOrUndefined(planHeader.planTime);
    const age =
        planTime === undefined
            ? ''
            : (() => {
                  const ageYears = differenceInYears(new Date(), planTime);
                  return (
                      formatDateShort(planTime) +
                      ' (' +
                      (ageYears === 0
                          ? t('tool-panel.geometry-plan.plan-age-under-1-year')
                          : t('tool-panel.geometry-plan.plan-age-content', { ageYears })) +
                      ')'
                  );
              })();

    const generalInfobox = (
        <Infobox
            title={t('tool-panel.geometry-plan.general-title')}
            qa-id="geometry-plan-general-infobox">
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.geometry-plan.message')}
                    value={planHeader.message}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.author')}
                    value={planHeader.author}
                />
                <InfoboxField label={t('tool-panel.geometry-plan.project')}>
                    <span className={styles['geometry-plan-tool-panel__long']}>
                        {planHeader.project.name}
                    </span>
                </InfoboxField>
                <InfoboxField label={t('tool-panel.geometry-plan.file')}>
                    <span className={styles['geometry-plan-tool-panel__long']}>
                        {planHeader.fileName}
                    </span>
                </InfoboxField>
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
                    label={t('tool-panel.geometry-plan.start-km')}
                    value={planHeader.kmNumberRange?.min}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.end-km')}
                    value={planHeader.kmNumberRange?.max}
                />
            </InfoboxContent>
        </Infobox>
    );

    const qualityInfobox = (
        <Infobox
            title={t('tool-panel.geometry-plan.quality-title')}
            qa-id="geometry-plan-quality-infobox">
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.geometry-plan.source')}
                    value={planHeader.source}
                />
                <InfoboxField label={t('tool-panel.geometry-plan.plan-age')} value={age} />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.measurement-method')}
                    value={<MeasurementMethod method={planHeader.measurementMethod} />}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.coordinate-system')}
                    value={
                        coordinateSystemModel && (
                            <CoordinateSystem coordinateSystem={coordinateSystemModel} />
                        )
                    }
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.has-vertical-geometry')}
                    value={planHeader.hasProfile ? t('yes') : t('no')}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.has-cant')}
                    value={planHeader.hasCant ? t('yes') : t('no')}
                />
                <InfoboxField
                    label={t('tool-panel.geometry-plan.vertical-coordinate-system')}
                    value={planHeader.units.verticalCoordinateSystem}
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

    return (
        <>
            {generalInfobox}
            {qualityInfobox}
        </>
    );
};

export default React.memo(GeometryPlanInfobox);
