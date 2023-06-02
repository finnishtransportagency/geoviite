import * as React from 'react';
import { Link } from 'vayla-design-lib/link/link';
import styles from 'infra-model/velho/velho-file-list.scss';
import { getVelhoRedirectUrl } from 'infra-model/infra-model-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid, TimeStamp } from 'common/common-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type VelhoRedirectLinkProps = {
    changeTime: TimeStamp;
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const VelhoRedirectLink: React.FC<VelhoRedirectLinkProps> = ({
    changeTime,
    oid,
    children,
}) => {
    const [url, loaderStatus] = useLoaderWithStatus(
        () => getVelhoRedirectUrl(changeTime, oid),
        [oid],
    );
    return (
        <React.Fragment>
            {loaderStatus === LoaderStatus.Ready ? (
                <Link
                    className={styles['velho-file-list__link']}
                    href={url || undefined}
                    target={'_blank'}>
                    <span>{children}</span>
                    <Icons.ExternalLink color={IconColor.INHERIT} size={IconSize.SMALL} />
                </Link>
            ) : (
                <Spinner />
            )}
        </React.Fragment>
    );
};
