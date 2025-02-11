import React from 'react';
import { useTranslation } from 'react-i18next';

import { Menu, MenuOption, menuOption } from 'vayla-design-lib/menu/menu';
import { useScrollListener } from 'utils/use-scroll-listener';

type RemovalConfirmationMenuProps = {
    anchorElementRef: React.RefObject<HTMLElement | null>;
    onConfirmRemoval: () => void;
    onClose: () => void;
    onClickOutside: () => void;
};

export const RemovalConfirmationMenu: React.FC<RemovalConfirmationMenuProps> = ({
    anchorElementRef,
    onConfirmRemoval,
    onClose,
    onClickOutside,
}) => {
    const { t } = useTranslation();

    useScrollListener(() => {
        // Hide the opened removal menu when the user is scrolling the sidebar
        // as it looks weird otherwise.
        onClickOutside();
    });

    const menuItems: MenuOption[] = [
        menuOption(
            () => onConfirmRemoval(),
            t('tool-panel.location-track.splitting.confirm-split-point-removal'),
            'confirm-split-point-removal',
        ),
    ];

    return (
        <Menu
            anchorElementRef={anchorElementRef}
            items={menuItems}
            onClose={onClose}
            onClickOutside={onClickOutside}
        />
    );
};
