import * as React from 'react';
import styles from './accordion-toggle.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

export type AccordionToggleProps = {
    onToggle?: React.MouseEventHandler;
    open: boolean;
    disabled?: boolean;
};

export const AccordionToggle: React.FC<AccordionToggleProps> = ({
    onToggle,
    open,
    disabled,
}: AccordionToggleProps) => {
    const className = createClassName(
        styles['accordion-toggle'],
        open && styles['accordion-toggle--open'],
        disabled && styles['accordion-toggle--disabled'],
    );
    return (
        <span onClick={onToggle} className={className}>
            <Icons.Chevron
                color={disabled ? IconColor.DISABLED : IconColor.ORIGINAL}
                size={IconSize.SMALL}
            />
        </span>
    );
};
