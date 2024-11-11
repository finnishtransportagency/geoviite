import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';

export enum InfoBoxVariant {
    BLUE = 'infobox--blue',
}

export type InfoboxProps = {
    title: string;
    children?: React.ReactNode;
    variant?: InfoBoxVariant;
    className?: string;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    iconDisabled?: boolean;
    iconHidden?: boolean;
    onEdit?: () => void;
    qaId?: string;
};

const Infobox: React.FC<InfoboxProps> = ({
    title,
    variant,
    className,
    contentVisible,
    onContentVisibilityChange,
    qaId,
    iconDisabled = false,
    iconHidden = false,
    onEdit,
    ...props
}: InfoboxProps) => {
    const { t } = useTranslation();
    const iconTitle = iconDisabled
        ? t('tool-panel.disabled.activity-disabled-in-official-mode')
        : '';

    const classes = createClassName(styles.infobox, variant && styles[variant], className);
    const titleClasses = createClassName(
        'infobox__title',
        contentVisible && 'infobox__title--visible',
    );

    const chevronClasses = createClassName(
        'infobox__title-chevron',
        contentVisible && 'infobox__title-chevron--visible',
    );

    return (
        <div className={classes} {...props}>
            <div className={titleClasses}>
                <span
                    className={styles['infobox__title-accordion']}
                    onClick={onContentVisibilityChange}>
                    <span className={chevronClasses} onClick={onContentVisibilityChange}>
                        <Icons.Chevron size={IconSize.SMALL} />
                    </span>
                    <span className={styles['infobox__title-content']}>{title}</span>
                </span>
                {onEdit && !iconHidden && (
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <span qa-id={`${qaId}-edit`} className={styles['infobox__title-edit-icon']}>
                            <Button
                                title={iconTitle}
                                disabled={iconDisabled || !onEdit}
                                icon={Icons.Edit}
                                variant={ButtonVariant.GHOST}
                                onClick={() => !iconDisabled && onEdit && onEdit()}
                                qa-id={`infobox-edit-button`}
                            />
                        </span>
                    </PrivilegeRequired>
                )}
            </div>
            {contentVisible && props.children}
        </div>
    );
};

export default Infobox;
