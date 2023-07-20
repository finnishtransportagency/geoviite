import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import { PublicationChange } from 'publication/publication-model';

type PublicationTableDetailsProps = {
    id: string;
    items: PublicationChange[];
};

const enumTranslationKey = (enumKey: string, value: string) => `enum.${enumKey}.${value}`;

export const PublicationTableDetails: React.FC<PublicationTableDetailsProps> = ({ id, items }) => {
    const { t } = useTranslation();

    function formatValue(value: string | boolean | null, enumKey: string | null): string | null {
        if (typeof value === 'boolean') {
            return value ? t('yes') : t('no');
        } else if (enumKey && value) {
            return t(enumTranslationKey(enumKey, value));
        } else {
            return value;
        }
    }

    return (
        <Table wide>
            <thead>
                <tr>
                    <Th>{t('publication-details-table.property')}</Th>
                    <Th>{t('publication-details-table.old-value')}</Th>
                    <Th>{t('publication-details-table.new-value')}</Th>
                    <Th>{t('publication-details-table.remarks')}</Th>
                </tr>
            </thead>
            <tbody>
                {items.map((item) => (
                    <tr key={`${id}_${item.propKey}`}>
                        <td>{t(`publication-details-table.prop.${item.propKey}`)}</td>
                        <td>{formatValue(item.oldValue, item.enumKey)}</td>
                        <td>{formatValue(item.newValue, item.enumKey)}</td>
                        <td>
                            {item.remark
                                ? t(`publication-details-table.remark.${item.remark.key}`, [
                                      item.remark.value,
                                  ])
                                : undefined}
                        </td>
                    </tr>
                ))}
            </tbody>
        </Table>
    );
};
