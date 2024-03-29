import i18n from 'i18next';
import Backend from 'i18next-http-backend';
import { initReactI18next } from 'react-i18next';
import { API_URI } from 'api/api-fetch';

export type LocalizationParams = { [key: string]: string };

i18n.use(initReactI18next)
    .use(Backend)
    .init({
        backend: {
            loadPath: `${API_URI}/localization/{{lng}}.json`,
            preload: true,
        },
        lng: 'fi',
        ns: '',
        fallbackLng: false,
        interpolation: {
            escapeValue: false,
        },
        missingInterpolationHandler: (_key: string, _value: object) => {
            // Uncomment if you want to debug missing values
            //console.log(_key, _value);
            return null;
        },
    });
