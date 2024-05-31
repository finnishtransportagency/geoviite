import React from 'react';
import styles from './infobox.module.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';

type InfoboxFieldProps = {
    label: React.ReactNode;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    onEdit?: () => void;
    className?: string;
    iconDisabled?: boolean;
    iconHidden?: boolean;
    hasErrors?: boolean;
};

const InfoboxField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    className,
    qaId,
    iconDisabled = false,
    iconHidden = false,
    hasErrors = false,
    ...props
}: InfoboxFieldProps) => {
    const classes = createClassName(styles['infobox__field'], className);
    const { t } = useTranslation();
    const iconTitle = iconDisabled
        ? t('tool-panel.disabled.activity-disabled-in-official-mode')
        : '';
    const iconColor = iconDisabled ? IconColor.DISABLED : IconColor.ORIGINAL;

    return (
        <div className={classes} qa-id={qaId}>
            <div
                className={createClassName(
                    styles['infobox__field-label'],
                    hasErrors && styles['infobox__field-label--error'],
                )}>
                {label}
            </div>
            <div
                className={createClassName(
                    styles['infobox__field-value'],
                    hasErrors && styles['infobox__field-value--error'],
                )}>
                {children || value}
            </div>
            {props.onEdit && !iconHidden && (
                <PrivilegeRequired privilege={EDIT_LAYOUT}>
                    <div
                        className={styles['infobox__edit-icon']}
                        onClick={() => !iconDisabled && props.onEdit && props.onEdit()}
                        qa-id={`${qaId}-edit`}>
                        <span title={iconTitle}>
                            <Icons.Edit size={IconSize.SMALL} color={iconColor} />
                        </span>
                    </div>
                </PrivilegeRequired>
            )}
        </div>
    );
};

export default InfoboxField;
