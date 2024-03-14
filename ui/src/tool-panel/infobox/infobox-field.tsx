import React from 'react';
import styles from './infobox.module.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';
import { PrivilegeRequired } from 'user/privilege-required';
import { PRIV_EDIT_LAYOUT } from 'user/user-model';

type InfoboxFieldProps = {
    label: React.ReactNode;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    inEditMode?: boolean;
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
    inEditMode = false,
    iconDisabled = false,
    iconHidden = false,
    hasErrors = false,
    ...props
}: InfoboxFieldProps) => {
    const classes = createClassName(styles['infobox__field'], className);
    const { t } = useTranslation();

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
            {!inEditMode && props.onEdit && !iconDisabled && !iconHidden && (
                <div
                    className={styles['infobox__edit-icon']}
                    onClick={() => props.onEdit && props.onEdit()}>
                    <Icons.Edit size={IconSize.SMALL} />
                </div>
            )}
            {iconDisabled && !iconHidden && (
                <PrivilegeRequired privilege={PRIV_EDIT_LAYOUT}>
                    <div className={styles['infobox__edit-icon']}>
                        <span title={t('tool-panel.disabled.activity-disabled-in-official-mode')}>
                            <Icons.Edit size={IconSize.SMALL} color={IconColor.DISABLED} />
                        </span>
                    </div>
                </PrivilegeRequired>
            )}
        </div>
    );
};

export default InfoboxField;
