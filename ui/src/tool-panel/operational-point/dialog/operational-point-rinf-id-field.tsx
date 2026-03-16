import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './operational-point-edit-dialog.scss';

type OperationalPointRinfIdFieldProps = {
    rinfIdGenerated: string;
    rinfIdOverride: string;
    onUpdateRinfId: (rinfId: string) => void;
    onCommitField: (fieldName: string) => void;
    editingRinfId: boolean;
    onEditingRinfIdChange: (editing: boolean) => void;
    errors: string[];
};

export const OperationalPointRinfIdField: React.FC<OperationalPointRinfIdFieldProps> = ({
    rinfIdGenerated,
    rinfIdOverride,
    editingRinfId,
    onUpdateRinfId,
    onCommitField,
    onEditingRinfIdChange,
    errors,
}) => {
    const { t } = useTranslation();
    const fieldRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (editingRinfId && fieldRef.current) {
            fieldRef.current.focus();
        }
    }, [editingRinfId]);

    const errorStrings = errors.map((err) => t(`operational-point-dialog.validation.${err}`));

    return (
        <FieldLayout
            label={
                <div className={styles['operational-point-edit-dialog__rinf-id-label']}>
                    <span>{t('operational-point-dialog.rinf-id')}</span>
                    <Switch
                        checked={editingRinfId}
                        onCheckedChange={() => onEditingRinfIdChange(!editingRinfId)}
                        contentOrder={'CONTENT_FIRST'}>
                        <span
                            className={
                                styles['operational-point-edit-dialog__rinf-id-switch-label']
                            }>
                            {t('operational-point-dialog.set-rinf-id-manually')}
                        </span>
                    </Switch>
                </div>
            }
            errors={errorStrings}
            value={
                <TextField
                    qa-id="operational-point-rinf-id"
                    value={editingRinfId ? rinfIdOverride : rinfIdGenerated}
                    disabled={!editingRinfId}
                    ref={fieldRef}
                    onBlur={() => onCommitField('rinfIdOverride')}
                    placeholder={
                        !editingRinfId
                            ? t('operational-point-dialog.rinf-id-will-be-generated')
                            : ''
                    }
                    onChange={(e) => onUpdateRinfId(e.target.value)}
                    hasError={errorStrings.length > 0}
                    wide
                />
            }
        />
    );
};
