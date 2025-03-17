import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/plan-download/plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';

type PlanDownloadPopupSectionProps = {
    selected: boolean;
    toggleOpen: () => void;
    title: React.ReactNode;
    children?: React.ReactNode;
    disabled: boolean;
};
export const PlanDownloadPopupSection: React.FC<PlanDownloadPopupSectionProps> = ({
    selected,
    toggleOpen,
    title,
    children,
    disabled,
}) => {
    const chevronClasses = createClassName(
        styles['plan-download-popup-chevron'],
        selected && styles['plan-download-popup-chevron--visible'],
    );

    const titleContentClasses = createClassName(
        styles['plan-download-popup__title-content'],
        disabled && styles['plan-download-popup__title-content--disabled'],
    );

    return (
        <React.Fragment>
            <h2 className={styles['plan-download-popup__title']}>
                <Button
                    size={ButtonSize.X_SMALL}
                    className={chevronClasses}
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Chevron}
                    disabled={disabled}
                    onClick={() => !disabled && toggleOpen()}
                />
                <span className={titleContentClasses}>{title}</span>
            </h2>
            {selected && <div className={styles['plan-download-popup__content']}>{children}</div>}
        </React.Fragment>
    );
};
