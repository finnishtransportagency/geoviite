import * as React from 'react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './app-bar.scss';
import { Menu, menuOption } from 'vayla-design-lib/menu/menu';
import { createClassName } from 'vayla-design-lib/utils';
import { filterNotEmpty } from 'utils/array-utils';
import { userHasPrivilege, VIEW_GEOMETRY, VIEW_GEOMETRY_FILE } from 'user/user-model';
import { useCommonDataAppSelector } from 'store/hooks';

const DataProductsMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();
    const currentPrivileges = useCommonDataAppSelector(
        (state) => state.user?.role.privileges ?? [],
    ).map((p) => p.code);

    const dataProducts = [
        userHasPrivilege(currentPrivileges, VIEW_GEOMETRY_FILE)
            ? menuOption(
                  () => {
                      navigate('data-products/element-list');
                  },
                  t('app-bar.data-products.element-list'),
                  'element-list-menu-link',
              )
            : undefined,
        userHasPrivilege(currentPrivileges, VIEW_GEOMETRY_FILE)
            ? menuOption(
                  () => {
                      navigate('data-products/vertical-geometry');
                  },
                  t('app-bar.data-products.vertical-geometry'),
                  'vertical-geometry-menu-link',
              )
            : undefined,
        userHasPrivilege(currentPrivileges, VIEW_GEOMETRY)
            ? menuOption(
                  () => {
                      navigate('data-products/kilometer-lengths');
                  },
                  t('app-bar.data-products.km-lengths'),
                  'kilometer-length-menu-link',
              )
            : undefined,
    ].filter(filterNotEmpty);
    const locationWithinDataProducts = useLocation().pathname.includes('data-products');

    return (
        <React.Fragment>
            {dataProducts.length > 0 && (
                <div
                    ref={menuRef}
                    className={
                        locationWithinDataProducts
                            ? createClassName(
                                  styles['app-bar__link'],
                                  styles['app-bar__link--active'],
                                  styles['app-bar__menu-button'],
                              )
                            : createClassName(
                                  styles['app-bar__link'],
                                  styles['app-bar__menu-button'],
                              )
                    }
                    qa-id="data-product-link"
                    onClick={() => setShowMenu(!showMenu)}>
                    <span>{t('app-bar.data-products-title')}</span>

                    {showMenu && (
                        <Menu
                            anchorElementRef={menuRef}
                            items={dataProducts}
                            className={styles['app-bar__data-products-menu']}
                            onClickOutside={() => setShowMenu(false)}
                            qa-id={'data-products-menu'}
                            onClose={() => setShowMenu(false)}
                        />
                    )}
                </div>
            )}
        </React.Fragment>
    );
};

export default DataProductsMenu;
