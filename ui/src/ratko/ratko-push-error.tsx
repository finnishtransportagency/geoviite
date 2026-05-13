import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useTranslation } from 'react-i18next';
import {
    isAssetError,
    RatkoAssetType,
    RatkoPushError,
    RatkoPushErrorAsset,
} from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useLayoutDesign, useLocationTrack, useSwitch, useTrackNumber } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { useEnvironmentInfo } from 'environment/environment-info';
import { officialMainLayoutContext } from 'common/common-model';

type RatkoPushErrorDetailsProps = {
    error: RatkoPushError;
    failedPublication: PublicationDetails | undefined;
};

const assetTranslationKeyByType = (assetType: RatkoAssetType) => {
    switch (assetType) {
        case RatkoAssetType.LOCATION_TRACK:
            return `publication-card.push-error.location-track`;
        case RatkoAssetType.TRACK_NUMBER:
            return `publication-card.push-error.track-number`;
        case RatkoAssetType.SWITCH:
            return `publication-card.push-error.switch`;
        default:
            return exhaustiveMatchingGuard(assetType);
    }
};

function useAssetName(errorAsset: RatkoPushErrorAsset | undefined): string | undefined {
    const context = officialMainLayoutContext();
    const locationTrack = useLocationTrack(
        errorAsset?.assetType === RatkoAssetType.LOCATION_TRACK ? errorAsset.assetId : undefined,
        context,
    );
    const layoutSwitch = useSwitch(
        errorAsset?.assetType === RatkoAssetType.SWITCH ? errorAsset.assetId : undefined,
        context,
    );
    const trackNumber = useTrackNumber(
        errorAsset?.assetType === RatkoAssetType.TRACK_NUMBER ? errorAsset.assetId : undefined,
        context,
    );

    if (!errorAsset) return undefined;
    switch (errorAsset.assetType) {
        case RatkoAssetType.LOCATION_TRACK:
            return locationTrack?.name;
        case RatkoAssetType.SWITCH:
            return layoutSwitch?.name;
        case RatkoAssetType.TRACK_NUMBER:
            return trackNumber?.number;
        default:
            return exhaustiveMatchingGuard(errorAsset);
    }
}

export const RatkoPushErrorDetails: React.FC<RatkoPushErrorDetailsProps> = ({
    error,
    failedPublication,
}) => {
    const { t } = useTranslation();
    const environmentInfo = useEnvironmentInfo();

    const design = useLayoutDesign(
        getChangeTimes().layoutDesign,
        failedPublication?.layoutBranch.branch ?? 'MAIN',
    )?.name;

    const errorAsset = isAssetError(error) ? error : undefined;
    const assetName = useAssetName(errorAsset);

    const isConnectionIssue = failedPublication?.ratkoPushStatus === 'CONNECTION_ISSUE';
    const isInternalError = error.errorType === 'INTERNAL';
    const isFetchError = errorAsset?.operation === 'FETCH_EXISTING';

    const ratkoFetchErrorString =
        errorAsset &&
        t('publication-card.push-error.ratko-fetch-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetType)),
            name: assetName,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
        });

    const ratkoErrorString =
        errorAsset &&
        t('publication-card.push-error.ratko-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetType)),
            errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
            name: assetName,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
        });

    const internalErrorString =
        errorAsset &&
        t('publication-card.push-error.internal-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetType)),
            errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
            name: assetName,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
            geoviiteSupportEmail: environmentInfo?.geoviiteSupportEmailAddress,
        });

    const pushErrorString = (): string => {
        if (isConnectionIssue) return t('publication-card.push-error.connection-issue');
        else if (isInternalError) return internalErrorString ?? t('publication-card.push-error.general-internal-error');
        else if (isFetchError) return ratkoFetchErrorString ?? '';
        else return ratkoErrorString ?? '';
    };

    return (
        <div className={styles['ratko-push-error']}>
            {design && <span className={styles['ratko-push-error__design-name']}>{design}: </span>}
            {pushErrorString()}
        </div>
    );
};
