import React from 'react';
import {
    GeometryAlignment,
    GeometryKmPost,
    GeometryPlan,
    PlanSource,
    Project,
} from 'geometry/geometry-model';
import styles from './infra-model-form.module.scss';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
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
import { InfraModelElevationMeasurementMethodField } from 'infra-model/view/form/fields/infra-model-elevation-measurement-method-field';
import NewAuthorDialog from 'infra-model/view/dialogs/new-author-dialog';
import NewProjectDialog from 'infra-model/view/dialogs/new-project-dialog';
import { InfraModelVerticalCoordinateInfoboxField } from 'infra-model/view/form/fields/infra-model-vertical-coordinate-infobox-field';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import InfraModelFormChosenDateDropDowns from 'infra-model/view/form/fields/infra-model-form-chosen-date-dropdowns';
import FormgroupField from 'infra-model/view/formgroup/formgroup-field';
import { formatDateShort } from 'utils/date-utils';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { filterNotEmpty } from 'utils/array-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import {
    updateProjectChangeTime,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { OnSelectFunction } from 'selection/selection-model';
import { ProjectDropdown } from 'infra-model/view/form/fields/infra-model-project-field';
import { ChangeTimes } from 'common/common-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { usePvDocumentHeader } from 'track-layout/track-layout-react-utils';
import { PVOid } from 'infra-model/projektivelho/pv-oid';
import FormgroupTextarea from 'infra-model/view/formgroup/formgroup-textarea';
import { PVRedirectLink } from 'infra-model/projektivelho/pv-redirect-link';
import { useLoader } from 'utils/react-utils';

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
    committedFields: InfraModelParametersProp[];
    onSelect: OnSelectFunction;
};

