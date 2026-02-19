import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './operational-point-edit-dialog.scss';

type OperationalPointRinfCodeFieldProps = {
    rinfCodeGenerated: string;
    rinfCodeOverride: string;
    onChange: (value: string) => void;
    onCommitField: (fieldName: string) => void;
    editingRinfCode: boolean;
    onEditingRinfCodeChange: (editing: boolean) => void;
    errors: string[];
};

export const OperationalPointRinfCodeField: React.FC<OperationalPointRinfCodeFieldProps> = ({
    rinfCodeGenerated,
    rinfCodeOverride,
    editingRinfCode,
    onChange,
    onCommitField,
    onEditingRinfCodeChange,
    errors,
}) => {
    const { t } = useTranslation();
    const fieldRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (editingRinfCode && fieldRef.current) {
            fieldRef.current.focus();
        }
    }, [editingRinfCode]);

    const errorStrings = errors.map((err) => t(`operational-point-dialog.validation.${err}`));

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
            errors={errorStrings}
            value={
                <TextField
                    qa-id="operational-point-rinf-code"
                    value={editingRinfCode ? rinfCodeOverride : rinfCodeGenerated}
                    disabled={!editingRinfCode}
                    ref={fieldRef}
                    onBlur={() => onCommitField('name')}
                    placeholder={
                        !editingRinfCode
                            ? t('operational-point-dialog.rinf-code-will-be-generated')
                            : ''
                    }
                    onChange={(e) => onChange(e.target.value)}
                    hasError={errorStrings.length > 0}
                    wide
                />
            }
        />
    );
};
