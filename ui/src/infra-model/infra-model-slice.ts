import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { Map, MapLayerType } from 'map/map-model';
import { initialMapState, mapReducers } from 'map/map-store';
import {
    infraModelListReducers,
    InfraModelListState,
    initialInfraModelListState,
} from 'infra-model/list/infra-model-list-store';
import { Selection } from 'selection/selection-model';
import { wrapReducers } from 'store/store-utils';
import { initialSelectionState, selectionReducers } from 'selection/selection-store';
import {
    AuthorId,
    DecisionPhase,
    GeometryPlan,
    PlanPhase,
    PlanSource,
    ProjectId,
} from 'geometry/geometry-model';
import { GeometryPlanLayout, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { SerializableFile } from 'utils/file-utils';
import { validateOid, ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    MeasurementMethod,
    Message,
    Oid,
    Srid,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { Prop } from 'utils/type-utils';

export enum InfraModelViewType {
    UPLOAD,
    EDIT,
}

export enum InfraModelTabType {
    PLAN,
    WAITING,
    REJECTED,
}

export type InfraModelState = {
    map: Map;
    infraModelList: InfraModelListState;
    selection: Selection;
    validationResponse: ValidationResponse | null;
    file: SerializableFile | undefined;
    extraInfraModelParameters: ExtraInfraModelParameters;
    overrideInfraModelParameters: OverrideInfraModelParameters;
    validationErrors: ValidationError<InfraModelParameters>[];
    committedFields: InfraModelParametersProp[];
    infraModelActiveTab: InfraModelTabType;
};

export type ExtraInfraModelParameters = {
    pvDocumentOid: Oid | undefined;
    planPhase: PlanPhase | undefined;
    decisionPhase: DecisionPhase | undefined;
    measurementMethod: MeasurementMethod | undefined;
    message: Message | undefined;
};

export type XmlCharset = 'US_ASCII' | 'UTF_16LE' | 'UTF_16' | 'UTF_16BE' | 'UTF_8' | 'ISO_8859_1';
export type OverrideInfraModelParameters = {
    coordinateSystemSrid: Srid | undefined;
    projectId?: ProjectId;
    authorId?: AuthorId;
    verticalCoordinateSystem: VerticalCoordinateSystem | undefined;
    trackNumberId?: LayoutTrackNumberId;
    createdDate: Date | undefined;
    encoding?: XmlCharset;
    source: PlanSource | undefined;
};

export type InfraModelParameters = ExtraInfraModelParameters & OverrideInfraModelParameters;

export type InfraModelParametersProp = keyof InfraModelParameters;

export type LocalizationKey = string;

export type ErrorType =
    | 'REQUEST_ERROR'
    | 'PARSING_ERROR'
    | 'TRANSFORMATION_ERROR'
    | 'VALIDATION_ERROR'
    | 'OBSERVATION_MAJOR'
    | 'OBSERVATION_MINOR';
export interface CustomValidationError {
    localizationKey: LocalizationKey;
    errorType: ErrorType;
}

export interface ValidationResponse {
    validationErrors: CustomValidationError[];
    geometryPlan: GeometryPlan | null;
    planLayout: GeometryPlanLayout | null;
}

const visibleMapLayerTypes: MapLayerType[] = [
    'tile',
    'alignment',
    'switches',
    'kmPosts',
    'geometry',
    'geometrySwitches',
    'geometryKmPosts',
];

export const initialInfraModelState: InfraModelState = {
    map: {
        ...initialMapState,
        mapLayers: initialMapState.mapLayers.map((layer) => ({
            ...layer,
            visible: visibleMapLayerTypes.includes(layer.type),
        })),
    },
    infraModelList: initialInfraModelListState,
    selection: {
        ...initialSelectionState,
        selectionModes: ['segment', 'switch'],
    },
    validationResponse: null,
    file: undefined,
    extraInfraModelParameters: {
        pvDocumentOid: undefined,
        planPhase: undefined,
        decisionPhase: undefined,
        measurementMethod: undefined,
        message: undefined,
    },
    overrideInfraModelParameters: {
        coordinateSystemSrid: undefined,
        verticalCoordinateSystem: undefined,
        createdDate: undefined,
        source: undefined,
    },
    validationErrors: [],
    committedFields: [],
    infraModelActiveTab: InfraModelTabType.PLAN,
};

const infraModelSlice = createSlice({
    name: 'infraModel',
    initialState: initialInfraModelState,
    reducers: {
        onPlanValidated: (
            state: InfraModelState,
            { payload: response }: PayloadAction<ValidationResponse>,
        ) => {
            state.validationResponse = response;

            if (response.planLayout) {
                state.selection.planLayouts = [response.planLayout];
                const bBox = response.planLayout.boundingBox;
                state.map.viewport = {
                    ...state.map.viewport,
                    source: undefined,
                    center: {
                        x: (bBox.x.min + bBox.x.max) / 2.0,
                        y: (bBox.y.min + bBox.y.max) / 2.0,
                    },
                    resolution: 30,
                };
            }
        },
        onInfraModelExtraParametersChange: function <TKey extends keyof ExtraInfraModelParameters>(
            state: InfraModelState,
            { payload: propEdit }: PayloadAction<Prop<ExtraInfraModelParameters, TKey>>,
        ) {
            state.extraInfraModelParameters[propEdit.key] = propEdit.value;
            state.validationErrors = validateParams(
                state.validationResponse?.geometryPlan || null,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );
            if (
                !state.committedFields.includes(propEdit.key) &&
                !state.validationErrors.some((error) => error.field == propEdit.key)
            ) {
                // Valid value entered for a field, mark that field as committed
                state.committedFields = [...state.committedFields, propEdit.key];
            }
        },
        onInfraModelOverrideParametersChange: (
            state: InfraModelState,
            { payload }: PayloadAction<OverrideInfraModelParameters>,
        ) => {
            state.overrideInfraModelParameters = { ...payload };
            state.validationErrors = validateParams(
                state.validationResponse?.geometryPlan || null,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );
        },
        onCommitField: (
            state: InfraModelState,
            { payload: key }: PayloadAction<InfraModelParametersProp>,
        ) => {
            state.committedFields = [...state.committedFields, key];
        },
        setInfraModelFile: (
            state: InfraModelState,
            { payload: file }: PayloadAction<SerializableFile>,
        ) => {
            state.file = file;

            state.validationResponse = initialInfraModelState.validationResponse;
            state.selection = initialSelectionState;
            state.map.viewport = initialMapState.viewport;
            state.committedFields = [];
            state.extraInfraModelParameters = initialInfraModelState.extraInfraModelParameters;
            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.validationErrors = validateParams(
                state.validationResponse?.geometryPlan || null,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );
        },
        setExistingInfraModel: (
            state: InfraModelState,
            { payload: plan }: PayloadAction<GeometryPlan | null>,
        ) => {
            state.extraInfraModelParameters = {
                pvDocumentOid: undefined, // TODO: get oid from the document (and document by id)
                planPhase: plan?.planPhase ?? undefined,
                decisionPhase: plan?.decisionPhase ?? undefined,
                measurementMethod: plan?.measurementMethod ?? undefined,
                message: plan?.message ?? undefined,
            };
            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.file = initialInfraModelState.file;
            state.selection = initialSelectionState;
            state.map.viewport = initialMapState.viewport;
            state.committedFields = [];
            state.validationErrors = initialInfraModelState.validationErrors;
        },
        setInfraModelActiveTab: (
            state: InfraModelState,
            { payload: tab }: PayloadAction<InfraModelTabType>,
        ): void => {
            state.infraModelActiveTab = tab;
        },
        ...wrapReducers((state: InfraModelState) => state.map, mapReducers),
        ...wrapReducers((state: InfraModelState) => state.infraModelList, infraModelListReducers),
        ...wrapReducers((state: InfraModelState) => state.selection, selectionReducers),
    },
});

function createError<TEntity>(field: keyof TEntity, reason: string, type: ValidationErrorType) {
    return {
        field,
        reason,
        type,
    };
}

function validateParams(
    plan: GeometryPlan | null,
    extraParams: ExtraInfraModelParameters,
    overrideParams: OverrideInfraModelParameters,
): ValidationError<InfraModelParameters>[] {
    const oidValidationErrors = extraParams.pvDocumentOid
        ? validateOid(extraParams.pvDocumentOid)
        : null;
    const errors: ValidationError<InfraModelParameters>[] = [];

    oidValidationErrors &&
        oidValidationErrors.forEach((error) =>
            errors.push(createError('pvDocumentOid', error, ValidationErrorType.ERROR)),
        );
    (extraParams.pvDocumentOid === undefined || oidValidationErrors?.length != 0) &&
        errors.push(createError('pvDocumentOid', 'critical', ValidationErrorType.WARNING));
    extraParams.planPhase === undefined &&
        errors.push(createError('planPhase', 'critical', ValidationErrorType.WARNING));
    extraParams.measurementMethod === undefined &&
        errors.push(createError('measurementMethod', 'critical', ValidationErrorType.WARNING));
    extraParams.decisionPhase === undefined &&
        errors.push(createError('decisionPhase', 'critical', ValidationErrorType.WARNING));
    overrideParams.createdDate === undefined &&
        plan?.planTime === undefined &&
        errors.push(createError('createdDate', 'critical', ValidationErrorType.WARNING));
    overrideParams.trackNumberId === undefined &&
        plan?.trackNumberId === undefined &&
        errors.push(createError('trackNumberId', 'critical', ValidationErrorType.WARNING));
    overrideParams.verticalCoordinateSystem === undefined &&
        plan?.units.verticalCoordinateSystem === undefined &&
        errors.push(
            createError('verticalCoordinateSystem', 'critical', ValidationErrorType.WARNING),
        );
    overrideParams.coordinateSystemSrid === undefined &&
        plan?.units.coordinateSystemSrid === undefined &&
        errors.push(createError('coordinateSystemSrid', 'critical', ValidationErrorType.WARNING));

    return errors;
}
export const infraModelReducer = infraModelSlice.reducer;
export const infraModelActionCreators = infraModelSlice.actions;
