import React from 'react';
import styles from './infobox.module.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';

type InfoboxFieldProps = {
    label: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    inEditMode?: boolean;
    onEdit?: () => void;
    className?: string;
    iconDisabled?: boolean;
};

const InfoboxField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    className,
    inEditMode = false,
    iconDisabled = false,
    ...props
}: InfoboxFieldProps) => {
    const classes = createClassName(styles['infobox__field'], className);
    const { t } = useTranslation();

    return (
        <div className={classes}>
            <div className={styles['infobox__field-label']}>{label}</div>
            <div className={styles['infobox__field-value']}>{children || value}</div>
            {!inEditMode && props.onEdit && !iconDisabled && (
                <div
                    className={styles['infobox__edit-icon']}
                    onClick={() => props.onEdit && props.onEdit()}>
                    <Icons.Edit size={IconSize.SMALL} />
                </div>
            )}
            <div>
                {iconDisabled && (
                    <div className={styles['infobox__edit-icon']}>
                        <span title={t('tool-panel.disabled.activity-disabled-in-official-mode')}>
                            <Icons.Edit size={IconSize.SMALL} color={IconColor.DISABLED} />
                        </span>
                    </div>
                )}
            </div>
        </div>
    );
};

export default InfoboxField;
