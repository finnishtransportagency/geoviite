import React from 'react';
import {
    Author,
    GeometryAlignment,
    GeometryKmPost,
    GeometryPlan,
    PlanSource,
    Project,
} from 'geometry/geometry-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { TextArea } from 'vayla-design-lib/text-area/text-area';
import Formgroup from 'infra-model/view/formgroup/formgroup';
import FormgroupContent from 'infra-model/view/formgroup/formgroup-content';
import {
    ExtraInfraModelParameters,
    InfraModelParametersProp,
    OverrideInfraModelParameters,
} from 'infra-model/infra-model-slice';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { CoordinateSystem as CoordinateSystemModel } from 'common/common-model';
import { getCoordinateSystem, getSridList } from 'common/common-api';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import { Prop } from 'utils/type-utils';
import { useTranslation } from 'react-i18next';
import { fetchAuthors, getProject } from 'geometry/geometry-api';
import { InfraModelPhaseField } from 'infra-model/view/form/fields/infra-model-phase-field';
import { InfraModelDecisionPhaseField } from 'infra-model/view/form/fields/infra-model-decision-phase-field';
import { InfraModelMeasurementMethodField } from 'infra-model/view/form/fields/infra-model-measurement-method-field';
import NewAuthorDialog from 'infra-model/view/dialogs/new-author-dialog';
import NewProjectDialog from 'infra-model/view/dialogs/new-project-dialog';
import { InfraModelVerticalCoordinateInfoboxField } from 'infra-model/view/form/fields/infra-model-vertical-coordinate-infobox-field';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import InfraModelFormChosenDateDropDowns from 'infra-model/view/form/fields/infra-model-form-chosen-date-dropdowns';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { formatDateShort } from 'utils/date-utils';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { filterNotEmpty } from 'utils/array-utils';
import { InfraModelTextField } from 'infra-model/view/form/infra-model-form-text-field';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { updateReferenceLineChangeTime, updateTrackNumberChangeTime } from 'common/change-time-api';
import { OnSelectFunction } from 'selection/selection-model';
import { ProjectDropdown } from 'infra-model/view/form/fields/infra-model-project-field';
import { ChangeTimes } from 'common/common-slice';
import { WriteRoleRequired } from 'user/write-role-required';

type InframodelViewFormContainerProps = {
    changeTimes: ChangeTimes;
    validationErrors: ValidationError<ExtraInfraModelParameters & OverrideInfraModelParameters>[];
    upLoading: boolean;
    geometryPlan: GeometryPlan;
    onInfraModelOverrideParametersChange: (
        overrideInfraModelParameters: OverrideInfraModelParameters,
    ) => void;
    onInfraModelExtraParametersChange: <TKey extends keyof ExtraInfraModelParameters>(
        infraModelExtraParameters: Prop<ExtraInfraModelParameters, TKey>,
    ) => void;
    overrideInfraModelParameters: OverrideInfraModelParameters;
    extraInframodelParameters: ExtraInfraModelParameters;
    onCommitField: (fieldName: string) => void;
    committedFields: InfraModelParametersProp[];
    onSelect: OnSelectFunction;
};

export type EditablePlanField =
    | undefined
    | 'planName'
    | 'planOid'
    | 'assignmentName'
    | 'trackNumbers'
    | 'coordinateSystem'
    | 'planPhase'
    | 'planDecisionPhase'
    | 'measurementMethod'
    | 'heightSystem'
    | 'author'
    | 'createdTime'
    | 'source';

function getKmRangePresentation(kmPosts: GeometryKmPost[]): string {
    const sorted = kmPosts
        .map((p) => p.kmNumber)
        .filter(filterNotEmpty)
        .sort((a, b) => a.localeCompare(b));
    if (sorted.length == 0) return '';
    else return `${sorted[0]} - ${sorted[sorted.length - 1]}`;
}

function profileInformationAvailable(alignments: GeometryAlignment[]): boolean {
    return alignments.some((alignment) => alignment.profile != null);
}

