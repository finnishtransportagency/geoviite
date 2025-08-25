import React from 'react';
import { ElevationMeasurementMethod as ElevationMeasurementMethodT } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type ElevationMeasurementMethodProps = {
    method: ElevationMeasurementMethodT | undefined;
    lowerCase?: boolean;
    includeTermContextForUnknownMethod?: boolean;
};

function getTranslationKey(
    method: ElevationMeasurementMethodT | undefined,
    includeTermContextForUnknownMethod: boolean,
) {
    switch (method) {
        case 'TOP_OF_SLEEPER':
        case 'TOP_OF_RAIL':
            return method;
        case undefined:
            return includeTermContextForUnknownMethod ? 'UNKNOWN_WITH_TERM_CONTEXT' : 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(method);
    }
}

const ElevationMeasurementMethod: React.FC<ElevationMeasurementMethodProps> = ({
    method,
    lowerCase = false,
    includeTermContextForUnknownMethod = false,
}: ElevationMeasurementMethodProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {elevationMeasurementMethodText(
                t,
                method,
                lowerCase,
                includeTermContextForUnknownMethod,
            )}
        </React.Fragment>
    );
};
export default ElevationMeasurementMethod;

export function elevationMeasurementMethodText(
    t: ReturnType<typeof useTranslation>['t'],
    method: ElevationMeasurementMethodT | undefined,
    lowerCase = false,
    includeTermContextForUnknownMethod = false,
) {
    const measurementMethodTranslationKey = getTranslationKey(
        method,
        includeTermContextForUnknownMethod,
    );

    return lowerCase
        ? t(`enum.ElevationMeasurementMethod.${measurementMethodTranslationKey}`).toLowerCase()
        : t(`enum.ElevationMeasurementMethod.${measurementMethodTranslationKey}`);
}
