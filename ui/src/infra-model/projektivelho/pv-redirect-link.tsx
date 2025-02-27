import * as React from 'react';
import styles from 'infra-model/projektivelho/pv-file-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid } from 'common/common-model';
import { getPVFilesRedirectUrl } from 'infra-model/infra-model-api';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

interface PVDocumentRedirectLinkProps {
    documentOid: Oid;
    assignmentOid: Oid;
    projectOid: Oid;
    projectGroupOid?: never;
}

interface PVAssignmentRedirectLinkProps {
    assignmentOid: Oid;
    projectOid: Oid;
    documentOid?: never;
    projectGroupOid?: never;
}

interface PVProjectGroupLinkProps {
    projectGroupOid: Oid;
    assignmentOid?: never;
    projectOid?: never;
    documentOid?: never;
}

interface PVProjectLinkProps {
    projectOid: Oid;
    projectGroupOid?: never;
    assignmentOid?: never;
    documentOid?: never;
}

type PVRedirectLinkProps = (
    | PVDocumentRedirectLinkProps
    | PVAssignmentRedirectLinkProps
    | PVProjectGroupLinkProps
    | PVProjectLinkProps
) & { children?: React.ReactNode };

export const PVRedirectLink: React.FC<PVRedirectLinkProps> = ({
    projectOid,
    projectGroupOid,
    assignmentOid,
    documentOid,
    children,
}) => {
    const url = getPVFilesRedirectUrl(projectGroupOid, projectOid, assignmentOid, documentOid);

    return (
        <AnchorLink
            className={styles['projektivelho-file-list__link']}
            href={url}
            target={'_blank'}>
            <span>
                {children}{' '}
                <span className={styles['projektivelho-file-list__link-icon']}>
                    <Icons.ExternalLink color={IconColor.INHERIT} size={IconSize.SMALL} />
                </span>
            </span>
        </AnchorLink>
    );
};
