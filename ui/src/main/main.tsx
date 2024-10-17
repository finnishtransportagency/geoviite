import * as React from 'react';
import 'i18n/config';
import { Route, Routes } from 'react-router-dom';
import styles from './main.module.scss';
import { TrackLayoutContainer } from 'track-layout/track-layout-container';
import { Slide, ToastContainer } from 'react-toastify';
import { I18nDemo } from 'i18n/i18n-demo';
import { AppBar } from 'app-bar/app-bar';
import { GeoviiteLibDemo } from 'geoviite-design-lib/demo/demo';
import { VersionHolderView } from 'version-holder/version-holder-view';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutMode } from 'common/common-model';
import { PreviewContainer } from 'preview/preview-container';
import { FrontpageContainer } from 'frontpage/frontpage-container';
import { EnvRestricted } from 'environment/env-restricted';
import { useTranslation, withTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
// fontsource requires fonts to be imported somewhere in code
import '@fontsource/open-sans/400.css';
import '@fontsource/open-sans/600.css';
import { useEnvironmentInfo } from 'environment/environment-info';
import { createDelegates } from 'store/store-utils';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { Button } from 'vayla-design-lib/button/button';
import { InfraModelMainView } from 'infra-model/infra-model-main-view';
import ElementListView from 'data-products/element-list/element-list-view';
import { KilometerLengthsView } from 'data-products/kilometer-lengths/kilometer-lengths-view';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';
import { commonActionCreators } from 'common/common-slice';
import { getOwnUser } from 'user/user-api';
import Licenses from 'licenses/licenses';
import PublicationLog from 'publication/log/publication-log';
import { PublicationDetailsContainer } from 'publication/publication-details-container';
import { purgePersistentState } from 'index';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { VIEW_GEOMETRY } from 'user/user-model';

type MainProps = {
    layoutMode: LayoutMode;
    version: string | undefined;
};

const Main: React.FC<MainProps> = (props: MainProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles.main}>
            <EnvRestricted restrictTo="test">
                <div className={styles['main__env-banner']}>{t('environment.test')}</div>
            </EnvRestricted>
            <AppBar />
            <div className={styles.main__content} qa-id="main-content-container">
                <Routes>
                    <Route path="/" element={<FrontpageContainer />} />
                    <Route path={'/publications'} element={<PublicationLog />} />
                    <Route
                        path={'/publications/:publicationId'}
                        element={<PublicationDetailsContainer />}
                    />
                    <Route
                        path="/track-layout"
                        element={
                            props.layoutMode == 'DEFAULT' ? (
                                <TrackLayoutContainer />
                            ) : (
                                <PreviewContainer />
                            )
                        }
                    />
                    <Route path="/infra-model/*" element={<InfraModelMainView />} />
                    <Route path="/design-lib-demo" element={<GeoviiteLibDemo />} />
                    <Route path="/localization-demo" element={<I18nDemo />} />
                    <Route path="/data-products/element-list" element={<ElementListView />} />
                    <Route
                        path="/data-products/vertical-geometry"
                        element={<VerticalGeometryView />}
                    />
                    <Route
                        path="/data-products/kilometer-lengths"
                        element={<KilometerLengthsView />}
                    />
                    <Route path="/licenses" element={<Licenses />} />
                </Routes>
            </div>
            <ToastContainer
                transition={Slide}
                position="top-center"
                closeButton={false}
                draggable={false}
                hideProgressBar={true}
                pauseOnFocusLoss={false}
                limit={3}
            />
            {props.version && <VersionHolderView version={props.version} />}
        </div>
    );
};

export const MainContainer: React.FC = () => {
    const { t } = useTranslation();
    const mapDelegates = createDelegates(trackLayoutActionCreators);

    const layoutMode = useTrackLayoutAppSelector((state) => state.layoutMode);
    const commonAppData = useCommonDataAppSelector((state) => state);
    const versionInStore = commonAppData.version;
    const versionStatus = commonAppData.versionStatus;
    const versionFromBackend = useEnvironmentInfo()?.releaseVersion;
    const delegates = React.useMemo(() => createDelegates(commonActionCreators), []);

    React.useEffect(() => {
        getOwnUser().then((user) => {
            delegates.setUser(user);

            if (!user.role.privileges.map((priv) => priv.code).includes(VIEW_GEOMETRY)) {
                mapDelegates.showLayers(['virtual-hide-geometry-layer']);
            }
        });
    }, []);

    React.useEffect(() => {
        if (typeof versionFromBackend == 'string') {
            delegates.setVersionStatus(
                !versionInStore || versionInStore === versionFromBackend ? 'ok' : 'reload',
            );

            if (!versionInStore) {
                delegates.setVersion(versionFromBackend);
            }
        }
    }, [versionFromBackend]);

    const props = {
        layoutMode: layoutMode,
        version: versionInStore || versionFromBackend,
    };

    return (
        <React.Fragment>
            {versionStatus == 'reload' && (
                <Dialog
                    allowClose={false}
                    title={t('version.geoviite-updated')}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => {
                                    purgePersistentState();
                                    location.reload();
                                }}>
                                {t('version.clear-cache')}
                            </Button>
                        </div>
                    }>
                    {t('version.cache-needs-clearing')}
                </Dialog>
            )}

            {versionStatus == 'ok' && <Main {...props} />}
        </React.Fragment>
    );
};

export default withTranslation()(MainContainer);
