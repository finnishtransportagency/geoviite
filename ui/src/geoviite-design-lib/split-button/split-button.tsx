import * as React from 'react';
import { Menu, MenuOption } from 'vayla-design-lib/menu/menu';
import { Button, ButtonProps } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import styles from './split-button.scss';

type SplitButtonProps = {
    'menuItems': MenuOption[];
    'children'?: React.ReactNode;
    'qa-id'?: string;
} & ButtonProps;

export const SplitButton = function ({
    menuItems,
    children,
    'qa-id': qaId,
    ...buttonProps
}: SplitButtonProps) {
    const ref = React.useRef<HTMLSpanElement>(null);
    const [menuOpen, setMenuOpen] = React.useState(false);

    return (
        <span className={styles['split-button']} ref={ref}>
            <Button {...buttonProps} qa-id={qaId}>
                {children}
            </Button>
            <Button
                icon={Icons.Down}
                onClick={() => setMenuOpen(!menuOpen)}
                size={buttonProps.size}
                variant={buttonProps.variant}
                qa-id={`${qaId}-menu`}
                disabled={buttonProps.disabled}
            />
            {menuOpen && (
                <Menu
                    anchorElementRef={ref}
                    onClickOutside={() => setMenuOpen(false)}
                    onClose={() => setMenuOpen(false)}
                    items={menuItems}
                />
            )}
        </span>
    );
};
