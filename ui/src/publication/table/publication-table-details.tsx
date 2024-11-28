import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import { PublicationChange } from 'publication/publication-model';
import { enumTranslation } from 'utils/translation-utils';

type PublicationTableDetailsProps = {
    id: string;
    changes: PublicationChange[];
};

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
            return enumTranslation(t, enumKey, value);
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
                {changes.map((change) => (
                    <tr key={`${id}_${change.propKey.key}_${change.propKey.params}`}>
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
