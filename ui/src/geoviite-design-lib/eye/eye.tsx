import * as React from 'react';
import styles from './eye.scss';
import CircularProgress from 'vayla-design-lib/progress/circular-progress';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

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
    const className = createClassName(
        styles['eye'],
        visibility && styles['eye--visible'],
        disabled && styles['eye--disabled'],
    );
    return (
        <span className={className} onClick={onVisibilityToggle}>
            {fetchingContent ? <CircularProgress /> : <Icons.Eye color={IconColor.INHERIT} />}
        </span>
    );
};
