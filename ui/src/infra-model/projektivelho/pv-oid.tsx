import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from 'infra-model/projektivelho/pv-file-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid } from 'common/common-model';

type PVOidProps = {
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const PVOid: React.FC<PVOidProps> = ({ oid }) => {
    return (
        <span className={styles['projektivelho-file-list__oid']}>
            {oid}
            <Icons.Copy
                color={IconColor.INHERIT}
                size={IconSize.SMALL}
                onClick={() => {
                    navigator.clipboard.writeText(oid);
                    Snackbar.success('projektivelho.file-list.oid-copied-to-clipboard', oid);
                }}
            />
        </span>
    );
};
