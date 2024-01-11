import * as React from 'react';
import { NavLink } from 'react-router-dom';
import { useTrackLayoutAppSelector } from 'store/hooks';
import styles from 'app-bar/app-bar.scss';

type FrontpageLinkProps = {
    linkTranslation: string;
};

export const FrontpageLink: React.FC<FrontpageLinkProps> = ({ linkTranslation }) => {
    const selectedPublicationId = useTrackLayoutAppSelector(
        (state) => state.selection.publicationId,
    );

    const selectedPublicationSearch = useTrackLayoutAppSelector(
        (state) => state.selection.publicationSearch,
    );

    function getFrontpageLink(): string {
        if (selectedPublicationId) {
            return `/publications/${selectedPublicationId}`;
        } else if (selectedPublicationSearch) {
            return '/publications';
        }

        return `/`;
    }

    return (
        <NavLink
            to={getFrontpageLink()}
            className={({ isActive }) =>
                `${styles['app-bar__link']} ${isActive ? styles['app-bar__link--active'] : ''}`
            }
            end>
            {linkTranslation}
        </NavLink>
    );
};
