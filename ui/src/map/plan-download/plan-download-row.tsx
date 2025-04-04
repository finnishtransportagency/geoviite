import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { GeometryPlanId, PlanApplicability, PlanSource } from 'geometry/geometry-model';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/plan-download/plan-download-popup.scss';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';

type ApplicabilityIconProps = {
    applicability: PlanApplicability | undefined;
    disabled: boolean;
};

const ApplicabilityIcon: React.FC<ApplicabilityIconProps> = ({ applicability, disabled }) => {
    const iconColor = disabled ? IconColor.DISABLED : IconColor.ORIGINAL;
    switch (applicability) {
        case 'STATISTICS':
            return <Icons.BarsI size={IconSize.SMALL} color={iconColor} />;
        case 'MAINTENANCE':
            return <Icons.BarsII size={IconSize.SMALL} color={iconColor} />;
        case 'PLANNING':
            return <Icons.BarsIII size={IconSize.SMALL} color={iconColor} />;
        case undefined:
            return <React.Fragment>?</React.Fragment>;
        default:
            return exhaustiveMatchingGuard(applicability);
    }
};

type PlanItemProps = {
    id: GeometryPlanId;
    name: string;
    checked: boolean;
    applicability: PlanApplicability | undefined;
    source: PlanSource;
    setPlanSelected: (planId: GeometryPlanId, selected: boolean) => void;
    selectPlanInToolPanel: (planId: GeometryPlanId) => void;
    disabled: boolean;
};

export const PlanDownloadRow: React.FC<PlanItemProps> = ({
    id,
    checked,
    name,
    source,
    applicability,
    setPlanSelected,
    selectPlanInToolPanel,
    disabled,
}) => {
    const { t } = useTranslation();
    const classNames = createClassName(
        styles['plan-download-popup__plan-row'],
        disabled && styles['plan-download-popup__plan-row--disabled'],
        !disabled && styles['plan-download-popup__plan-row--enabled'],
    );
    const fromPaikannuspalvelu = source === 'PAIKANNUSPALVELU';
    const nameClassNames = createClassName(
        fromPaikannuspalvelu && styles['plan-download-popup__plan-name--has-subheader'],
    );

    return (
        <li className={classNames}>
            <Checkbox
                checked={checked}
                onChange={() => setPlanSelected(id, !checked)}
                disabled={disabled}
            />
            <span
                className={styles['plan-download-popup__plan-name']}
                onClick={() => !disabled && selectPlanInToolPanel(id)}>
                <span className={nameClassNames}>{name}</span>
                {fromPaikannuspalvelu && (
                    <span className={styles['plan-download-popup__plan-name-subheader']}>
                        {t('enum.PlanSource.PAIKANNUSPALVELU')}
                    </span>
                )}
            </span>
            <span className={styles['plan-download-popup__plan-icon']}>
                <ApplicabilityIcon applicability={applicability} disabled={disabled} />
            </span>
        </li>
    );
};
