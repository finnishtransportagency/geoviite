import * as React from 'react';
import { NavLink } from 'react-router-dom';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import styles from 'app-bar/app-bar.scss';
import { useTranslation } from 'react-i18next';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import { useLoader } from 'utils/react-utils';
import { getCurrentPublicationFailure } from 'ratko/ratko-api';
import { getChangeTimes } from 'common/change-time-api';

export const FrontpageLink: React.FC = () => {
    const { t } = useTranslation();

    const selectedPublicationId = useTrackLayoutAppSelector(
        (state) => state.selection.publicationId,
    );

    const selectedPublicationSearch = useTrackLayoutAppSelector(
        (state) => state.selection.publicationSearch,
    );

    const ratkoStatus = useCommonDataAppSelector((state) => state.ratkoStatus);
    const changeTimes = getChangeTimes();

    const currentRatkoPushError = useLoader(
        () => getCurrentPublicationFailure(),
        [changeTimes.publication, changeTimes.ratkoPush],
    );

    const ratkoIsOffline = ratkoStatus?.connectionStatus === 'OFFLINE';
    const ratkoNotConfigured = ratkoStatus?.connectionStatus === 'NOT_CONFIGURED';
    const hasRatkoPushError = !!currentRatkoPushError;
    const hasSomeError = ratkoIsOffline || ratkoNotConfigured || hasRatkoPushError;

    function getFrontpageLink(): string {
        if (selectedPublicationId) {
            return `/publications/${selectedPublicationId}`;
        } else if (selectedPublicationSearch) {
            return '/publications';
        }

        return `/`;
    }

    const ratkoErrorIndicatorTitle = () => {
        if (hasRatkoPushError) {
            return 'push-error';
        } else if (ratkoIsOffline) {
            return 'ratko-unreachable.';
        } else if (ratkoNotConfigured) {
            return 'ratko-not-configured';
        } else {
            return undefined;
        }
    };

    return (
        <NavLink
            to={getFrontpageLink()}
            className={({ isActive }) =>
                `${styles['app-bar__link']} ${isActive ? styles['app-bar__link--active'] : ''}`
            }
            end>
            {t('app-bar.frontpage')}
            {hasSomeError && (
                <React.Fragment>
                    <span
                        className={styles['app-bar__link--exclamation-point']}
                        title={t(`app-bar.ratko-errors.${ratkoErrorIndicatorTitle()}`)}>
                        <ExclamationPoint />
                    </span>
                </React.Fragment>
            )}
        </NavLink>
    );
};
