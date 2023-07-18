import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';

type PublicationTableDetailsProps = {
    id: string;
    // TODO Move somewhere else
    items: {
        propKey: string;
        oldValue: string;
        newValue: string;
        remarks:
            | {
                  key: string;
                  value: string;
              }
            | undefined;
    }[];
};

export const PublicationTableDetails: React.FC<PublicationTableDetailsProps> = ({ id, items }) => {
    const { t } = useTranslation();
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
                        <td>{item.oldValue}</td>
                        <td>{item.newValue}</td>
                        <td>
                            {item.remarks
                                ? t(`publication-details-table.remark.${item.remarks.key}`, [
                                      item.remarks.value,
                                  ])
                                : undefined}
                        </td>
                    </tr>
                ))}
            </tbody>
        </Table>
    );
};
