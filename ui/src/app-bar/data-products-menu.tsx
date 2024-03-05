import * as React from 'react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './app-bar.scss';
import { Menu, menuSelectOption } from 'vayla-design-lib/menu/menu';
import { createClassName } from 'vayla-design-lib/utils';

const DataProductsMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();

    const dataProducts = [
        menuSelectOption(
            () => {
                setShowMenu(false);
                navigate('data-products/element-list');
            },
            t('app-bar.data-products.element-list'),
            'element-list-menu-link',
        ),
        menuSelectOption(
            () => {
                setShowMenu(false);
                navigate('data-products/vertical-geometry');
            },
            t('app-bar.data-products.vertical-geometry'),
            'vertical-geometry-menu-link',
        ),
        menuSelectOption(
            () => {
                setShowMenu(false);
                navigate('data-products/kilometer-lengths');
            },
            t('app-bar.data-products.km-lengths'),
            'kilometer-length-menu-link',
        ),
    ];

    return (
        <div
            ref={menuRef}
            className={
                useLocation().pathname.includes('data-products')
                    ? createClassName(
                          styles['app-bar__link'],
                          styles['app-bar__link--active'],
                          styles['app-bar__menu-button'],
                      )
                    : createClassName(styles['app-bar__link'], styles['app-bar__menu-button'])
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
