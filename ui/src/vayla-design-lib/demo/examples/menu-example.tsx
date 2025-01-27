import * as React from 'react';
import { Menu, menuOption } from 'vayla-design-lib/menu/menu';
import { Button } from 'vayla-design-lib/button/button';

export const MenuExample: React.FC = () => {
    const items = [
        menuOption(() => setChosenItem('MENU1'), 'Menu option 1', 'MENU1'),
        menuOption(() => setChosenItem('MENU2'), 'Menu option 2', 'MENU2'),
        menuOption(() => setChosenItem('MENU3'), 'Menu option 3', 'MENU3'),
    ];

    const [chosenItem, setChosenItem] = React.useState<string>('');
    const [showMenu, setShowMenu] = React.useState(false);

    const menuRef = React.useRef(null);

    return (
        <div>
            <h2>Menu</h2>

            <div ref={menuRef}>
                <Button onClick={() => setShowMenu(true)}>Toggle menu</Button>
            </div>

            <div>Chosen item: {chosenItem}</div>

            {showMenu && (
                <Menu
                    anchorElementRef={menuRef}
                    items={items}
                    onClickOutside={() => {}}
                    onClose={() => setShowMenu(false)}
                />
            )}
        </div>
    );
};
