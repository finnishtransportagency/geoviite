import * as React from 'react';
import styles from './geometry-plan-infobox.scss';

import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { formatDateShort, toDateOrUndefined } from 'utils/date-utils';
import PlanPhase from 'geoviite-design-lib/geometry-plan/plan-phase';
import PlanDecisionPhase from 'geoviite-design-lib/geometry-plan/plan-decision-phase';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import { differenceInYears } from 'date-fns';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useAppNavigate } from 'common/navigate';
import { inframodelDownloadUri } from 'infra-model/infra-model-api';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getCoordinateSystem } from 'common/common-api';
import { useLoader } from 'utils/react-utils';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import MeasurementMethod from 'geoviite-design-lib/measurement-method/measurement-method';
import { TimeStamp } from 'common/common-model';
import { GeometryPlanInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { ConfirmDownloadUnreliableInfraModelDialog } from 'infra-model/list/confirm-download-unreliable-infra-model-dialog';
import ElevationMeasurementMethod from 'geoviite-design-lib/elevation-measurement-method/elevation-measurement-method';

type GeometryPlanInfoboxProps = {
    planHeader: GeometryPlanHeader;
    visibilities: GeometryPlanInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometryPlanInfoboxVisibilities) => void;
};

interface AgeProps {
    timeStamp: TimeStamp;
}
const Age: React.FC<AgeProps> = ({ timeStamp }) => {
    const { t } = useTranslation();
    const time = toDateOrUndefined(timeStamp);
    if (time === undefined) {
        return <React.Fragment />;
    } else {
        const ageYears = differenceInYears(new Date(), time);
        const ageString =
            formatDateShort(time) +
            ' (' +
            (ageYears === 0
                ? t(`tool-panel.geometry-plan.plan-age-under-1-year`)
                : t(`tool-panel.geometry-plan.plan-age-content`, { ageYears })) +
            ')';
        return <>{ageString}</>;
    }
};

const GeometryPlanInfobox: React.FC<GeometryPlanInfoboxProps> = ({
    planHeader,
    visibilities,
    onVisibilityChange,
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

    const [downloadConfirmPlan, setDownloadConfirmPlan] = React.useState<GeometryPlanHeader>();

    const generalInfobox = (
        <Infobox
            title={t('tool-panel.geometry-plan.general-title')}
            qa-id="geometry-plan-general-infobox"
            contentVisible={visibilities.plan}
            onContentVisibilityChange={() =>
                onVisibilityChange({ ...visibilities, plan: !visibilities.plan })
            }>
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
                    value={<TrackNumberLinkContainer trackNumberId={planHeader.trackNumberId} />}
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
        <React.Fragment>
            <Infobox
                title={t('tool-panel.geometry-plan.quality-title')}
                qa-id="geometry-plan-quality-infobox"
                contentVisible={visibilities.planQuality}
                onContentVisibilityChange={() =>
                    onVisibilityChange({ ...visibilities, planQuality: !visibilities.planQuality })
                }>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.geometry-plan.source')}
                        value={planHeader.source}
                    />
                    <InfoboxField label={t('tool-panel.geometry-plan.plan-age')}>
                        <Age timeStamp={planHeader.planTime} />
                    </InfoboxField>
                    <InfoboxField label={t('tool-panel.geometry-plan.plan-uploaded')}>
                        <Age timeStamp={planHeader.uploadTime} />
                    </InfoboxField>
                    <InfoboxField
                        label={t('tool-panel.geometry-plan.measurement-method')}
                        value={<MeasurementMethod method={planHeader.measurementMethod} />}
                    />
                    <InfoboxField
                        label={t('tool-panel.geometry-plan.coordinate-system')}
                        value={
                            coordinateSystemModel && (
                                <CoordinateSystemView coordinateSystem={coordinateSystemModel} />
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
                    <InfoboxField
                        label={t('tool-panel.geometry-plan.elevation-measurement-method')}
                        value={
                            <ElevationMeasurementMethod
                                method={planHeader.elevationMeasurementMethod}
                            />
                        }
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => navigate('inframodel-edit', planHeader.id)}>
                            {t('tool-panel.geometry-plan.open-inframodel')}
                        </Button>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            className={styles['geometry-plan-tool-panel__download-link']}
                            onClick={() => {
                                if (planHeader.source === 'PAIKANNUSPALVELU') {
                                    setDownloadConfirmPlan(planHeader);
                                } else {
                                    location.href = inframodelDownloadUri(planHeader.id);
                                }
                            }}>
                            <Icons.Download size={IconSize.SMALL} />{' '}
                            {t('tool-panel.geometry-plan.download-file')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {downloadConfirmPlan && (
                <ConfirmDownloadUnreliableInfraModelDialog
                    onClose={() => setDownloadConfirmPlan(undefined)}
                    plan={downloadConfirmPlan}
                />
            )}
        </React.Fragment>
    );

    return (
        <>
            {generalInfobox}
            {qualityInfobox}
        </>
    );
};

export default React.memo(GeometryPlanInfobox);