const InfraModelForm: React.FC<InframodelViewFormContainerProps> = ({
    changeTimes,
    validationErrors,
    upLoading,
    geometryPlan,
    onInfraModelOverrideParametersChange,
    onInfraModelExtraParametersChange,
    overrideInfraModelParameters,
    extraInframodelParameters,
    onCommitField,
    committedFields,
    onSelect,
}: InframodelViewFormContainerProps) => {
    const { t } = useTranslation();
    const [coordinateSystem, setCoordinateSystem] = React.useState<CoordinateSystemModel | null>();
    const [planSource, setPlanSource] = React.useState<PlanSource | undefined>(geometryPlan.source);
    const [sridList, setSridList] = React.useState<CoordinateSystemModel[] | null>();
    const [fieldInEdit, setFieldInEdit] = React.useState<EditablePlanField | undefined>();
    const [authors, setAuthors] = React.useState<Author[]>();
    const [showNewAuthorDialog, setShowNewAuthorDialog] = React.useState<boolean>();
    const [showNewProjectDialog, setShowNewProjectDialog] = React.useState<boolean>();
    const [showNewTrackNumberDialog, setShowNewTrackNumberDialog] = React.useState(false);
    const [trackNumberList, setTrackNumberList] = React.useState<LayoutTrackNumber[]>();
    const [project, setProject] = React.useState<Project>();

    const planSourceOptions = [
        {
            name: t('enum.plan-source.GEOMETRIAPALVELU'),
            value: 'GEOMETRIAPALVELU' as PlanSource,
        },
        {
            name: t('enum.plan-source.PAIKANNUSPALVELU'),
            value: 'PAIKANNUSPALVELU' as PlanSource,
        },
    ];

    function changeInExtraParametersField<
        TKey extends keyof ExtraInfraModelParameters,
        TValue extends ExtraInfraModelParameters[TKey],
    >(value: TValue, fieldName: TKey) {
        onInfraModelExtraParametersChange({
            key: fieldName,
            value: value,
        });
    }

    function changeInOverrideParametersField<
        TKey extends keyof OverrideInfraModelParameters,
        TValue extends OverrideInfraModelParameters[TKey],
    >(value: TValue, fieldName: TKey) {
        onInfraModelOverrideParametersChange({
            ...overrideInfraModelParameters,
            [fieldName]: value,
        });
    }

    function openAddTrackNumberDialog() {
        setShowNewTrackNumberDialog(true);
    }

    function closeAddTrackNumberDialog() {
        setShowNewTrackNumberDialog(false);
    }

    function updateTrackNumbers() {
        getTrackNumbers('DRAFT').then((trackNumbers) => setTrackNumberList(trackNumbers));
    }

    function handleDayChange(chosenDate: Date) {
        changeInOverrideParametersField(chosenDate, 'createdDate');
    }

    function getTrackNumberName() {
        const trackId = overrideInfraModelParameters.trackNumberId
            ? overrideInfraModelParameters.trackNumberId
            : geometryPlan.trackNumberId;

        return trackNumberList?.find((t) => t.id == trackId)?.number;
    }

    React.useEffect(() => {
        getSridList().then((list) => setSridList(list));
        updateTrackNumbers();
    }, []);

    React.useEffect(() => {
        updateTrackNumbers();
    }, [changeTimes]);

    React.useEffect(() => {
        overrideInfraModelParameters.projectId
            ? getProject(overrideInfraModelParameters.projectId).then(setProject)
            : setProject(geometryPlan.project);
        fetchAuthors().then((authors) => {
            const authorInList = authors.find((p) => p.id === geometryPlan.author?.id);
            setAuthors([
                ...authors,
                ...(authorInList || !geometryPlan.author ? [] : [geometryPlan.author]),
            ]);
        });
    }, [geometryPlan]);

    React.useEffect(() => {
        const srid =
            overrideInfraModelParameters.coordinateSystemSrid ||
            geometryPlan.units.coordinateSystemSrid;
        if (srid) {
            getCoordinateSystem(srid).then((cs) => {
                setCoordinateSystem(cs);
                setFieldInEdit(undefined);
            });
        } else {
            setCoordinateSystem(undefined);
            setFieldInEdit(undefined);
        }
    }, [
        overrideInfraModelParameters.coordinateSystemSrid,
        geometryPlan.units.coordinateSystemSrid,
    ]);

    function getVisibleErrorsByProp(prop: InfraModelParametersProp) {
        return committedFields.includes(prop)
            ? validationErrors
                  .filter(
                      (error) => error.field == prop && error.type === ValidationErrorType.ERROR,
                  )
                  .map((error) => {
                      return t(`im-form.${error.reason}`);
                  })
            : [];
    }

    function hasErrors(prop: InfraModelParametersProp) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    function onSelectTrackNumber(id: LayoutTrackNumberId) {
        onSelect({ trackNumbers: [id] });
    }

    function handleTrackNumberSave(id: LayoutTrackNumberId) {
        changeInOverrideParametersField(id, 'trackNumberId');
        updateReferenceLineChangeTime().then(() =>
            updateTrackNumberChangeTime().then(() => onSelectTrackNumber(id)),
        );
    }

    return (
        <React.Fragment>
            {upLoading && <div> {t('im-form.uploading-file-msg')}</div>}
            <WriteRoleRequired>
                <Formgroup>
                    <FieldLayout
                        label={t('im-form.observations-field')}
                        value={
                            <TextArea
                                value={extraInframodelParameters.message}
                                wide
                                readOnly={false}
                                maxlength={250}
                                onChange={(e) =>
                                    changeInExtraParametersField(e.currentTarget.value, 'message')
                                }
                            />
                        }
                        help={t('im-form.observations-help')}
                    />
                </Formgroup>
            </WriteRoleRequired>
            <Formgroup qa-id="im-form-project">
                <FormgroupContent title={t('im-form.project-information')}>
                    <FormgroupField
                        label={t('im-form.name-field')}
                        inEditMode={fieldInEdit === 'planName'}
                        onEdit={() => setFieldInEdit('planName')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit !== 'planName' ? (
                            project?.name
                        ) : (
                            <ProjectDropdown
                                id={geometryPlan.project.id}
                                setProject={(projectId) =>
                                    changeInOverrideParametersField(projectId, 'projectId')
                                }
                                onAddProject={() => setShowNewProjectDialog(true)}
                            />
                        )}
                    </FormgroupField>

                    <FormgroupField
                        label={t('im-form.oid-field')}
                        inEditMode={fieldInEdit === 'planOid'}
                        onEdit={() => setFieldInEdit('planOid')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit !== 'planOid' ? (
                            <FieldLayout
                                value={
                                    <InfraModelTextField hasError={hasErrors('pvDocumentOid')}>
                                        {extraInframodelParameters.pvDocumentOid
                                            ? extraInframodelParameters.pvDocumentOid
                                            : t('im-form.information-missing')}
                                    </InfraModelTextField>
                                }
                                errors={getVisibleErrorsByProp('pvDocumentOid')}
                            />
                        ) : (
                            <FieldLayout
                                value={
                                    <TextField
                                        id="inframodel_oid"
                                        value={extraInframodelParameters.pvDocumentOid}
                                        hasError={hasErrors('pvDocumentOid')}
                                        onBlur={() => onCommitField('pvDocumentOid')}
                                        onChange={(e) =>
                                            changeInExtraParametersField(
                                                e.target.value,
                                                'pvDocumentOid',
                                            )
                                        }
                                        wide
                                    />
                                }
                                help={t('im-form.oid-help')}
                                errors={getVisibleErrorsByProp('pvDocumentOid')}
                            />
                        )}
                    </FormgroupField>

                    <FormgroupField
                        label={t('im-form.company')}
                        inEditMode={fieldInEdit === 'author'}
                        onEdit={() => setFieldInEdit('author')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit === 'author' ? (
                            <FieldLayout
                                value={
                                    <Dropdown
                                        wide
                                        value={geometryPlan.author?.id}
                                        options={
                                            authors
                                                ? authors.map((author) => ({
                                                      name: author.companyName,
                                                      value: author.id,
                                                  }))
                                                : []
                                        }
                                        onChange={(authorId) => {
                                            authorId &&
                                                authorId != geometryPlan.author?.id &&
                                                changeInOverrideParametersField(
                                                    authorId,
                                                    'authorId',
                                                );
                                        }}
                                        onAddClick={() => setShowNewAuthorDialog(true)}
                                    />
                                }
                            />
                        ) : (
                            geometryPlan.author?.companyName
                        )}
                    </FormgroupField>
                </FormgroupContent>
            </Formgroup>

            <Formgroup qa-id="im-form-location">
                <FormgroupContent title={t('im-form.location-formgroup-title')}>
                    <FormgroupField
                        label={t('im-form.tracknumberfield')}
                        inEditMode={fieldInEdit === 'trackNumbers'}
                        onEdit={() => setFieldInEdit('trackNumbers')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {trackNumberList && fieldInEdit !== 'trackNumbers' ? (
                            getTrackNumberName()
                        ) : (
                            <React.Fragment>
                                <FieldLayout
                                    value={
                                        <Dropdown
                                            placeholder={t('im-form.coordinate-system-dropdown')}
                                            value={
                                                overrideInfraModelParameters.trackNumberId
                                                    ? overrideInfraModelParameters.trackNumberId
                                                    : geometryPlan.trackNumberId
                                            }
                                            options={
                                                trackNumberList
                                                    ? trackNumberList.map((tn) => ({
                                                          name: `${tn.number}`,
                                                          value: tn.id,
                                                      }))
                                                    : []
                                            }
                                            canUnselect
                                            onChange={(tn) =>
                                                changeInOverrideParametersField(tn, 'trackNumberId')
                                            }
                                            onAddClick={openAddTrackNumberDialog}
                                        />
                                    }
                                />
                            </React.Fragment>
                        )}
                    </FormgroupField>

                    <FormgroupField label={t('im-form.km-interval-field')}>
                        {getKmRangePresentation(geometryPlan.kmPosts)}
                    </FormgroupField>

                    <FormgroupField
                        label={t('im-form.coordinate-system-field')}
                        inEditMode={fieldInEdit === 'coordinateSystem'}
                        onEdit={() => setFieldInEdit('coordinateSystem')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit !== 'coordinateSystem' ? (
                            coordinateSystem ? (
                                <CoordinateSystemView coordinateSystem={coordinateSystem} />
                            ) : (
                                t('im-form.information-missing')
                            )
                        ) : (
                            <FieldLayout
                                value={
                                    <Dropdown
                                        placeholder={t('im-form.coordinate-system-dropdown')}
                                        value={coordinateSystem?.srid}
                                        options={
                                            sridList
                                                ? sridList.map((srid) => ({
                                                      name: `${srid.name} ${srid.srid}`,
                                                      value: srid.srid,
                                                  }))
                                                : []
                                        }
                                        canUnselect
                                        onChange={(srid) =>
                                            changeInOverrideParametersField(
                                                srid,
                                                'coordinateSystemSrid',
                                            )
                                        }
                                    />
                                }
                            />
                        )}
                    </FormgroupField>

                    <InfraModelVerticalCoordinateInfoboxField
                        fieldInEdit={fieldInEdit}
                        setFieldInEdit={setFieldInEdit}
                        value={
                            overrideInfraModelParameters.verticalCoordinateSystem ||
                            geometryPlan.units.verticalCoordinateSystem ||
                            ''
                        }
                        changeInOverrideParametersField={changeInOverrideParametersField}
                        getVisibleErrorsByProp={
                            profileInformationAvailable(geometryPlan.alignments)
                                ? getVisibleErrorsByProp
                                : undefined
                        }
                    />
                </FormgroupContent>
            </Formgroup>

            <Formgroup qa-id="im-form-phase-quality">
                <FormgroupContent title={t('im-form.phase-measurement-method-formgroup-title')}>
                    <InfraModelPhaseField
                        fieldInEdit={fieldInEdit}
                        setFieldInEdit={setFieldInEdit}
                        extraInframodelParameters={extraInframodelParameters}
                        changeInExtraParametersField={changeInExtraParametersField}
                    />
                    <InfraModelDecisionPhaseField
                        fieldInEdit={fieldInEdit}
                        setFieldInEdit={setFieldInEdit}
                        extraInframodelParameters={extraInframodelParameters}
                        changeInExtraParametersField={changeInExtraParametersField}
                    />
                    <InfraModelMeasurementMethodField
                        fieldInEdit={fieldInEdit}
                        setFieldInEdit={setFieldInEdit}
                        extraInframodelParameters={extraInframodelParameters}
                        changeInExtraParametersField={changeInExtraParametersField}
                    />
                    <FormgroupField
                        label={t('im-form.plan-source')}
                        inEditMode={fieldInEdit === 'source'}
                        onEdit={() => setFieldInEdit('source')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit !== 'source' ? (
                            planSource ? (
                                t(`enum.plan-source.${planSource}`)
                            ) : (
                                t('im-form.information-missing')
                            )
                        ) : (
                            <FieldLayout
                                value={
                                    <Dropdown
                                        placeholder={t('im-form.coordinate-system-dropdown')}
                                        value={planSource}
                                        options={planSourceOptions}
                                        onChange={(planSource) => {
                                            setPlanSource(planSource);
                                            changeInOverrideParametersField(
                                                planSource as PlanSource,
                                                'source',
                                            );
                                        }}
                                    />
                                }
                            />
                        )}
                    </FormgroupField>
                </FormgroupContent>
            </Formgroup>

            <Formgroup qa-id="im-form-log">
                <FormgroupContent title={t('im-form.log-formgroup-title')}>
                    <FormgroupField
                        label={t('im-form.plan-time-field')}
                        inEditMode={fieldInEdit === 'createdTime'}
                        onEdit={() => setFieldInEdit('createdTime')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit !== 'createdTime' ? (
                            (overrideInfraModelParameters.createdDate &&
                                formatDateShort(overrideInfraModelParameters.createdDate)) ||
                            (geometryPlan.planTime
                                ? formatDateShort(geometryPlan.planTime)
                                : t('im-form.information-missing'))
                        ) : (
                            <InfraModelFormChosenDateDropDowns
                                date={
                                    overrideInfraModelParameters.createdDate ||
                                    (geometryPlan.planTime ? geometryPlan.planTime : new Date())
                                }
                                handleDayChange={handleDayChange}
                            />
                        )}
                    </FormgroupField>
                </FormgroupContent>
            </Formgroup>

            {showNewAuthorDialog && (
                <NewAuthorDialog
                    authors={authors}
                    onClose={() => setShowNewAuthorDialog(false)}
                    onSave={(author) => {
                        setShowNewAuthorDialog(false);
                        changeInOverrideParametersField(author.id, 'authorId');
                    }}></NewAuthorDialog>
            )}

            {showNewProjectDialog && (
                <NewProjectDialog
                    onClose={() => setShowNewProjectDialog(false)}
                    onSave={(project) => {
                        setShowNewProjectDialog(false);
                        changeInOverrideParametersField(project.id, 'projectId');
                    }}></NewProjectDialog>
            )}
            {showNewTrackNumberDialog && trackNumberList && (
                <TrackNumberEditDialogContainer
                    onClose={closeAddTrackNumberDialog}
                    onSave={handleTrackNumberSave}
                />
            )}
        </React.Fragment>
    );
};

export default InfraModelForm;
