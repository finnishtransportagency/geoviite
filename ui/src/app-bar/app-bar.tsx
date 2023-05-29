import * as React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';
import { EnvRestricted } from 'environment/env-restricted';
import { Environment } from 'environment/environment-info';
import { useTranslation } from 'react-i18next';
import { useInfraModelAppSelector } from 'store/hooks';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { InfraModelLink } from 'app-bar/infra-model-link';

type Link = {
    link: string;
    name: string;
    type: Environment;
};

const links: Link[] = [
    { link: '/', name: 'app-bar.frontpage', type: 'prod' },
    { link: '/track-layout', name: 'app-bar.track-layout', type: 'prod' },
    { link: '/rekisteri', name: 'app-bar.register', type: 'dev' },
    { link: '/infra-model', name: 'app-bar.infra-model', type: 'prod' },
    { link: '/design-lib-demo', name: 'app-bar.components', type: 'dev' },
    { link: '/localization-demo', name: 'app-bar.localization', type: 'dev' },
    {
        link: '/vertical-geometry-diagram-demo',
        name: 'app-bar.vertical-geometry-diagram-demo',
        type: 'dev',
    },
];

export const AppBar: React.FC = () => {
    const { t } = useTranslation();
    const [dataMenuOpen, setDataMenuOpen] = React.useState(false);
    const selectedInfraModelTab = useInfraModelAppSelector((state) => state.infraModelActiveTab);
    //TODO update velhoFilesWaiting with value showing whether there are file candidates waiting for approval
    const velhoFilesWaiting = true;

    function getInfraModelLink(): string {
        switch (selectedInfraModelTab) {
            case InfraModelTabType.PLAN:
                return '/infra-model/plans';
            case InfraModelTabType.WAITING:
                return '/infra-model/waiting-for-approval';
            case InfraModelTabType.REJECTED:
                return '/infra-model/rejected';
        }
    }

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
                                    {link.link !== '/infra-model' ? (
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
                                    ) : (
                                        <NavLink
                                            to={getInfraModelLink()}
                                            className={({ isActive }) =>
                                                `${styles['app-bar__link']} ${
                                                    styles['app-bar__link--infra-model']
                                                } ${
                                                    isActive ? styles['app-bar__link--active'] : ''
                                                }`
                                            }
                                            end>
                                            <InfraModelLink
                                                exclamationPointVisibility={velhoFilesWaiting}
                                            />
                                        </NavLink>
                                    )}
                                </li>
                            </EnvRestricted>
                        );
                    })}
                <li>
                    <div
                        className={
                            useLocation().pathname.includes('data-products')
                                ? `${styles['app-bar__link']} ${styles['app-bar__data-menu-button--active']}`
                                : `${styles['app-bar__link']} ${styles['app-bar__data-menu-button']}`
                        }
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
                                <div>
                                    <NavLink
                                        className={styles['menu__item']}
                                        to={'data-products/vertical-geometry'}>
                                        {t('app-bar.data-products.vertical-geometry')}
                                    </NavLink>
                                </div>
                                <div>
                                    <NavLink
                                        className={styles['menu__item']}
                                        to={'data-products/kilometer-lengths'}>
                                        {t('app-bar.data-products.km-lengths')}
                                    </NavLink>
                                </div>
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
