import styles from './switch-edit-dialog.scss';
import React, { useState } from 'react';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Link } from 'vayla-design-lib/link/link';
import { Oid, TimeStamp } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus, useRateLimitedTwoPartEffect } from 'utils/react-utils';
import {
    GeoviiteSwitchOidPresence,
    getSwitchOidPresence,
    getSwitchOids,
    SwitchOidPresence,
} from 'track-layout/layout-switch-api';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { LayoutSwitchSaveRequest } from 'linking/linking-model';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { moveToEditLinkText } from 'tool-panel/switch/dialog/switch-edit-dialog';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { filterNotEmpty } from 'utils/array-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Switch } from 'vayla-design-lib/switch/switch';

const SWITCH_OID_REQUIRED_PREFIX = '1.2.246.578.3.117.';

type ExistingSwitchEditLinkProps = {
    onEdit: (id: LayoutSwitchId) => void;
    setDraftOid: (oid: Oid) => void;
    setDraftOidFieldOpen: (open: boolean) => void;
    existingInGeoviite: GeoviiteSwitchOidPresence;
};

const ExistingSwitchEditLink: React.FC<ExistingSwitchEditLinkProps> = ({
    onEdit,
    setDraftOid,
    setDraftOidFieldOpen,
    existingInGeoviite,
}: ExistingSwitchEditLinkProps) => {
    const { t } = useTranslation();
    return (
        <Link
            className={styles['switch-edit-dialog__alert']}
            onClick={() => {
                setDraftOid('');
                setDraftOidFieldOpen(false);
                onEdit(existingInGeoviite.id);
            }}>
            {moveToEditLinkText(t, existingInGeoviite)}
        </Link>
    );
};

type SwitchDraftOidFieldProps = {
    switchId: LayoutSwitchId | undefined;
    changeTime: TimeStamp;
    draftOid: Oid;
    setDraftOid: (oid: Oid) => void;
    setDraftOidExistsInRatko: (exists: boolean) => void;
    errors: string[];
    visitField: () => void;
    isVisited: boolean;
    editingOid: boolean;
    setEditingOid: (open: boolean) => void;
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
    isVisited,
    editingOid,
    setEditingOid,
    onEdit,
}) => {
    const { t } = useTranslation();
    const [existingSwitchOid, existingSwitchOidLoaderStatus] = useLoaderWithStatus(
        () => switchId && getSwitchOids(switchId, changeTime).then((oids) => oids['MAIN']),
        [switchId, changeTime],
    );

    const [oidPresence, setOidPresence] = useState<SwitchOidPresence>();
    const [mostRecentlyCheckedOid, setMostRecentlyCheckedOid] = useState<Oid>();
    const oidFieldRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (editingOid && oidFieldRef.current) {
            oidFieldRef.current.focus();
        }
    }, [editingOid]);

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
        editingOid && !loadingOidPresence && oidPresence && oidIsLocallyValid
            ? [
                  oidPresence.existsInRatko === undefined
                      ? t('switch-dialog.ratko-connection-is-down')
                      : undefined,

                  oidPresence.existsInRatko === false
                      ? t('switch-dialog.oid-doesnt-exist-in-ratko')
                      : undefined,
              ].filter(filterNotEmpty)
            : [];

    const mandatoryFieldError =
        isVisited && editingOid && draftOid.trim().length === 0
            ? [t('switch-dialog.mandatory-field')]
            : [];

    const combinedErrors = [...errors, ...mandatoryFieldError, ...oidPresenceErrors];

    const oidPresenceWarnings =
        editingOid && !loadingOidPresence && oidPresence && oidIsLocallyValid
            ? [
                  existingInGeoviite?.stateCategory &&
                  existingInGeoviite.stateCategory === 'NOT_EXISTING' ? (
                      <React.Fragment>
                          <div>{t('switch-dialog.oid-in-use-deleted')}</div>
                          <div>
                              <ExistingSwitchEditLink
                                  onEdit={onEdit}
                                  setDraftOid={setDraftOid}
                                  setDraftOidFieldOpen={setEditingOid}
                                  existingInGeoviite={existingInGeoviite}
                              />
                          </div>
                      </React.Fragment>
                  ) : undefined,

                  existingInGeoviite?.stateCategory &&
                  existingInGeoviite.stateCategory !== 'NOT_EXISTING' ? (
                      <React.Fragment>
                          <div>{t('switch-dialog.oid-in-use')}</div>
                          <div>
                              <ExistingSwitchEditLink
                                  onEdit={onEdit}
                                  setDraftOid={setDraftOid}
                                  setDraftOidFieldOpen={setEditingOid}
                                  existingInGeoviite={existingInGeoviite}
                              />
                          </div>
                      </React.Fragment>
                  ) : undefined,
              ].filter(filterNotEmpty)
            : [];

    const oidOk =
        !loadingOidPresence &&
        combinedErrors.length === 0 &&
        draftOid.length > 0 &&
        oidPresence?.existsInRatko === true;

    const spinnerIfLoading =
        editingOid && loadingOidPresence ? <Spinner size={SpinnerSize.SMALL} inline /> : undefined;
    const oidOkIndicator = oidOk && (
        <span className={styles['switch-edit-dialog__switch-oid-ok']}>
            <Icons.Tick size={IconSize.SMALL} color={IconColor.INHERIT} />
            {t('switch-dialog.oid-was-found-from-ratko')}
        </span>
    );

    const helpComponent = spinnerIfLoading ?? oidOkIndicator;

    return existingSwitchOid !== undefined ||
        existingSwitchOidLoaderStatus !== LoaderStatus.Ready ? (
        <React.Fragment />
    ) : (
        <React.Fragment>
            <FieldLayout
                label={
                    <div className={styles['switch-edit-dialog__switch-oid-label']}>
                        <span>{`${t('switch-dialog.switch-draft-oid')} * `}</span>
                        <Switch
                            checked={editingOid}
                            onCheckedChange={() => setEditingOid(!editingOid)}
                            contentOrder={'CONTENT_FIRST'}>
                            <span className={styles['switch-edit-dialog__switch-oid-switch-label']}>
                                {t('switch-dialog.open-switch-draft-field')}
                            </span>
                        </Switch>
                    </div>
                }
                errors={combinedErrors}
                warnings={oidPresenceWarnings}
                help={helpComponent}
                value={
                    <React.Fragment>
                        <TextField
                            qa-id="switch-draft-oid"
                            value={editingOid ? draftOid : ''}
                            disabled={!editingOid}
                            ref={oidFieldRef}
                            placeholder={
                                !editingOid ? t('switch-dialog.switch-draft-oid-unset') : ''
                            }
                            onChange={(e) => setDraftOid(e.target.value)}
                            hasError={isVisited && combinedErrors.length > 0}
                            onBlur={visitField}
                            wide
                        />
                    </React.Fragment>
                }
            />
        </React.Fragment>
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
