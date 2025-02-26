import * as React from 'react';
import styles from './eye.scss';
import CircularProgress from 'vayla-design-lib/progress/circular-progress';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

type EyeProps = {
    visibility?: boolean;
    fetchingContent?: boolean;
    onVisibilityToggle: React.MouseEventHandler;
    disabled?: boolean;
};
export const Eye: React.FC<EyeProps> = ({
    visibility,
    fetchingContent,
    onVisibilityToggle,
    disabled = false,
}) => {
    const iconClassName = createClassName(
        styles['eye-icon'],
        visibility && styles['eye--visible'],
        disabled && styles['eye--disabled'],
    );

    return (
        <span className={styles['eye-container']}>
            {fetchingContent ? (
                <CircularProgress />
            ) : (
                <Button
                    size={ButtonSize.SMALL}
                    onClick={onVisibilityToggle}
                    icon={Icons.Eye}
                    iconProps={{
                        size: IconSize.MEDIUM,
                        color: IconColor.INHERIT,
                        extraClassName: iconClassName,
                    }}
                    variant={ButtonVariant.GHOST}
                    disabled={disabled}
                />
            )}
        </span>
    );
};
