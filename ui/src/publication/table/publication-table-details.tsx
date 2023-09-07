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
                    <tr key={`${id}_${item.propKey.key}_${item.propKey.params}`}>
                        <td>
                            {t(
                                `publication-details-table.prop.${item.propKey.key}`,
                                item.propKey.params,
                            )}
                        </td>
                        <td>{formatValue(item.value.oldValue, item.value.localizationKey)}</td>
                        <td>{formatValue(item.value.newValue, item.value.localizationKey)}</td>
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
