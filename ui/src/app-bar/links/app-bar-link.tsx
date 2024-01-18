import * as React from 'react';
import { NavLink } from 'react-router-dom';
import styles from 'app-bar/app-bar.scss';

type AppBarLinkProps = {
    linkAddress: string;
    linkName: string;
};

export const AppBarLink: React.FC<AppBarLinkProps> = ({ linkAddress, linkName }) => {
    return (
        <NavLink
            to={linkAddress}
            className={({ isActive }) =>
                `${styles['app-bar__link']} ${isActive ? styles['app-bar__link--active'] : ''}`
            }
            end>
            {linkName}
        </NavLink>
    );
};
