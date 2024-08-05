import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useLoader } from 'utils/react-utils';
import { getRatkoPushError } from 'ratko/ratko-api';
import { useTranslation } from 'react-i18next';
import { RatkoAssetType, RatkoPushErrorAsset, RatkoPushStatus } from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useLayoutDesign } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';

type RatkoPushErrorDetailsProps = {
    failedPublication: PublicationDetails;
};

const assetTypeAndName = (errorAsset: RatkoPushErrorAsset) => {
    switch (errorAsset.assetType) {
        case RatkoAssetType.LOCATION_TRACK:
            return `Sijaintiraiteen (${errorAsset.asset.name})`;
        case RatkoAssetType.TRACK_NUMBER:
            return `Ratanumeron (${errorAsset.asset.number})`;
        case RatkoAssetType.SWITCH:
            return `Vaihteen (${errorAsset.asset.name})`;
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

    return (
        <div className={styles['ratko-push-error']}>
            {design && <span className={styles['ratko-push-error__design-name']}>{design}: </span>}
            {error
                ? `${assetTypeAndName(error)} ${t(
                      `enum.ratko-push-error-type.${error.errorType}`,
                  )} ${t(`enum.ratko-push-error-operation.${error.operation}`)} epäonnistui`
                : failedPublication &&
                    failedPublication.ratkoPushStatus === RatkoPushStatus.CONNECTION_ISSUE
                  ? t('publication-card.ratko-push-connection-issue')
                  : ''}
        </div>
    );
};
