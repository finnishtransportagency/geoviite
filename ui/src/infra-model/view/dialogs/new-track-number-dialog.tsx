import * as React from 'react';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { useTranslation } from 'react-i18next';
import { debounce } from 'ts-debounce';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { isEmpty, isEqualWithoutWhitespace } from 'utils/string-utils';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { createTrackNumber } from 'track-layout/track-layout-api';
import { ZERO_TRACK_METER } from 'common/common-model';
import { TrackNumberSaveRequest } from 'tool-panel/track-number/dialog/track-number-edit-store';
import { formatTrackMeter } from 'utils/geography-utils';

type NewProjectDialogProps = {
    trackNumbers: LayoutTrackNumber[] | undefined;
    onClose: () => void;
    onInsert?: (trackNumberId: LayoutTrackNumberId) => void;
};

export const NewTrackNumberDialog: React.FC<NewProjectDialogProps> = ({
    trackNumbers,
    onClose,
    onInsert,
}) => {
    const { t } = useTranslation();
    const [trackNumberName, setTrackNumberName] = React.useState<string>('');
    const [trackNumberDescription, setTrackNumberDescription] = React.useState<string>('');
    const [duplicateName, setDuplicateName] = React.useState<boolean>(false);
    const [emptyDescription, setEmptyDescription] = React.useState<boolean>(true);
    const [emptyName, setEmptyName] = React.useState<boolean>(false);

    const [saveInProgress, setSaveInProgress] = React.useState<boolean>(false);

    const debouncer = React.useCallback(
        debounce((newName: string) => {
            const existingTrackNumber = trackNumbers?.find((tn) =>
                isEqualWithoutWhitespace(tn.number, newName),
            );
            if (existingTrackNumber) setDuplicateName(true);
        }, 300),
        [trackNumbers],
    );

    const getErrorMessage = () => {
        if (duplicateName) {
            return [t('im-form.duplicate-track-number-name')];
        }
    };

    const getDescriptionErrorMessage = () => {
        if (emptyDescription) {
            return [t('im-form.empty-track-number-description')];
        }
    };

    const onNameChange = (name: string) => {
        setTrackNumberName(name);
        setDuplicateName(false);
        debouncer(name);
        isEmpty(name) ? setEmptyName(true) : setEmptyName(false);
    };

    const onDescriptionChange = (desc: string) => {
        setTrackNumberDescription(desc);
        isEmpty(desc) ? setEmptyDescription(true) : setEmptyDescription(false);
    };

    const saveTrackNumber = () => {
        setSaveInProgress(true);
        createTrackNumber({
            number: trackNumberName,
            description: trackNumberDescription,
            state: 'NOT_IN_USE',
            startAddress: formatTrackMeter(ZERO_TRACK_METER),
        } as TrackNumberSaveRequest).then((tn) => {
            setSaveInProgress(false);

            if (tn) {
                Snackbar.success(t('im-form.new-track-number-created'));
                onInsert && onInsert(tn);
                onClose();
            } else {
                Snackbar.error(t('im-form.new-track-number-creation-failed'));
            }
        });
    };

    return (
        <Dialog
            title={t('im-form.new-track-number-dialog-title')}
            onClose={onClose}
            footerContent={
                <React.Fragment>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        icon={Icons.Delete}
                        disabled={saveInProgress}
                        onClick={onClose}>
                        {t('im-form.dialog-cancel-button')}
                    </Button>
                    <Button
                        disabled={
                            !(!duplicateName && !emptyName && !emptyDescription) || saveInProgress
                        }
                        isProcessing={saveInProgress}
                        onClick={saveTrackNumber}>
                        {t('im-form.dialog-create-button')}
                    </Button>
                </React.Fragment>
            }>
            <FormLayoutColumn>
                <FieldLayout
                    label={t('im-form.tracknumberfield')}
                    value={
                        <TextField
                            value={trackNumberName}
                            onChange={(e) => onNameChange(e.target.value)}
                            disabled={saveInProgress}
                            hasError={duplicateName}
                            wide
                        />
                    }
                    errors={getErrorMessage()}
                />
                <FieldLayout
                    label={t('im-form.new-track-number-description')}
                    value={
                        <TextField
                            value={trackNumberDescription}
                            onChange={(e) => onDescriptionChange(e.target.value)}
                            disabled={saveInProgress}
                            hasError={emptyDescription}
                            wide
                        />
                    }
                    errors={getDescriptionErrorMessage()}
                />
            </FormLayoutColumn>
        </Dialog>
    );
};

export default NewTrackNumberDialog;
