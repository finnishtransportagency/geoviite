import * as React from 'react';
import { useTranslation } from 'react-i18next';

export type ValidationStateRowProps = {
    errorTexts: string[];
    warningTexts: string[];
};

export const ValidationStateRow: React.FC<ValidationStateRowProps> = ({
    errorTexts,
    warningTexts,
}) => {
    const { t } = useTranslation();

    return (
        <tr className={'preview-table-item preview-table-item--error'}>
            <td colSpan={7}>
                {errorTexts.length > 0 && (
                    <div className="preview-table-item__msg-group preview-table-item__msg-group--errors">
                        <div className="preview-table-item__group-title">
                            {t('preview-table.errors-group-title')}
                        </div>
                        {errorTexts.map((errorText, index) => (
                            <div key={index} className="preview-table-item__msg">
                                {errorText}
                            </div>
                        ))}
                    </div>
                )}
                {warningTexts.length > 0 && (
                    <div className="preview-table-item__msg-group preview-table-item__msg-group--warnings">
                        <div className="preview-table-item__group-title">
                            {t('preview-table.warnings-group-title')}
                        </div>
                        {warningTexts?.map((warningText, index) => (
                            <div key={index} className="preview-table-item__msg">
                                {warningText}
                            </div>
                        ))}
                    </div>
                )}
            </td>
        </tr>
    );
};
