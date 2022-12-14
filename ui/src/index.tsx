import * as React from 'react';
import * as ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { HashRouter } from 'react-router-dom';
import { MainContainer } from 'main/main';
import { trackLayoutStore } from 'store/store';
import './style.scss';
import 'normalize.css';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import { Helmet } from 'react-helmet';

import opensans400 from '@fontsource/open-sans/files/open-sans-all-400-normal.woff';
import opensans600 from '@fontsource/open-sans/files/open-sans-all-600-normal.woff';

const persistor = persistStore(trackLayoutStore);

const rootElement = document.getElementById('root');
if (rootElement) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
        <>
        <Helmet>
            <link rel="preload" as="font" href={opensans400} type="font/woff"/>
            <link rel="preload" as="font" href={opensans600} type="font/woff"/>
        </Helmet>
        <Provider store={trackLayoutStore}>
            <PersistGate loading={null} persistor={persistor}>
                <HashRouter>
                    <MainContainer />
                </HashRouter>
            </PersistGate>
        </Provider>
        </>
    );
}
