import * as React from 'react';
import styles from './tab-header.scss';
import { createClassName } from 'vayla-design-lib/utils';

type TabHeaderProps = {
    selected: boolean;
    onClick: () => void;
    children: React.ReactNode;
    className?: string;
    qaId?: string;
    disabled?: boolean;
    title?: string;
};

export const TabHeader: React.FC<TabHeaderProps> = ({
    selected,
    onClick,
    children,
    className,
    qaId,
    disabled,
    title,
}) => {
    const tabClassName = createClassName(
        styles['tab-header'],
        selected && styles['tab-header--selected'],
        disabled && styles['tab-header--disabled'],
        className,
    );
    return (
        <a
            title={title}
            className={tabClassName}
            onClick={() => {
                !disabled && onClick();
            }}
            qa-id={qaId}>
            {children}
        </a>
    );
};
