import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from 'infra-model/velho/velho-file-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Oid } from 'common/common-model';
import { useTranslation } from 'react-i18next';

type VelhoOidProps = {
    oid: Oid;
} & React.HTMLProps<HTMLAnchorElement>;

export const VelhoOid: React.FC<VelhoOidProps> = ({ oid }) => {
    const { t } = useTranslation();
    return (
        <span className={styles['velho-file-list__oid']}>
            {oid}
            <Icons.Copy
                color={IconColor.INHERIT}
                size={IconSize.SMALL}
                onClick={() => {
                    navigator.clipboard.writeText(oid);
                    Snackbar.success(t('velho.file-list.oid-copied-to-clipboard'), oid);
                }}
            />
        </span>
    );
};
