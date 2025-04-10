import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/plan-download/plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';

type PlanDownloadPopupSectionProps = {
    selected: boolean;
    toggleOpen: () => void;
    title: React.ReactNode;
    titleButtons?: React.ReactNode;
    children?: React.ReactNode;
};
export const PlanDownloadPopupSection: React.FC<PlanDownloadPopupSectionProps> = ({
    selected,
    toggleOpen,
    title,
    titleButtons,
    children,
}) => {
    const chevronClasses = createClassName(
        styles['plan-download-popup-chevron'],
        selected && styles['plan-download-popup-chevron--visible'],
    );

    const titleContentClasses = createClassName(styles['plan-download-popup__title-content']);
    const titleClasses = createClassName(
        styles['plan-download-popup__title'],
        styles['plan-download-popup__title--interactable'],
    );

    return (
        <React.Fragment>
            <div className={styles['plan-download-popup__title-container']}>
                <h2 className={titleClasses} onClick={() => toggleOpen()}>
                    <Button
                        size={ButtonSize.X_SMALL}
                        className={chevronClasses}
                        variant={ButtonVariant.GHOST}
                        icon={Icons.Chevron}
                    />
                    <span className={titleContentClasses}>{title}</span>
                </h2>
                {titleButtons && (
                    <div className={styles['plan-download-popup__title-buttons']}>
                        {titleButtons}
                    </div>
                )}
            </div>
            {selected && <div className={styles['plan-download-popup__content']}>{children}</div>}
        </React.Fragment>
    );
};
