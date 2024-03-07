import * as React from 'react';
import styles from './tab-header.scss';
import { createClassName } from 'vayla-design-lib/utils';

type TabHeaderProps = {
    selected: boolean;
    onClick: () => void;
    children: React.ReactNode;
};

export const TabHeader: React.FC<TabHeaderProps> = ({ selected, onClick, children }) => {
    const className = createClassName(
        styles['tab-header'],
        selected && styles['tab-header--selected'],
    );
    return (
        <button className={className} onClick={onClick}>
            {children}
        </button>
    );
};
