import * as React from 'react';
import styles from './eye.scss';
import CircularProgress from 'vayla-design-lib/progress/circular-progress';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';

type EyeProps = {
    visibility?: boolean;
    fetchingContent?: boolean;
    onVisibilityToggle: React.MouseEventHandler;
};
export const Eye: React.FC<EyeProps> = ({ visibility, fetchingContent, onVisibilityToggle }) => {
    return (
        <span
            className={`${styles['eye']} ${visibility ? styles['eye--visible'] : ''}`}
            onClick={onVisibilityToggle}>
            {fetchingContent ? <CircularProgress /> : <Icons.Eye color={IconColor.INHERIT} />}
        </span>
    );
};
