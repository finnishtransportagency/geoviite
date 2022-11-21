import * as React from 'react';
import { NavLink } from 'react-router-dom';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';
import { EnvRestricted } from 'environment/env-restricted';
import { Environment } from 'environment/environment-info';

type Link = {
    link: string;
    name: string;
    type: Environment;
};

const links: Link[] = [
    { link: '/', name: 'Etusivu', type: 'prod' },
    { link: '/track-layout', name: 'Kartta', type: 'prod' },
    { link: '/rekisteri', name: 'Rekisteri', type: 'prod' },
    { link: '/infra-model', name: 'InfraModel', type: 'prod' },
    { link: '/design-lib-demo', name: 'Components', type: 'dev' },
    { link: '/localization-demo', name: 'Localization', type: 'dev' },
];

export const AppBar: React.FC = () => {
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
                                        {link.name}
                                    </NavLink>
                                </li>
                            </EnvRestricted>
                        );
                    })}
            </ul>
            <img
                className={styles['app-bar__vayla-logo']}
                src={vaylaLogo}
                alt="Väylävirasto logo"
            />
        </nav>
    );
};
