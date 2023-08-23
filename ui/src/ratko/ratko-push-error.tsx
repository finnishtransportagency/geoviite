import * as React from 'react';
import { PublicationDetails } from 'publication/publication-model';
import styles from 'ratko/ratko-push-error.scss';
import { useLoader } from 'utils/react-utils';
import { getRatkoPushError } from 'ratko/ratko-api';
import { useTranslation } from 'react-i18next';
import { RatkoAssetType, RatkoPushErrorAsset, RatkoPushStatus } from 'ratko/ratko-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type RatkoPushErrorDetailsProps = {
    latestFailure: PublicationDetails;
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

export const RatkoPushErrorDetails: React.FC<RatkoPushErrorDetailsProps> = ({ latestFailure }) => {
    const { t } = useTranslation();
    const error = useLoader(() => getRatkoPushError(latestFailure.id), [latestFailure]);

    if (!error) {
        return <React.Fragment />;
    }

    return (
        <div className={styles['ratko-push-error']}>
            {error
                ? `${assetTypeAndName(error)} ${t(
                      `enum.ratko-push-error-type.${error.errorType}`,
                  )} ${t(`enum.ratko-push-error-operation.${error.operation}`)} epäonnistui`
                : latestFailure &&
                  latestFailure.ratkoPushStatus === RatkoPushStatus.CONNECTION_ISSUE
                ? `Yhteysvirhe Ratko-viennissä`
                : ''}
        </div>
    );
};
