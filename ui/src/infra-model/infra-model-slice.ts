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
/*
import { RESET_STORE_ACTION } from 'store/store';

export const infraModelRootReducer: typeof infraModelReducer = (state, action) => {
    if (action.type == RESET_STORE_ACTION.type) {
        // Reset to initial state
        return infraModelReducer(undefined, action);
    }
    return infraModelReducer(state, action);
};
*/
export enum InfraModelViewType {
    UPLOAD,
    EDIT,
}

export type InfraModelState = {
    map: Map;
    infraModelList: InfraModelListState;
    selection: Selection;
    plan: GeometryPlan | null;
    planLayout: GeometryPlanLayout | null;
    file: SerializableFile | undefined;
    extraInframodelParameters: ExtraInfraModelParameters;
    overrideInfraModelParameters: OverrideInfraModelParameters;
    validationErrors: ValidationError<InfraModelParameters>[];
    committedFields: InfraModelParametersProp[];
};

export type ExtraInfraModelParameters = {
    oid: Oid | undefined;
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

export type OnPlanFetchReady = {
    plan: GeometryPlan | null;
    planLayout: GeometryPlanLayout | null;
};

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
    plan: null,
    planLayout: null,
    file: undefined,
    extraInframodelParameters: {
        oid: undefined,
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
};

export type GeometryPlanWithParameters = {
    geometryPlan: GeometryPlan;
    extraInfraModelParameters: ExtraInfraModelParameters;
};

const infraModelSlice = createSlice({
    name: 'infraModel',
    initialState: initialInfraModelState,
    reducers: {
        onPlanFetchReady: (
            state: InfraModelState,
            { payload: { plan, planLayout } }: PayloadAction<OnPlanFetchReady>, //MIKSI NÄÄ ON NULL
        ) => {
            state.plan = plan;
            state.planLayout = planLayout;

            console.log('INFRA-MODEL-SLICESTÄ STATE, PLAN JA PLANLAYOUT');
            console.log({ ...state });
            console.log(plan);
            console.log(planLayout);

            if (planLayout) {
                state.selection.planLayouts = [planLayout];
                const bBox = planLayout && planLayout.boundingBox;
                state.map.viewport = {
                    ...state.map.viewport,
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
            state.extraInframodelParameters[propEdit.key] = propEdit.value;
            state.validationErrors = validateParams(
                state.plan,
                state.extraInframodelParameters,
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
                state.plan,
                state.extraInframodelParameters,
                state.overrideInfraModelParameters,
            );
        },
        onCommitField: (
            state: InfraModelState,
            { payload: key }: PayloadAction<InfraModelParametersProp>,
        ) => {
            state.committedFields = [...state.committedFields, key];
        },
        onPlanUpdate: (state: InfraModelState) => {
            state.validationErrors = validateParams(
                state.plan,
                state.extraInframodelParameters,
                state.overrideInfraModelParameters,
            );
        },
        setInfraModelFile: (
            state: InfraModelState,
            { payload: file }: PayloadAction<SerializableFile>,
        ) => {
            state.file = file;

            state.plan = initialInfraModelState.plan;
            state.selection = initialSelectionState;
            state.map.viewport = initialMapState.viewport;
            state.committedFields = [];
            state.extraInframodelParameters = initialInfraModelState.extraInframodelParameters;
            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.validationErrors = validateParams(
                state.plan,
                state.extraInframodelParameters,
                state.overrideInfraModelParameters,
            );
        },
        setExistingInfraModel: (
            state: InfraModelState,
            { payload }: PayloadAction<GeometryPlanWithParameters>,
        ) => {
            state.plan = payload.geometryPlan;
            state.extraInframodelParameters = payload.extraInfraModelParameters;

            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.file = initialInfraModelState.file;
            state.selection = initialSelectionState;
            state.map.viewport = initialMapState.viewport;
            state.committedFields = [];
            state.validationErrors = initialInfraModelState.validationErrors;
        },
        ...wrapReducers((state: InfraModelState) => state.map, mapReducers),
        ...wrapReducers((state: InfraModelState) => state.infraModelList, infraModelListReducers),
        ...wrapReducers((state: InfraModelState) => state.selection, selectionReducers),
    },
    /*
    extraReducers: (builder) => {
        //builder.addCase();
    },
     */
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
    const oidValidationErrors = extraParams.oid ? validateOid(extraParams.oid) : null;
    const errors: ValidationError<InfraModelParameters>[] = [];

    oidValidationErrors &&
        oidValidationErrors.forEach((error) =>
            errors.push(createError('oid', error, ValidationErrorType.ERROR)),
        );
    (extraParams.oid === undefined || oidValidationErrors?.length != 0) &&
        errors.push(createError('oid', 'critical', ValidationErrorType.WARNING));
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
