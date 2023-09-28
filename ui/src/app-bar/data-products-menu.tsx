import * as React from 'react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, useLocation } from 'react-router-dom';
import styles from './app-bar.scss';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';

const DataProductsMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const dataProductsModalOffsetX = 0;
    const dataProductsModalOffsetY = 50;

    return (
        <div
            ref={menuRef}
            className={
                useLocation().pathname.includes('data-products')
                    ? `${styles['app-bar__link']} ${styles['app-bar__data-menu-button--active']}`
                    : `${styles['app-bar__link']} ${styles['app-bar__data-menu-button']}`
            }
            qa-id="data-product-link"
            onClick={() => setShowMenu(!showMenu)}>
            <span>{t('app-bar.data-products-title')}</span>

            {showMenu && (
                <CloseableModal
                    positionRef={menuRef}
                    onClickOutside={() => setShowMenu(false)}
                    offsetX={dataProductsModalOffsetX}
                    offsetY={dataProductsModalOffsetY}
                    className={styles['app-bar__data-menu']}>
                    <div className={styles['app-bar__data-menu-item']}>
                        <NavLink
                            to={'data-products/element-list'}
                            onClick={() => setShowMenu(false)}
                            qa-id="element-list-menu-link">
                            {t('app-bar.data-products.element-list')}
                        </NavLink>
                    </div>
                    <div className={styles['app-bar__data-menu-item']}>
                        <NavLink
                            to={'data-products/vertical-geometry'}
                            onClick={() => setShowMenu(false)}
                            qa-id="vertical-geometry-menu-link">
                            {t('app-bar.data-products.vertical-geometry')}
                        </NavLink>
                    </div>
                    <div className={styles['app-bar__data-menu-item']}>
                        <NavLink
                            to={'data-products/kilometer-lengths'}
                            onClick={() => setShowMenu(false)}
                            qa-id="kilometer-length-menu-link">
                            {t('app-bar.data-products.km-lengths')}
                        </NavLink>
                    </div>
                </CloseableModal>
            )}
        </div>
    );
};

export default DataProductsMenu;
