import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import { PublicationChange } from 'publication/publication-model';
import styles from './publication-table.scss';

type PublicationTableDetailsProps = {
    id: string;
    changes: PublicationChange[];
};

const enumTranslationKey = (enumKey: string, value: string) => `enum.${enumKey}.${value}`;

export const PublicationTableDetails: React.FC<PublicationTableDetailsProps> = ({
    id,
    changes,
}) => {
    const { t } = useTranslation();

    function formatValue(
        value: string | boolean | undefined,
        enumKey: string | undefined,
    ): string | undefined {
        if (typeof value === 'boolean') {
            return value ? t('yes') : t('no');
        } else if (enumKey && value) {
            return t(enumTranslationKey(enumKey, value));
        } else {
            return value;
        }
    }

    return (
        <Table wide className={styles['publication-table-details']}>
            <thead>
                <tr>
                    <Th
                        transparent
                        className={styles['publication-table-details__property-header']}>
                        {t('publication-details-table.property')}
                    </Th>
                    <Th
                        transparent
                        className={styles['publication-table-details__old-value-header']}>
                        {t('publication-details-table.old-value')}
                    </Th>
                    <Th
                        transparent
                        className={styles['publication-table-details__new-value-header']}>
                        {t('publication-details-table.new-value')}
                    </Th>
                    <Th transparent className={styles['publication-table-details__remarks-header']}>
                        {t('publication-details-table.remarks')}
                    </Th>
                </tr>
            </thead>
            <tbody className={styles['publication-table__row-details-row']}>
                {changes.map((change, index) => (
                    <tr key={`${id}_detail_${index}`}>
                        <td>
                            {t(
                                `publication-details-table.prop.${change.propKey.key}`,
                                change.propKey.params,
                            )}
                        </td>
                        <td>{formatValue(change.value.oldValue, change.value.localizationKey)}</td>
                        <td>{formatValue(change.value.newValue, change.value.localizationKey)}</td>
                        <td>{change.remark}</td>
                    </tr>
                ))}
            </tbody>
        </Table>
    );
};
