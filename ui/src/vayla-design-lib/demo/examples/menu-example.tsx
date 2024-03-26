import * as React from 'react';
import { Menu, menuValueOption } from 'vayla-design-lib/menu/menu';
import { Button } from 'vayla-design-lib/button/button';

export const MenuExample: React.FC = () => {
    const items = [
        menuValueOption('MENU1', 'Menu option 1', 'MENU1'),
        menuValueOption('MENU2', 'Menu option 2', 'MENU2'),
        menuValueOption('MENU3', 'Menu option 3', 'MENU3'),
    ];

    const [chosenItem, setChosenItem] = React.useState<string>('');
    const [showMenu, setShowMenu] = React.useState(false);

    const handleItemChange = (item: string) => {
        setChosenItem(item);
    };

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
                    positionRef={menuRef}
                    items={items}
                    onSelect={(item) => item && handleItemChange(item)}
                    onClickOutside={() => {}}
                />
            )}
        </div>
    );
};
