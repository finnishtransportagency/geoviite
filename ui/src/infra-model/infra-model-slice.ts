import { ActionReducerMapBuilder, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { Map, MapLayerMenuItem, MapLayerMenuGroups, MapLayerName } from 'map/map-model';
import {
    initialMapState,
    layerMenuItemMapLayers,
    mapReducers,
    collectRelatedLayers,
} from 'map/map-store';
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
    GeometryPlan,
    PlanApplicability,
    PlanDecisionPhase,
    PlanPhase,
    PlanSource,
    ProjectId,
} from 'geometry/geometry-model';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { SerializableFile } from 'utils/file-utils';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import {
    ElevationMeasurementMethod,
    MeasurementMethod,
    Message,
    Srid,
    TrackNumber,
    VerticalCoordinateSystem,
} from 'common/common-model';
import { Prop } from 'utils/type-utils';
import { LocalizationParams } from 'i18n/config';
import { PURGE } from 'redux-persist';
import { isNilOrBlank } from 'utils/string-utils';

export enum InfraModelViewType {
    UPLOAD,
    IMPORT,
    EDIT,
}

export enum InfraModelTabType {
    PLAN = 'plan',
    WAITING = 'waiting',
    REJECTED = 'rejected',
}

export type InfraModelState = {
    map: Map;
    infraModelList: InfraModelListState;
    selection: Selection;
    validationResponse?: ValidationResponse;
    file?: SerializableFile;
    extraInfraModelParameters: ExtraInfraModelParameters;
    overrideInfraModelParameters: OverrideInfraModelParameters;
    validationIssues: FieldValidationIssue<InfraModelParameters>[];
    committedFields: InfraModelParametersProp[];
    infraModelActiveTab: InfraModelTabType;
};

export type ExtraInfraModelParameters = {
    planPhase?: PlanPhase;
    decisionPhase?: PlanDecisionPhase;
    measurementMethod?: MeasurementMethod;
    elevationMeasurementMethod?: ElevationMeasurementMethod;
    message?: Message;
    name?: string;
    planApplicability?: PlanApplicability;
};

export type XmlCharset = 'US_ASCII' | 'UTF_16LE' | 'UTF_16' | 'UTF_16BE' | 'UTF_8' | 'ISO_8859_1';
export type OverrideInfraModelParameters = {
    coordinateSystemSrid?: Srid;
    projectId?: ProjectId;
    authorId?: AuthorId;
    verticalCoordinateSystem?: VerticalCoordinateSystem;
    trackNumber?: TrackNumber;
    createdDate?: Date;
    encoding?: XmlCharset;
    source?: PlanSource;
};

export type InfraModelParameters = ExtraInfraModelParameters & OverrideInfraModelParameters;

export type InfraModelParametersProp = keyof InfraModelParameters;

export type LocalizationKey = string;

export type GeometryValidationIssueType =
    | 'REQUEST_ERROR'
    | 'PARSING_ERROR'
    | 'TRANSFORMATION_ERROR'
    | 'VALIDATION_ERROR'
    | 'OBSERVATION_MAJOR'
    | 'OBSERVATION_MINOR';

export type CustomGeometryValidationIssue = {
    localizationKey: LocalizationKey;
    issueType: GeometryValidationIssueType;
} & LocalizationParams;

export type ValidationResponse = {
    geometryValidationIssues: CustomGeometryValidationIssue[];
    geometryPlan?: GeometryPlan;
    planLayout?: GeometryPlanLayout;
};

const visibleMapLayers: MapLayerName[] = [
    'background-map-layer',
    'location-track-alignment-layer',
    'reference-line-alignment-layer',
    'reference-line-badge-layer',
    'switch-layer',
    'km-post-layer',
    'geometry-alignment-layer',
    'geometry-switch-layer',
    'geometry-km-post-layer',
];

function createInfraModelLayerMenu(visibleLayers: MapLayerName[]): MapLayerMenuGroups {
    const allVisibleLayers = new Set([...visibleLayers, ...collectRelatedLayers(visibleLayers)]);

    const updateMenuItemsVisibility = (items: MapLayerMenuItem[]): MapLayerMenuItem[] => {
        return items.map((item) => {
            const layers = layerMenuItemMapLayers[item.name];
            const allLayersVisible =
                layers.length === 0
                    ? item.selected
                    : layers.every((layer) => allVisibleLayers.has(layer));

            return {
                ...item,
                selected: allLayersVisible,
                subMenu: item.subMenu ? updateMenuItemsVisibility(item.subMenu) : undefined,
            };
        });
    };

    return {
        layout: updateMenuItemsVisibility(initialMapState.layerMenu.layout),
        geometry: updateMenuItemsVisibility(initialMapState.layerMenu.geometry),
        debug: updateMenuItemsVisibility(initialMapState.layerMenu.debug),
    };
}

export const initialInfraModelState: InfraModelState = {
    map: {
        ...initialMapState,
        visibleLayers: visibleMapLayers,
        layerMenu: createInfraModelLayerMenu(visibleMapLayers),
    },
    infraModelList: initialInfraModelListState,
    selection: {
        ...initialSelectionState,
        selectionModes: ['segment', 'switch'],
    },
    validationResponse: undefined,
    file: undefined,
    extraInfraModelParameters: {
        planPhase: undefined,
        decisionPhase: undefined,
        measurementMethod: undefined,
        elevationMeasurementMethod: undefined,
        message: undefined,
    },
    overrideInfraModelParameters: {
        coordinateSystemSrid: undefined,
        verticalCoordinateSystem: undefined,
        createdDate: undefined,
        source: undefined,
    },
    validationIssues: [],
    committedFields: [],
    infraModelActiveTab: InfraModelTabType.PLAN,
};

