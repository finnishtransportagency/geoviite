import i18n from 'i18next';
import Backend from 'i18next-http-backend';
import { initReactI18next } from 'react-i18next';
import { API_URI } from 'api/api-fetch';

i18n.use(initReactI18next)
    .use(Backend)
    .init({
        backend: {
            loadPath: `${API_URI}/locale/{{lng}}.json?ns={{ns}}`,
            preload: true,
        },
        lng: 'fi',
        ns: '',
        fallbackLng: false,
        interpolation: {
            escapeValue: false,
        },
        debug: true,
    });
