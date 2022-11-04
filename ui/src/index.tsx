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

const persistor = persistStore(trackLayoutStore);

const rootElement = document.getElementById('root');
if (rootElement) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
        <Provider store={trackLayoutStore}>
            <PersistGate loading={null} persistor={persistor}>
                <HashRouter>
                    <MainContainer />
                </HashRouter>
            </PersistGate>
        </Provider>,
    );
}