const infraModelSlice = createSlice({
    name: 'infraModel',
    initialState: initialInfraModelState,
    extraReducers: (builder: ActionReducerMapBuilder<InfraModelState>) => {
        builder.addCase(PURGE, (_state, _action) => {
            return initialInfraModelState;
        });
    },
    reducers: {
        onPlanValidated: (
            state: InfraModelState,
            { payload: response }: PayloadAction<ValidationResponse>,
        ) => {
            state.extraInfraModelParameters = {
                ...state.extraInfraModelParameters,
                name: state.extraInfraModelParameters.name || response.geometryPlan?.name,
            };
            state.committedFields = ['name'];
            state.validationResponse = response;
            state.validationIssues = validateParams(
                response.geometryPlan,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );

            if (response.planLayout) {
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
            state.validationIssues = validateParams(
                state.validationResponse?.geometryPlan,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );
            if (
                !state.committedFields.includes(propEdit.key) &&
                !state.validationIssues.some((error) => error.field === propEdit.key)
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
            state.validationIssues = validateParams(
                state.validationResponse?.geometryPlan,
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
            state.validationIssues = validateParams(
                state.validationResponse?.geometryPlan,
                state.extraInfraModelParameters,
                state.overrideInfraModelParameters,
            );
        },
        setExistingInfraModel: (
            state: InfraModelState,
            { payload: plan }: PayloadAction<GeometryPlan | undefined>,
        ) => {
            state.extraInfraModelParameters = {
                planPhase: plan?.planPhase ?? undefined,
                decisionPhase: plan?.decisionPhase ?? undefined,
                measurementMethod: plan?.measurementMethod ?? undefined,
                elevationMeasurementMethod: plan?.elevationMeasurementMethod ?? undefined,
                message: plan?.message ?? undefined,
                name: plan?.name ?? undefined,
                planApplicability: plan?.planApplicability ?? undefined,
            };
            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.file = initialInfraModelState.file;
            state.selection = initialSelectionState;
            state.map.viewport = initialMapState.viewport;
            state.committedFields = [];
            state.validationIssues = initialInfraModelState.validationIssues;
        },
        clearInfraModelState: (state: InfraModelState) => {
            state.validationResponse = initialInfraModelState.validationResponse;
            state.extraInfraModelParameters = initialInfraModelState.extraInfraModelParameters;
            state.overrideInfraModelParameters =
                initialInfraModelState.overrideInfraModelParameters;
            state.file = initialInfraModelState.file;
            state.selection = initialSelectionState;
            state.map.viewport = initialInfraModelState.map.viewport;
            state.committedFields = [];
            state.validationIssues = initialInfraModelState.validationIssues;
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

function createValidationIssue<TEntity>(
    field: keyof TEntity,
    reason: string,
    type: FieldValidationIssueType,
) {
    return {
        field,
        reason,
        type,
    };
}

function validateParams(
    plan: GeometryPlan | undefined,
    extraParams: ExtraInfraModelParameters,
    overrideParams: OverrideInfraModelParameters,
): FieldValidationIssue<InfraModelParameters>[] {
    const issues: FieldValidationIssue<InfraModelParameters>[] = [];

    extraParams.planPhase === undefined &&
        issues.push(
            createValidationIssue('planPhase', 'critical', FieldValidationIssueType.WARNING),
        );
    extraParams.measurementMethod === undefined &&
        issues.push(
            createValidationIssue(
                'measurementMethod',
                'critical',
                FieldValidationIssueType.WARNING,
            ),
        );
    extraParams.elevationMeasurementMethod === undefined &&
        issues.push(
            createValidationIssue(
                'elevationMeasurementMethod',
                'critical',
                FieldValidationIssueType.WARNING,
            ),
        );
    extraParams.decisionPhase === undefined &&
        issues.push(
            createValidationIssue('decisionPhase', 'critical', FieldValidationIssueType.WARNING),
        );

    isNilOrBlank(extraParams.name) &&
        issues.push(
            createValidationIssue(
                'name',
                'name-is-mandatory-field',
                FieldValidationIssueType.ERROR,
            ),
        );

    overrideParams.createdDate === undefined &&
        plan?.planTime === undefined &&
        issues.push(
            createValidationIssue('createdDate', 'critical', FieldValidationIssueType.WARNING),
        );
    overrideParams.trackNumber === undefined &&
        plan?.trackNumber === undefined &&
        issues.push(
            createValidationIssue('trackNumber', 'critical', FieldValidationIssueType.WARNING),
        );
    overrideParams.verticalCoordinateSystem === undefined &&
        plan?.units.verticalCoordinateSystem === undefined &&
        issues.push(
            createValidationIssue(
                'verticalCoordinateSystem',
                'critical',
                FieldValidationIssueType.WARNING,
            ),
        );
    overrideParams.coordinateSystemSrid === undefined &&
        plan?.units.coordinateSystemSrid === undefined &&
        issues.push(
            createValidationIssue(
                'coordinateSystemSrid',
                'critical',
                FieldValidationIssueType.WARNING,
            ),
        );

    return issues;
}

export const infraModelReducer = infraModelSlice.reducer;
export const infraModelActionCreators = infraModelSlice.actions;
