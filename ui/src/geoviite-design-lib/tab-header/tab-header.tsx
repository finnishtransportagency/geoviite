import * as React from 'react';
import styles from './tab-header.scss';
import { createClassName } from 'vayla-design-lib/utils';

export enum TabHeaderSize {
    Small = 'tab-header--size-small',
    Large = 'tab-header--size-large',
}

type TabHeaderProps = {
    selected: boolean;
    onClick: () => void;
    children: React.ReactNode;
    size?: TabHeaderSize;
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
    size = TabHeaderSize.Large,
}) => {
    const tabClassName = createClassName(
        styles['tab-header'],
        styles[size],
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
