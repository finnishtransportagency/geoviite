import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import commonFi from 'i18n/fi/fi.json';
import commonEn from 'i18n/en/en.json';
import infraModelFi from 'infra-model/translations.fi.json';
import selectionPanelFi from 'selection-panel/translations.fi.json';
import toolPanelFi from 'tool-panel/translations.fi.json';
import toolBarFi from 'tool-bar/translations.fi.json';
import preview from 'preview/translations.fi.json';
import publication from 'publication/translations.fi.json';
import frontpage from 'frontpage/translations.fi.json';
import map from 'map/translations.fi.json';
import switchDialogFi from 'tool-panel/switch/dialog/translations.fi.json';
import alignmentDialogFi from 'tool-panel/location-track/dialog/translations.fi.json';
import kmPostDialogFi from 'tool-panel/km-post/dialog/translations.fi.json';
import trackNumberDialogFi from 'tool-panel/track-number/dialog/translations.fi.json';
import linkingFi from 'linking/translations.fi.json';

export const resources = {
    en: {
        translation: commonEn,
    },
    fi: {
        translation: {
            ...commonFi,
            ...infraModelFi,
            ...selectionPanelFi,
            ...toolPanelFi,
            ...toolBarFi,
            ...preview,
            ...publication,
            ...frontpage,
            ...map,
            ...switchDialogFi,
            ...alignmentDialogFi,
            ...trackNumberDialogFi,
            ...kmPostDialogFi,
            ...linkingFi,
        },
    },
} as const;

i18n.use(initReactI18next).init({
    lng: 'fi',
    interpolation: {
        escapeValue: false,
    },
    resources,
});
