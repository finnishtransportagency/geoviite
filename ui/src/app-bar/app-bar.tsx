import * as React from 'react';
import { NavLink } from 'react-router-dom';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';

type Link = {
    link: string;
    name: string;
    type: string;
};

const links: Link[] = [
    { link: '/', name: 'Etusivu', type: 'prod' },
    { link: '/track-layout', name: 'Kartta', type: 'prod' },
    { link: '/rekisteri', name: 'Rekisteri', type: 'prod' },
    { link: '/infra-model', name: 'InfraModel', type: 'prod' },
    { link: '/design-lib-demo', name: 'Components', type: 'dev' },
    { link: '/localization-demo', name: 'Localization', type: 'dev' },
];

function filterLinks(links: Link[], type: string) {
    return links.filter((link) => type == link.type);
}

export const AppBar: React.FC = () => {
    const [navLinks, setNavLinks] = React.useState<Link[]>();
    React.useEffect(() => {
        setNavLinks(
            location.hostname === 'localhost' || location.hostname === '127.0.0.1'
                ? links
                : filterLinks(links, 'prod'),
        );
    }, []);

    return (
        <nav className={styles['app-bar']}>
            <div className={styles['app-bar__title']}>
                <img className={styles['app-bar__logo']} src={geoviiteLogo} alt="Geoviite logo" />
                <div>Geoviite</div>
            </div>
            <ul className={styles['app-bar__links']}>
                {navLinks &&
                    navLinks.map((link) => {
                        return (
                            <li key={link.name}>
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
