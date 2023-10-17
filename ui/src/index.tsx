import * as React from 'react';
import * as ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { HashRouter } from 'react-router-dom';
import { MainContainer } from 'main/main';
import { appStore } from 'store/store';
import './style.scss';
import 'normalize.css';
import { PersistGate } from 'redux-persist/integration/react';
import { persistStore } from 'redux-persist';
import { LoadingScreen } from 'geoviite-design-lib/loading-screen/loading-screen';

const persistor = persistStore(appStore);

const rootElement = document.getElementById('root');
if (rootElement) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
        <React.StrictMode>
            <React.Suspense fallback={<LoadingScreen />}>
                <Provider store={appStore}>
                    <PersistGate loading={null} persistor={persistor}>
                        <HashRouter>
                            <MainContainer />
                        </HashRouter>
                    </PersistGate>
                </Provider>
            </React.Suspense>
        </React.StrictMode>,
    );
}
