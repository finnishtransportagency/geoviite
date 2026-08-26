import * as React from 'react';
import styles from './eye.scss';
import { IconColor, IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export type VisibilityState = 'hidden' | 'partial' | 'visible' | 'forced';

export function resolveVisibility(forced: boolean, visible: boolean): VisibilityState {
    if (forced) return 'forced';
    return visible ? 'visible' : 'hidden';
}

type EyeProps = {
    visibility?: VisibilityState;
    fetchingContent?: boolean;
    onVisibilityToggle: React.MouseEventHandler;
    extraClassName?: string;
};

function pickIcon(visibility: VisibilityState): IconComponent {
    switch (visibility) {
        case 'hidden':
            return Icons.EyeHidden;
        case 'partial':
            return Icons.EyePartiallyVisible;
        case 'forced':
            return Icons.EyeForced;
        case 'visible':
            return Icons.EyeVisible;
        default:
            return exhaustiveMatchingGuard(visibility);
    }
}

export const Eye: React.FC<EyeProps> = ({
    visibility = 'hidden',
    fetchingContent,
    onVisibilityToggle,
    extraClassName,
}) => {
    const containerClassName = createClassName(styles['eye-container'], extraClassName);

    const icon = pickIcon(visibility);
    const disabled = visibility === 'forced';

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
