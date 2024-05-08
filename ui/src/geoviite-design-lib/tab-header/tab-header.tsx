import * as React from 'react';
import styles from './tab-header.scss';
import { createClassName } from 'vayla-design-lib/utils';

type TabHeaderProps = {
    selected: boolean;
    onClick: () => void;
    children: React.ReactNode;
    className?: string;
};

export const TabHeader: React.FC<TabHeaderProps> = ({ selected, onClick, children, className }) => {
    const tabClassName = createClassName(
        styles['tab-header'],
        selected && styles['tab-header--selected'],
        className,
    );
    return (
        <button className={tabClassName} onClick={onClick}>
            {children}
        </button>
    );
};
