import * as React from 'react';
import { NavLink } from 'react-router-dom';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';
import { EnvRestricted } from 'environment/env-restricted';
import { Environment } from 'environment/environment-info';
import { useTranslation } from 'react-i18next';

type Link = {
    link: string;
    name: string;
    type: Environment;
};

const links: Link[] = [
    { link: '/', name: 'app-bar.frontpage', type: 'prod' },
    { link: '/track-layout', name: 'app-bar.track-layout', type: 'prod' },
    { link: '/rekisteri', name: 'app-bar.register', type: 'prod' },
    { link: '/infra-model', name: 'app-bar.infra-model', type: 'prod' },
    { link: '/design-lib-demo', name: 'app-bar.components', type: 'dev' },
    { link: '/localization-demo', name: 'app-bar.localization', type: 'dev' },
];

export const AppBar: React.FC = () => {
    const { t } = useTranslation();
    const [dataMenuOpen, setDataMenuOpen] = React.useState(false);

    return (
        <nav className={styles['app-bar']}>
            <div className={styles['app-bar__title']}>
                <img className={styles['app-bar__logo']} src={geoviiteLogo} alt="Geoviite logo" />
                <div>Geoviite</div>
            </div>
            <ul className={styles['app-bar__links']}>
                {links &&
                    links.map((link) => {
                        return (
                            <EnvRestricted restrictTo={link.type} key={link.name}>
                                <li>
                                    <NavLink
                                        to={link.link}
                                        className={({ isActive }) =>
                                            `${styles['app-bar__link']} ${
                                                isActive ? styles['app-bar__link--active'] : ''
                                            }`
                                        }
                                        end>
                                        {t(link.name)}
                                    </NavLink>
                                </li>
                            </EnvRestricted>
                        );
                    })}
                <li>
                    <div
                        className={`${styles['app-bar__link']} ${styles['app-bar__data-menu-button']}`}
                        onClick={() => setDataMenuOpen(!dataMenuOpen)}>
                        <span>{t('app-bar.data-products-title')}</span>
                        {dataMenuOpen && (
                            <div className={styles['app-bar__data-menu']}>
                                <div>
                                    <NavLink
                                        className={styles['menu__item']}
                                        to={'data-products/element-list'}>
                                        {t('app-bar.data-products.element-list')}
                                    </NavLink>
                                </div>
                                <EnvRestricted restrictTo={'dev'}>
                                    <div>
                                        <NavLink
                                            className={styles['menu__item']}
                                            to={'data-products/vertical-geometry'}>
                                            {t('app-bar.data-products.vertical-geometry')}
                                        </NavLink>
                                    </div>
                                    <div className={styles['menu__item']}>
                                        {t('app-bar.data-products.km-lengths')}
                                    </div>
                                </EnvRestricted>
                            </div>
                        )}
                    </div>
                </li>
            </ul>
            <img
                className={styles['app-bar__vayla-logo']}
                src={vaylaLogo}
                alt="Väylävirasto logo"
            />
        </nav>
    );
};
