import * as React from 'react';
import { Menu } from 'vayla-design-lib/menu/menu';
import { Button } from 'vayla-design-lib/button/button';

export const MenuExample: React.FC = () => {
    const items = [
        { value: 'MENU1', name: 'Menu option 1' },
        { value: 'MENU2', name: 'Menu option 2' },
        { value: 'MENU3', name: 'Menu option 3' },
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
                {showMenu && (
                    <Menu
                        positionRef={menuRef}
                        items={items}
                        onSelect={(item) => item && handleItemChange(item)}
                        onClickOutside={() => {}}
                    />
                )}
            </div>

            <div>Chosen item: {chosenItem}</div>
        </div>
    );
};
