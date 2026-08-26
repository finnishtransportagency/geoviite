import * as React from 'react';
import styles from './eye.scss';
import { IconColor, IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

export type VisibilityState = 'hidden' | 'partial' | 'visible';

type EyeProps = {
    visibility?: VisibilityState;
    fetchingContent?: boolean;
    onVisibilityToggle: React.MouseEventHandler;
    disabled?: boolean;
    extraClassName?: string;
};

function pickIcon(visibility: VisibilityState, disabled: boolean): IconComponent {
    switch (visibility) {
        case 'hidden':
            return Icons.EyeHidden;
        case 'partial':
            return Icons.EyePartiallyVisible;
        default:
            return disabled ? Icons.EyeForced : Icons.EyeVisible;
    }
}

export const Eye: React.FC<EyeProps> = ({
    visibility = 'hidden',
    fetchingContent,
    onVisibilityToggle,
    disabled = false,
    extraClassName,
}) => {
    const containerClassName = createClassName(styles['eye-container'], extraClassName);

    const icon = pickIcon(visibility, disabled);

    return (
        <span className={containerClassName}>
            <Button
                size={ButtonSize.SMALL}
                onClick={onVisibilityToggle}
                icon={icon}
                iconProps={{
                    size: IconSize.MEDIUM,
                    color: IconColor.ORIGINAL,
                }}
                variant={ButtonVariant.GHOST}
                disabled={disabled}
                isProcessing={fetchingContent}
            />
        </span>
    );
};
