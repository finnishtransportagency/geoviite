import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './operational-point-edit-dialog.scss';

const RINF_CODE_REGEX = /^[A-Z]{2}[0-9]{0,10}$/;

export function validateRinfCode(value: string): string | undefined {
    if (!value.startsWith('EU')) {
        return 'must-start-with-eu';
    }
    if (!RINF_CODE_REGEX.test(value)) {
        return 'invalid-rinf-code';
    }
    return undefined;
}

type OperationalPointRinfCodeFieldProps = {
    rinfCodeGenerated: string;
    rinfCodeOverride: string;
    onChange: (value: string) => void;
    editingRinfCode: boolean;
    onEditingRinfCodeChange: (editing: boolean) => void;
    hasError: boolean;
};

export const OperationalPointRinfCodeField: React.FC<OperationalPointRinfCodeFieldProps> = ({
    rinfCodeGenerated,
    rinfCodeOverride,
    onChange,
    editingRinfCode,
    onEditingRinfCodeChange,
    hasError,
}) => {
    const { t } = useTranslation();
    const fieldRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (editingRinfCode && fieldRef.current) {
            fieldRef.current.focus();
        }
    }, [editingRinfCode]);

    const validationError = validateRinfCode(rinfCodeOverride);

    const errors =
        hasError && validationError
            ? [t(`operational-point-dialog.validation.${validationError}`)]
            : [];

    return (
        <FieldLayout
            label={
                <div className={styles['operational-point-edit-dialog__rinf-code-label']}>
                    <span>{t('operational-point-dialog.rinf-code')}</span>
                    <Switch
                        checked={editingRinfCode}
                        onCheckedChange={() => onEditingRinfCodeChange(!editingRinfCode)}
                        contentOrder={'CONTENT_FIRST'}>
                        <span
                            className={
                                styles['operational-point-edit-dialog__rinf-code-switch-label']
                            }>
                            {t('operational-point-dialog.set-rinf-code-manually')}
                        </span>
                    </Switch>
                </div>
            }
            errors={errors}
            value={
                <TextField
                    qa-id="operational-point-rinf-code"
                    value={editingRinfCode ? rinfCodeOverride : rinfCodeGenerated}
                    disabled={!editingRinfCode}
                    ref={fieldRef}
                    placeholder={
                        !editingRinfCode
                            ? t('operational-point-dialog.rinf-code-will-be-generated')
                            : ''
                    }
                    onChange={(e) => onChange(e.target.value)}
                    hasError={hasError}
                    wide
                />
            }
        />
    );
};
