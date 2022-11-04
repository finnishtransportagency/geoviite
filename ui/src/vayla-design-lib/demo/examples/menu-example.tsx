import * as React from 'react';
import { Menu } from 'vayla-design-lib/menu/menu';

export const MenuExample: React.FC = () => {
    const items = [
        { value: 'MENU1', name: 'Menu option 1' },
        { value: 'MENU2', name: 'Menu option 2' },
        { value: 'MENU3', name: 'Menu option 3' },
    ];

    const [chosenItem, setChosenItem] = React.useState<string>('');

    const handleItemChange = (item: string) => {
        setChosenItem(item);
    };

    return (
        <div>
            <h2>Menu</h2>
            <Menu
                items={items}
                value={chosenItem}
                onChange={(item) => item && handleItemChange(item)}
            />
            <div>Chosen item: {chosenItem}</div>
        </div>
    );
};
