import * as React from 'react';
import { Link } from 'vayla-design-lib/link/link';
import styles from 'infra-model/projektivelho/pv-file-list.scss';
import { getPVRedirectUrl } from 'infra-model/infra-model-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid, TimeStamp } from 'common/common-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type PVRedirectLinkProps = {
    changeTime: TimeStamp;
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const PVRedirectLink: React.FC<PVRedirectLinkProps> = ({ changeTime, oid, children }) => {
    const [url, loaderStatus] = useLoaderWithStatus(() => getPVRedirectUrl(changeTime, oid), [oid]);
    return (
        <React.Fragment>
            <Link
                className={styles['projektivelho-file-list__link']}
                href={url || undefined}
                target={'_blank'}>
                <span>{children}</span>
                <span className={styles['projektivelho-file-list__link-icon']}>
                    {loaderStatus === LoaderStatus.Ready ? (
                        <Icons.ExternalLink color={IconColor.INHERIT} size={IconSize.SMALL} />
                    ) : (
                        <Spinner />
                    )}
                </span>
            </Link>
        </React.Fragment>
    );
};
