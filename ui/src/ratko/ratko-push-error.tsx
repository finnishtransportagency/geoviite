import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useLoader } from 'utils/react-utils';
import { getRatkoPushError } from 'ratko/ratko-api';
import { useTranslation } from 'react-i18next';
import { RatkoAssetType, RatkoPushErrorAsset } from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useLayoutDesign } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';

type RatkoPushErrorDetailsProps = {
    failedPublication: PublicationDetails;
};

const assetTranslationKeyByType = (errorAsset: RatkoPushErrorAsset) => {
    switch (errorAsset.assetType) {
        case RatkoAssetType.LOCATION_TRACK:
            return `publication-card.push-error.location-track`;
        case RatkoAssetType.TRACK_NUMBER:
            return `publication-card.push-error.track-number`;
        case RatkoAssetType.SWITCH:
            return `publication-card.push-error.switch`;
        default:
            return exhaustiveMatchingGuard(errorAsset);
    }
};

const assetNameByType = (errorAsset: RatkoPushErrorAsset) => {
    switch (errorAsset.assetType) {
        case RatkoAssetType.LOCATION_TRACK:
        case RatkoAssetType.SWITCH:
            return errorAsset.asset.name;
        case RatkoAssetType.TRACK_NUMBER:
            return errorAsset.asset.number;
        default:
            return exhaustiveMatchingGuard(errorAsset);
    }
};

export const RatkoPushErrorDetails: React.FC<RatkoPushErrorDetailsProps> = ({
    failedPublication,
}) => {
    const { t } = useTranslation();
    const error = useLoader(() => getRatkoPushError(failedPublication.id), [failedPublication]);

    const design = useLayoutDesign(
        getChangeTimes().layoutDesign,
        failedPublication.layoutBranch,
    )?.name;

    if (!error) {
        return <React.Fragment />;
    }

    const isInternalError = error.errorType === 'INTERNAL';

    const ratkoErrorString = `${t(assetTranslationKeyByType(error), { name: assetNameByType(error) })} ${t(
        `enum.ratko-push-error-type.${error.errorType}`,
    )} ${t(`enum.ratko-push-error-operation.${error.operation}`)} ${t('publication-card.push-error.failed')}`;

    const internalErrorString = t('publication-card.push-error.internal-error');

    return (
        <div className={styles['ratko-push-error']}>
            {design && <span className={styles['ratko-push-error__design-name']}>{design}: </span>}
            {failedPublication.ratkoPushStatus === 'CONNECTION_ISSUE'
                ? t('publication-card.push-error.connection-issue')
                : isInternalError
                  ? internalErrorString
                  : ratkoErrorString}
        </div>
    );
};
