import * as React from 'react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, useLocation } from 'react-router-dom';
import styles from './app-bar.scss';
import { CloseableModal } from 'geoviite-design-lib/closeable-modal/closeable-modal';

const DataProductsMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const ref = useRef<HTMLDivElement>(null);

    return (
        <React.Fragment>
            <div
                ref={ref}
                className={
                    useLocation().pathname.includes('data-products')
                        ? `${styles['app-bar__link']} ${styles['app-bar__data-menu-button--active']}`
                        : `${styles['app-bar__link']} ${styles['app-bar__data-menu-button']}`
                }
                onClick={() => setShowMenu(true)}>
                <span>{t('app-bar.data-products-title')}</span>
            </div>
            {showMenu && (
                <CloseableModal
                    positionRef={ref}
                    onClickOutside={() => setShowMenu(false)}
                    offsetX={0}
                    offsetY={48}>
                    <div className={styles['app-bar__data-menu']}>
                        <div>
                            <NavLink
                                className={styles['menu__item']}
                                to={'data-products/element-list'}
                                onClick={() => setShowMenu(false)}>
                                {t('app-bar.data-products.element-list')}
                            </NavLink>
                        </div>
                        <div>
                            <NavLink
                                className={styles['menu__item']}
                                to={'data-products/vertical-geometry'}
                                onClick={() => setShowMenu(false)}>
                                {t('app-bar.data-products.vertical-geometry')}
                            </NavLink>
                        </div>
                        <div>
                            <NavLink
                                className={styles['menu__item']}
                                to={'data-products/kilometer-lengths'}
                                onClick={() => setShowMenu(false)}>
                                {t('app-bar.data-products.km-lengths')}
                            </NavLink>
                        </div>
                    </div>
                </CloseableModal>
            )}
        </React.Fragment>
    );
};

export default DataProductsMenu;
