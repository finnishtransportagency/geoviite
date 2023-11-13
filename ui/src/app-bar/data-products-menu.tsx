import * as React from 'react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './app-bar.scss';
import { Menu } from 'vayla-design-lib/menu/menu';

const DataProductsMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();

    const dataProducts = [
        {
            onSelect: () => {
                setShowMenu(false);
                navigate('data-products/element-list');
            },
            qaId: 'element-list-menu-link',
            name: t('app-bar.data-products.element-list'),
        },
        {
            onSelect: () => {
                setShowMenu(false);
                navigate('data-products/vertical-geometry');
            },
            qaId: 'vertical-geometry-menu-link',
            name: t('app-bar.data-products.vertical-geometry'),
        },
        {
            onSelect: () => {
                setShowMenu(false);
                navigate('data-products/kilometer-lengths');
            },
            qaId: 'kilometer-length-menu-link',
            name: t('app-bar.data-products.km-lengths'),
        },
    ];

    return (
        <div
            ref={menuRef}
            className={
                useLocation().pathname.includes('data-products')
                    ? `${styles['app-bar__link']} ${styles['app-bar__menu-button--active']}`
                    : `${styles['app-bar__link']} ${styles['app-bar__menu-button']}`
            }
            qa-id="data-product-link"
            onClick={() => setShowMenu(!showMenu)}>
            <span>{t('app-bar.data-products-title')}</span>

            {showMenu && (
                <Menu
                    positionRef={menuRef}
                    items={dataProducts}
                    className={styles['app-bar__data-products-menu']}
                    onClickOutside={() => setShowMenu(false)}
                />
            )}
        </div>
    );
};

export default DataProductsMenu;
