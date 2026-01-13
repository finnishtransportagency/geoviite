import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useTranslation } from 'react-i18next';
import { RatkoAssetType, RatkoPushError, RatkoPushErrorAsset } from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useLayoutDesign } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { useEnvironmentInfo } from 'environment/environment-info';

type RatkoPushErrorDetailsProps = {
    error: RatkoPushError;
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
    error,
    failedPublication,
}) => {
    const { t } = useTranslation();
    const environmentInfo = useEnvironmentInfo();

    const design = useLayoutDesign(
        getChangeTimes().layoutDesign,
        failedPublication.layoutBranch.branch,
    )?.name;

    const isConnectionIssue = failedPublication.ratkoPushStatus === 'CONNECTION_ISSUE';
    const isInternalError = error.errorType === 'INTERNAL';
    const isFetchError = error.operation === 'FETCH_EXISTING';

    const ratkoFetchErrorString = t('publication-card.push-error.ratko-fetch-error', {
        assetType: t(assetTranslationKeyByType(error)),
        name: assetNameByType(error),
        operation: t(`enum.RatkoPushErrorOperation.${error.operation}`),
    });

    const ratkoErrorString = t('publication-card.push-error.ratko-error', {
        assetType: t(assetTranslationKeyByType(error)),
        errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
        name: assetNameByType(error),
        operation: t(`enum.RatkoPushErrorOperation.${error.operation}`),
    });

    const internalErrorString = t('publication-card.push-error.internal-error', {
        assetType: t(assetTranslationKeyByType(error)),
        errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
        name: assetNameByType(error),
        operation: t(`enum.RatkoPushErrorOperation.${error.operation}`),
        geoviiteSupportEmail: environmentInfo?.geoviiteSupportEmailAddress,
    });

    const pushErrorString = (): string => {
        if (isConnectionIssue) return 'publication-card.push-error.connection-issue';
        else if (isInternalError) return internalErrorString;
        else if (isFetchError) return ratkoFetchErrorString;
        else return ratkoErrorString;
    };

    return (
        <div className={styles['ratko-push-error']}>
            {design && <span className={styles['ratko-push-error__design-name']}>{design}: </span>}
            {pushErrorString()}
        </div>
    );
};
