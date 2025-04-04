import React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from 'tool-panel/geometry-plan/geometry-plan-infobox.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    getPlanApplicabilityTranslationKey,
    PlanApplicabilitySelector,
} from 'tool-panel/geometry-plan/geometry-plan-applicability-selector';

type GeometryPlanApplicabilityProps = {
    planHeader: GeometryPlanHeader;
};
export const GeometryPlanApplicability: React.FC<GeometryPlanApplicabilityProps> = ({
    planHeader,
}) => {
    const { t } = useTranslation();
    const [editing, setEditing] = React.useState(false);

    React.useEffect(() => {
        setEditing(false);
    }, [planHeader.id]);

    return (
        <InfoboxField
            label={t('tool-panel.geometry-plan.applicability')}
            value={
                <React.Fragment>
                    {editing ? (
                        <PlanApplicabilitySelector
                            planHeader={planHeader}
                            endEditing={() => setEditing(false)}
                        />
                    ) : (
                        <span className={styles['geometry-plan-tool-panel__applicability-value']}>
                            <span>
                                {t(
                                    getPlanApplicabilityTranslationKey(
                                        planHeader.planApplicability,
                                    ),
                                )}
                            </span>
                            <Button
                                icon={Icons.Edit}
                                onClick={() => setEditing(true)}
                                size={ButtonSize.X_SMALL}
                                variant={ButtonVariant.GHOST}
                            />
                        </span>
                    )}
                </React.Fragment>
            }
        />
    );
};
