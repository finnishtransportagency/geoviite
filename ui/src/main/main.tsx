import { connect } from 'react-redux';
import * as React from 'react';
import 'i18n/config';
import { Route, Routes } from 'react-router-dom';
import styles from './main.module.scss';
import { TrackLayoutContainer } from 'track-layout/track-layout-container';
import { Slide, ToastContainer } from 'react-toastify';
import { I18nDemo } from 'i18n/i18n-demo';
import { AppBar } from 'app-bar/app-bar';
import { HttpStatusCodeGenerator } from 'monitoring/http-status-code-generator';
import { InfraModelMainContainerWithProvider } from 'infra-model/infra-model-main-container';
import { GeoviiteLibDemo } from 'geoviite-design-lib/demo/demo';
import { VersionHolderView } from 'version-holder/version-holder-view';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutMode } from 'common/common-model';
import { PreviewContainer } from 'preview/preview-container';
import { FrontpageContainer } from 'frontpage/frontpage-container';
import { EnvRestricted } from 'environment/env-restricted';
import { useTranslation } from 'react-i18next';
// fontsource requires fonts to be imported somewhere in code
import '@fontsource/open-sans/400.css';
import '@fontsource/open-sans/600.css';
import ElementListView from 'data-products/element-list/element-list-view';

type MainProps = {
    layoutMode: LayoutMode;
};

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
                    <Route path="/infra-model" element={<InfraModelMainContainerWithProvider />} />
                    <Route path="/design-lib-demo" element={<GeoviiteLibDemo />} />
                    <Route path="/localization-demo" element={<I18nDemo />} />
                    <Route path="/data-products-element-list" element={<ElementListView />} />
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
            <VersionHolderView />
        </div>
    );
};

export const MainContainer: React.FC = () => {
    const layoutMode = useTrackLayoutAppSelector((state) => state.trackLayout.layoutMode);

    const props = {
        layoutMode: layoutMode,
    };

    return <Main {...props} />;
};

export default connect()(MainContainer);
