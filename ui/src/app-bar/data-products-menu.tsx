import * as React from 'react';
import { useRef } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { NavLink, useLocation } from 'react-router-dom';
import useResizeObserver from 'use-resize-observer';
import styles from './app-bar.scss';

type DataProductsMenuProps = React.HTMLProps<HTMLDivElement>;
//TODO move under a general closeable component
const DataProductsMenu: React.FC<DataProductsMenuProps> = ({ ...props }) => {
    const [showMenu, setShowMenu] = React.useState(false);
    const ref = useRef<HTMLDivElement>(null);
    const { t } = useTranslation();
    const [x, setX] = React.useState<number>();
    const [y, setY] = React.useState<number>();

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (ref.current && !ref.current.contains(event.target as HTMLElement)) {
                setShowMenu(false);
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [ref]);

    React.useEffect(() => {
        setX(ref.current?.getBoundingClientRect().x);
    }, [ref.current?.getBoundingClientRect().x]);

    useResizeObserver({
        ref: document.body,
        onResize: () => {
            const { x: newX, y: newY } = ref.current?.getBoundingClientRect() ?? {};
            setX(newX);
            setY(newY);
        },
    });

    return (
        <div ref={ref} {...props}>
            <div
                className={
                    useLocation().pathname.includes('data-products')
                        ? `${styles['app-bar__link']} ${styles['app-bar__data-menu-button--active']}`
                        : `${styles['app-bar__link']} ${styles['app-bar__data-menu-button']}`
                }
                onClick={() => setShowMenu(!showMenu)}>
                <span>{t('app-bar.data-products-title')}</span>
            </div>
            {showMenu && x != undefined && y != undefined && (
                <DataMenuItems x={x} y={y + 48} onSelect={() => setShowMenu(false)} />
            )}
        </div>
    );
};

type DataMenuItemProps = {
    x: number;
    y: number;
    onSelect: () => void;
};

const DataMenuItems: React.FC<DataMenuItemProps> = ({ x, y, onSelect }) => {
    const { t } = useTranslation();
    return createPortal(
        <div style={{ top: y, left: x, position: 'absolute' }} onClick={(e) => e.stopPropagation()}>
            <div className={styles['app-bar__data-menu']}>
                <div>
                    <NavLink
                        className={styles['menu__item']}
                        to={'data-products/element-list'}
                        onClick={onSelect}>
                        {t('app-bar.data-products.element-list')}
                    </NavLink>
                </div>
                <div>
                    <NavLink
                        className={styles['menu__item']}
                        to={'data-products/vertical-geometry'}
                        onClick={onSelect}>
                        {t('app-bar.data-products.vertical-geometry')}
                    </NavLink>
                </div>
                <div>
                    <NavLink
                        className={styles['menu__item']}
                        to={'data-products/kilometer-lengths'}
                        onClick={onSelect}>
                        {t('app-bar.data-products.km-lengths')}
                    </NavLink>
                </div>
            </div>
        </div>,
        document.body,
    );
};

export default DataProductsMenu;
