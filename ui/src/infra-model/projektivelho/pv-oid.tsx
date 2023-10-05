import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from 'infra-model/projektivelho/pv-file-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid } from 'common/common-model';
import { useTranslation } from 'react-i18next';

type PVOidProps = {
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const PVOid: React.FC<PVOidProps> = ({ oid }) => {
    const { t } = useTranslation();
    return (
        <span className={styles['projektivelho-file-list__oid']}>
            {oid}
            <Icons.Copy
                color={IconColor.INHERIT}
                size={IconSize.SMALL}
                onClick={() => {
                    navigator.clipboard.writeText(oid);
                    Snackbar.success(t('projektivelho.file-list.oid-copied-to-clipboard'), {
                        body: oid,
                    });
                }}
            />
        </span>
    );
};
