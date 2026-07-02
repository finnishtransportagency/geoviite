import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import fi from "./fi";
import en from "./en";

i18n.use(initReactI18next).init({
  resources: { fi, en },
  lng: "fi",
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export default i18n;
