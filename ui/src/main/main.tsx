import { connect, Provider } from 'react-redux';
import * as React from 'react';
import 'i18n/config';
import { Route, Routes } from 'react-router-dom';
import styles from './main.module.scss';
import { TrackLayoutContainer } from 'track-layout/track-layout-container';
import { Slide, ToastContainer } from 'react-toastify';
import { I18nDemo } from 'i18n/i18n-demo';
import { AppBar } from 'app-bar/app-bar';
import { HttpStatusCodeGenerator } from 'monitoring/http-status-code-generator';
import { GeoviiteLibDemo } from 'geoviite-design-lib/demo/demo';
import { VersionHolderView } from 'version-holder/version-holder-view';
import { useAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutMode } from 'common/common-model';
import { PreviewContainer } from 'preview/preview-container';
import { FrontpageContainer } from 'frontpage/frontpage-container';
import { EnvRestricted } from 'environment/env-restricted';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { useNavigate } from 'react-router-dom';
// fontsource requires fonts to be imported somewhere in code
import '@fontsource/open-sans/400.css';
import '@fontsource/open-sans/600.css';
import { getEnvironmentInfo } from 'environment/environment-info';
import { createDelegates } from 'store/store-utils';
import { actionCreators } from 'track-layout/track-layout-slice';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import { Button } from 'vayla-design-lib/button/button';
import { InfraModelMainView } from 'infra-model/infra-model-main-view';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import { rootStore } from 'store/store';
import ElementListView from 'data-products/element-list/element-list-view';
import { KilometerLengthsView } from 'data-products/kilometer-lengths/kilometer-lengths-view';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';

type MainProps = {
    layoutMode: LayoutMode;
    version: string | undefined;
};

const persistorRoot = persistStore(rootStore);

const Main: React.VFC<MainProps> = (props: MainProps) => {
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
                    <Route path="/monitoring" element={<HttpStatusCodeGenerator />} />
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
    const navigate = useNavigate();
    const layoutMode = useTrackLayoutAppSelector((state) => state.layoutMode);
    const versionInStore = useTrackLayoutAppSelector((state) => state.version);
    const versionFromBackend = getEnvironmentInfo()?.releaseVersion;
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, actionCreators);
    const [showDialog, setShowDialog] = React.useState(false);

    React.useEffect(() => {
        setShowDialog(
            !!versionFromBackend && !!versionInStore && versionInStore !== versionFromBackend,
        );
        if (versionFromBackend && !versionInStore) delegates.setVersion(versionFromBackend || '');
    }, [versionFromBackend]);

    const props = {
        layoutMode: layoutMode,
        version: versionInStore || versionFromBackend,
    };

    return (
        <React.Fragment>
            <Provider store={rootStore}>
                <PersistGate loading={null} persistor={persistorRoot}>
                    <Main {...props} />
                </PersistGate>
            </Provider>{' '}
            {showDialog && (
                <Dialog
                    allowClose={false}
                    className={dialogStyles['dialog--wide']}
                    title={t('version.geoviite-updated')}
                    footerContent={
                        <Button
                            onClick={() => {
                                navigate('/');
                                localStorage.clear();
                                location.reload();
                            }}>
                            {t('version.clear-cache')}
                        </Button>
                    }>
                    {t('version.cache-needs-clearing')}
                </Dialog>
            )}
        </React.Fragment>
    );
};

export default connect()(MainContainer);