export type EditablePlanField =
    | undefined
    | 'observations'
    | 'planName'
    | 'planOid'
    | 'assignmentName'
    | 'trackNumbers'
    | 'coordinateSystem'
    | 'planPhase'
    | 'planDecisionPhase'
    | 'measurementMethod'
    | 'elevationMeasurementMethod'
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
    return alignments.some((alignment) => alignment.profile);
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
    committedFields,
    onSelect,
}: InframodelViewFormContainerProps) => {
    const { t } = useTranslation();
    const [coordinateSystem, setCoordinateSystem] = React.useState<
        CoordinateSystemModel | undefined
    >();
    const [planSource, setPlanSource] = React.useState<PlanSource | undefined>(geometryPlan.source);
    const [sridList, setSridList] = React.useState<CoordinateSystemModel[] | undefined>();
    const [fieldInEdit, setFieldInEdit] = React.useState<EditablePlanField | undefined>();
    const [showNewAuthorDialog, setShowNewAuthorDialog] = React.useState<boolean>();
    const [showNewProjectDialog, setShowNewProjectDialog] = React.useState<boolean>();
    const [showNewTrackNumberDialog, setShowNewTrackNumberDialog] = React.useState(false);
    const [trackNumberList, setTrackNumberList] = React.useState<LayoutTrackNumber[]>();
    const [project, setProject] = React.useState<Project>();
    const pvDocument = usePvDocumentHeader(geometryPlan.pvDocumentId);
    const authors = useLoader(() => fetchAuthors(), [changeTimes.author]) || [];

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

    const authorsIncludingFromPlan = () => {
        const authorInList = authors.find((p) => p.id === geometryPlan.author?.id);
        return [...authors, ...(!authorInList && geometryPlan.author ? [geometryPlan.author] : [])];
    };

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
            <WriteAccessRequired>
                <Formgroup>
                    <FieldLayout
                        label={t('im-form.observations-field')}
                        help={t('im-form.observations-help')}
                        value={
                            <FormgroupTextarea
                                label={t('im-form.observations-field')}
                                defaultDisplayedValue={t('im-form.observations-field-default')}
                                value={extraInframodelParameters.message}
                                inEditMode={fieldInEdit === 'observations'}
                                onEdit={() => setFieldInEdit('observations')}
                                onClose={() => setFieldInEdit(undefined)}
                                onChange={(e) =>
                                    changeInExtraParametersField(e.currentTarget.value, 'message')
                                }
                            />
                        }
                    />
                </Formgroup>
            </WriteAccessRequired>
            <Formgroup qa-id="im-form-project">
                {pvDocument && (
                    <FormgroupContent title={t('im-form.pv-document-information.title')}>
                        {pvDocument.projectGroup && (
                            <FormgroupField
                                label={t('im-form.pv-document-information.project-group')}>
                                <span className={styles['infra-model-upload__project-field']}>
                                    <PVOid oid={pvDocument.projectGroup.oid} />
                                    <PVRedirectLink projectGroupOid={pvDocument.projectGroup.oid}>
                                        {pvDocument.projectGroup.name}
                                    </PVRedirectLink>
                                </span>
                            </FormgroupField>
                        )}
                        {pvDocument.project && (
                            <FormgroupField label={t('im-form.pv-document-information.project')}>
                                <span className={styles['infra-model-upload__project-field']}>
                                    <PVOid oid={pvDocument.project.oid} />
                                    <PVRedirectLink projectOid={pvDocument.project.oid}>
                                        {pvDocument.project.name}
                                    </PVRedirectLink>
                                </span>
                            </FormgroupField>
                        )}
                        {pvDocument.assignment && pvDocument.project && (
                            <>
                                <FormgroupField
                                    label={t('im-form.pv-document-information.assignment')}>
                                    <span className={styles['infra-model-upload__project-field']}>
                                        <PVOid oid={pvDocument.assignment.oid} />
                                        <PVRedirectLink
                                            assignmentOid={pvDocument.assignment.oid}
                                            projectOid={pvDocument.project.oid}>
                                            {pvDocument.assignment.name}
                                        </PVRedirectLink>
                                    </span>
                                </FormgroupField>
                                <FormgroupField
                                    label={t('im-form.pv-document-information.document')}>
                                    <span className={styles['infra-model-upload__project-field']}>
                                        <PVOid oid={pvDocument.document.oid} />
                                        <PVRedirectLink
                                            documentOid={pvDocument.document.oid}
                                            assignmentOid={pvDocument.assignment.oid}
                                            projectOid={pvDocument.project.oid}>
                                            {pvDocument.document.description}
                                        </PVRedirectLink>
                                    </span>
                                </FormgroupField>
                            </>
                        )}
                    </FormgroupContent>
                )}

                <FormgroupContent title={t('im-form.file-metadata')}>
                    <FormgroupField
                        label={t('im-form.name-field')}
                        qaId="project-im-field"
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
                        label={t('im-form.company')}
                        qaId="author-im-field"
                        inEditMode={fieldInEdit === 'author'}
                        onEdit={() => setFieldInEdit('author')}
                        onClose={() => setFieldInEdit(undefined)}>
                        {fieldInEdit === 'author' ? (
                            <FieldLayout
                                value={
                                    <Dropdown
                                        wide
                                        value={geometryPlan.author?.id}
                                        options={authorsIncludingFromPlan().map((author) => ({
                                            name: author.companyName,
                                            value: author.id,
                                        }))}
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
                        qaId="track-number-im-field"
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

                    <FormgroupField
                        label={t('im-form.km-interval-field')}
                        qaId="km-interval-im-field">
                        {getKmRangePresentation(geometryPlan.kmPosts)}
                    </FormgroupField>

                    <FormgroupField
                        label={t('im-form.coordinate-system-field')}
                        qaId="coordinate-system-im-field"
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
                    <InfraModelElevationMeasurementMethodField
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
                        qaId="plan-time-im-field"
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
                    authors={authorsIncludingFromPlan()}
                    onClose={() => setShowNewAuthorDialog(false)}
                    onSave={(author) => {
                        setShowNewAuthorDialog(false);
                        changeInOverrideParametersField(author.id, 'authorId');
                    }}></NewAuthorDialog>
            )}

            {showNewProjectDialog && (
                <NewProjectDialog
                    onClose={() => setShowNewProjectDialog(false)}
                    onSave={(projectId) => {
                        setShowNewProjectDialog(false);
                        changeInOverrideParametersField(projectId, 'projectId');
                        updateProjectChangeTime();
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
