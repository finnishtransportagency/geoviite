import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useTranslation } from 'react-i18next';
import { isAssetError, RatkoAssetRef, RatkoAssetType, RatkoPushError } from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import {
    useLayoutDesign,
    useLocationTrack,
    useSwitch,
    useTrackNumber,
} from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { useEnvironmentInfo } from 'environment/environment-info';
import { LayoutBranch, officialContext } from 'common/common-model';

type RatkoPushErrorDetailsProps = {
    error: RatkoPushError | undefined;
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

function useAssetName(assetRef: RatkoAssetRef, branch: LayoutBranch): string | undefined {
    const context = officialContext(branch);
    const locationTrack = useLocationTrack(
        assetRef?.type === RatkoAssetType.LOCATION_TRACK ? assetRef.id : undefined,
        context,
    );
    const layoutSwitch = useSwitch(
        assetRef?.type === RatkoAssetType.SWITCH ? assetRef.id : undefined,
        context,
    );
    const trackNumber = useTrackNumber(
        assetRef?.type === RatkoAssetType.TRACK_NUMBER ? assetRef.id : undefined,
        context,
    );

    switch (assetRef?.type) {
        case undefined:
            return undefined;
        case RatkoAssetType.LOCATION_TRACK:
            return locationTrack?.name;
        case RatkoAssetType.SWITCH:
            return layoutSwitch?.name;
        case RatkoAssetType.TRACK_NUMBER:
            return trackNumber?.number;
        default:
            return exhaustiveMatchingGuard(assetRef);
    }
}

function formatAssetLabel(name: string | undefined, oid: string | undefined): string | undefined {
    if (name === undefined && oid === undefined) return undefined;
    else if (name === undefined) return `OID ${oid}`;
    else if (oid === undefined) return name;
    else return `${name}, OID ${oid}`;
}

export const RatkoPushErrorDetails: React.FC<RatkoPushErrorDetailsProps> = ({
    error,
    failedPublication,
}) => {
    const { t } = useTranslation();
    const environmentInfo = useEnvironmentInfo();

    const branch = failedPublication?.layoutBranch.branch ?? 'MAIN';
    const design = useLayoutDesign(getChangeTimes().layoutDesign, branch)?.name;

    const errorAsset = error && isAssetError(error) ? error : undefined;
    const assetName = errorAsset ? useAssetName(errorAsset.assetRef, branch) : undefined;
    const assetLabel = formatAssetLabel(assetName, errorAsset?.assetRef.oid);

    if (!error) {
        return (
            <div className={styles['ratko-push-error']}>
                {t('publication-card.push-error.general-error')}
            </div>
        );
    }

    const isConnectionIssue = failedPublication?.ratkoPushStatus === 'CONNECTION_ISSUE';
    const isInternalError = error.errorType === 'INTERNAL';
    const isFetchError = errorAsset?.operation === 'FETCH_EXISTING';

    const ratkoFetchErrorString =
        errorAsset &&
        t('publication-card.push-error.ratko-fetch-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetRef.type)),
            name: assetLabel,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
        });

    const ratkoErrorString =
        errorAsset &&
        t('publication-card.push-error.ratko-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetRef.type)),
            errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
            name: assetLabel,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
        });

    const internalErrorString =
        errorAsset &&
        t('publication-card.push-error.internal-error', {
            assetType: t(assetTranslationKeyByType(errorAsset.assetRef.type)),
            errorType: t(`enum.RatkoPushErrorType.${error.errorType}`),
            name: assetLabel,
            operation: t(`enum.RatkoPushErrorOperation.${errorAsset.operation}`),
            geoviiteSupportEmail: environmentInfo?.geoviiteSupportEmailAddress,
        });

    const pushErrorString = (): string => {
        if (isConnectionIssue) return t('publication-card.push-error.connection-issue');
        if (isInternalError) {
            return (
                internalErrorString ??
                t('publication-card.push-error.general-internal-error', {
                    geoviiteSupportEmail: environmentInfo?.geoviiteSupportEmailAddress,
                })
            );
        }
        if (isFetchError)
            return ratkoFetchErrorString ?? t('publication-card.push-error.general-error');
        return ratkoErrorString ?? t('publication-card.push-error.general-error');
    };

    const technicalDetails = error.ratkoStatusCode
        ? `[${error.ratkoStatusCode}] ${error.technicalMessage}`
        : error.technicalMessage;

    return (
        <div className={styles['ratko-push-error']}>
            <div>
                {design && (
                    <span className={styles['ratko-push-error__design-name']}>{design}: </span>
                )}
                {pushErrorString()}
            </div>
            <div className={styles['ratko-push-error__technical-message']}>{technicalDetails}</div>
        </div>
    );
};
