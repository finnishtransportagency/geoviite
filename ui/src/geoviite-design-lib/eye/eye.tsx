import * as React from 'react';
import styles from './eye.scss';
import { IconColor, IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

export type VisibilityState = 'hidden' | 'partial' | 'visible';

const PartialEyeIcon: IconComponent = ({ size, extraClassName }) => (
    <span className={createClassName(styles['eye-icon--partial'], extraClassName)}>
        <Icons.Eye
            size={size}
            color={IconColor.INHERIT}
            extraClassName={styles['eye-icon--partial-hidden-half']}
        />
        <Icons.Eye
            size={size}
            color={IconColor.INHERIT}
            extraClassName={styles['eye-icon--partial-visible-half']}
        />
    </span>
);

type EyeProps = {
    visibility?: VisibilityState;
    fetchingContent?: boolean;
    onVisibilityToggle: React.MouseEventHandler;
    disabled?: boolean;
    extraClassName?: string;
};
export const Eye: React.FC<EyeProps> = ({
    visibility = 'hidden',
    fetchingContent,
    onVisibilityToggle,
    disabled = false,
    extraClassName,
}) => {
    const containerClassName = createClassName(styles['eye-container'], extraClassName);

    const iconClassName = createClassName(
        styles['eye-icon'],
        visibility === 'visible' && styles['eye--visible'],
        disabled && styles['eye--disabled'],
    );

    return (
        <span className={containerClassName}>
            <Button
                size={ButtonSize.SMALL}
                onClick={onVisibilityToggle}
                icon={visibility === 'partial' ? PartialEyeIcon : Icons.Eye}
                iconProps={{
                    size: IconSize.MEDIUM,
                    color: IconColor.INHERIT,
                    extraClassName: iconClassName,
                }}
                variant={ButtonVariant.GHOST}
                disabled={disabled}
                isProcessing={fetchingContent}
            />
        </span>
    );
};
