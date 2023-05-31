import * as React from 'react';
import { Link } from 'vayla-design-lib/link/link';
import styles from 'infra-model/velho/velho-file-list.scss';
import { getVelhoRedirectUrl } from 'infra-model/infra-model-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid } from 'common/common-model';

type VelhoRedirectLinkProps = {
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const VelhoRedirectLink: React.FC<VelhoRedirectLinkProps> = ({ oid, children }) => {
    return (
        <Link
            className={styles['velho-file-list__link']}
            onClick={() =>
                getVelhoRedirectUrl(oid).then((url) => {
                    if (url) window.location.href = url;
                })
            }
            target={'_blank'}>
            <span>{children}</span>
            <Icons.ExternalLink color={IconColor.INHERIT} size={IconSize.SMALL} />
        </Link>
    );
};
