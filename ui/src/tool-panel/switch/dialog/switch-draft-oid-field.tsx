import styles from './switch-edit-dialog.scss';
import React, { useState } from 'react';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Link } from 'vayla-design-lib/link/link';
import { Oid, TimeStamp } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus, useRateLimitedTwoPartEffect } from 'utils/react-utils';
import {
    getSwitchOidPresence,
    getSwitchOids,
    SwitchOidPresence,
} from 'track-layout/layout-switch-api';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { LayoutSwitchSaveRequest } from 'linking/linking-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { moveToEditLinkText } from 'tool-panel/switch/dialog/switch-edit-dialog';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { filterNotEmpty } from 'utils/array-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

const SWITCH_OID_REQUIRED_PREFIX = '1.2.246.578.3.117.';

type SwitchDraftOidFieldProps = {
    switchId: LayoutSwitchId | undefined;
    changeTime: TimeStamp;
    draftOid: Oid;
    setDraftOid: (oid: Oid) => void;
    setDraftOidExistsInRatko: (exists: boolean) => void;
    errors: string[];
    visitField: () => void;
    draftOidFieldOpen: boolean;
    setDraftOidFieldOpen: (open: boolean) => void;
    onEdit: (id: LayoutSwitchId) => void;
};

export const SwitchDraftOidField: React.FC<SwitchDraftOidFieldProps> = ({
    switchId,
    changeTime,
    draftOid,
    setDraftOid,
    setDraftOidExistsInRatko,
    errors,
    visitField,
    draftOidFieldOpen,
    setDraftOidFieldOpen,
    onEdit,
}) => {
    const { t } = useTranslation();
    const [existingSwitchOid, existingSwitchOidLoaderStatus] = useLoaderWithStatus(
        () => switchId && getSwitchOids(switchId, changeTime).then((oids) => oids['MAIN']),
        [switchId, changeTime],
    );

    const [oidPresence, setOidPresence] = useState<SwitchOidPresence>();
    const [mostRecentlyCheckedOid, setMostRecentlyCheckedOid] = useState<Oid>();

    const oidIsLocallyValid = draftOid !== '' && validateDraftOid(draftOid).length === 0;
    const loadingOidPresence = oidIsLocallyValid && draftOid !== mostRecentlyCheckedOid;
    useRateLimitedTwoPartEffect(
        () => (oidIsLocallyValid ? getSwitchOidPresence(draftOid) : undefined),
        (newOidPresence) => {
            setMostRecentlyCheckedOid(draftOid);
            setOidPresence(newOidPresence);
            setDraftOidExistsInRatko(newOidPresence.existsInRatko === true);
        },
        500,
        [draftOid],
    );

    const existingInGeoviite = oidPresence?.existsInGeoviiteAs;
    const oidPresenceErrors =
        !loadingOidPresence && oidPresence && oidIsLocallyValid
            ? [
                  oidPresence.existsInRatko === undefined
                      ? t('switch-dialog.ratko-connection-is-down')
                      : undefined,

                  oidPresence.existsInRatko === false
                      ? t('switch-dialog.oid-doesnt-exist-in-ratko')
                      : undefined,

                  existingInGeoviite?.stateCategory &&
                  existingInGeoviite.stateCategory === 'NOT_EXISTING'
                      ? t('switch-dialog.oid-in-use-deleted')
                      : undefined,

                  existingInGeoviite?.stateCategory &&
                  existingInGeoviite.stateCategory !== 'NOT_EXISTING'
                      ? t('switch-dialog.oid-in-use')
                      : undefined,
              ].filter(filterNotEmpty)
            : [];

    const combinedErrors = [...errors, ...oidPresenceErrors];

    const oidOk =
        !loadingOidPresence &&
        combinedErrors.length === 0 &&
        draftOid.length > 0 &&
        oidPresence?.existsInRatko === true;

    return existingSwitchOid !== undefined ||
        existingSwitchOidLoaderStatus !== LoaderStatus.Ready ? (
        <React.Fragment />
    ) : (
        <FieldLayout
            label={`${t('switch-dialog.switch-draft-oid')} *`}
            errors={combinedErrors}
            value={
                draftOidFieldOpen ? (
                    <React.Fragment>
                        <TextField
                            qa-id="switch-draft-oid"
                            value={draftOid}
                            onChange={(e) => setDraftOid(e.target.value)}
                            hasError={combinedErrors.length > 0}
                            onBlur={visitField}
                            wide
                        />
                        {loadingOidPresence && <Spinner inputField />}
                        {!loadingOidPresence &&
                            oidPresence &&
                            oidIsLocallyValid &&
                            existingInGeoviite && (
                                <Link
                                    className={styles['switch-edit-dialog__alert']}
                                    onClick={() => {
                                        setDraftOid('');
                                        setDraftOidFieldOpen(false);
                                        onEdit(existingInGeoviite.id);
                                    }}>
                                    {moveToEditLinkText(t, existingInGeoviite)}
                                </Link>
                            )}
                        {oidOk && (
                            <span className={styles['switch-edit-dialog__switch-oid-ok']}>
                                <Icons.Tick size={IconSize.SMALL} color={IconColor.INHERIT} />
                                {t('switch-dialog.oid-was-found-from-ratko')}
                            </span>
                        )}
                    </React.Fragment>
                ) : (
                    <div>
                        <span>{t('switch-dialog.switch-draft-oid-unset')}</span>
                        <Link style={{ float: 'right' }} onClick={() => setDraftOidFieldOpen(true)}>
                            {t('switch-dialog.open-switch-draft-field')}
                        </Link>
                    </div>
                )
            }
        />
    );
};

export function validateDraftOid(oid: Oid): FieldValidationIssue<LayoutSwitchSaveRequest>[] {
    if (oid === '') {
        return [];
    }
    const errors: FieldValidationIssue<LayoutSwitchSaveRequest>[] = [];
    if (!oid.startsWith(SWITCH_OID_REQUIRED_PREFIX)) {
        errors.push({
            field: 'draftOid',
            reason: 'invalid-draft-oid',
            type: FieldValidationIssueType.ERROR,
        });
    } else {
        const oidEnd = oid.slice(SWITCH_OID_REQUIRED_PREFIX.length);
        if (oidEnd !== `${parseInt(oidEnd)}`) {
            errors.push({
                field: 'draftOid',
                reason: 'invalid-draft-oid',
                type: FieldValidationIssueType.ERROR,
            });
        }
    }
    return errors;
}
