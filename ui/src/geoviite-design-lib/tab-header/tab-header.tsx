import * as React from 'react';
import styles from './tab-header.scss';
import { createClassName } from 'vayla-design-lib/utils';

type TabHeaderProps = {
    selected: boolean;
    onClick: () => void;
    children: React.ReactNode;
    className?: string;
    qaId?: string;
};

export const TabHeader: React.FC<TabHeaderProps> = ({
    selected,
    onClick,
    children,
    className,
    qaId,
}) => {
    const tabClassName = createClassName(
        styles['tab-header'],
        selected && styles['tab-header--selected'],
        className,
    );
    return (
        <a className={tabClassName} onClick={onClick} qa-id={qaId}>
            {children}
        </a>
    );
};
