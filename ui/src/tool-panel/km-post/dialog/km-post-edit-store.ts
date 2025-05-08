import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    KmPostEditFields,
    KmPostGkFields,
    KmPostSaveRequest,
    KmPostSimpleFields,
} from 'linking/linking-model';
import {
    KmPostGkLocationSource,
    LayoutKmPost,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import {
    FieldValidationIssue,
    FieldValidationIssueType,
    isPropEditFieldCommitted,
    PropEdit,
} from 'utils/validation-utils';
import { isNilOrBlank, parseFloatOrUndefined } from 'utils/string-utils';
import { filterNotEmpty, first } from 'utils/array-utils';
import { BoundingBox, GeometryPoint, Point, rangeContainsInclusive } from 'model/geometry';
import { Srid } from 'common/common-model';
import proj4 from 'proj4';
import { expectDefined } from 'utils/type-utils';

export type KmPostEditState = {
    isNewKmPost: boolean;
    existingKmPost?: LayoutKmPost;
    trackNumberKmPost?: LayoutKmPost;
    geometryKmPostLocation?: GeometryPoint;
    kmPost: KmPostEditFields;
    isSaving: boolean;
    gkLocationEnabled: boolean;
    validationIssues: FieldValidationIssue<KmPostEditFields>[];
    committedFields: (keyof KmPostEditFields)[];
    allFieldsCommitted: boolean;
};

export const initialKmPostEditState: KmPostEditState = {
    isNewKmPost: false,
    existingKmPost: undefined,
    geometryKmPostLocation: undefined,
    gkLocationEnabled: false,
    kmPost: {
        kmNumber: '',
        gkLocationX: '',
        gkLocationY: '',
        gkSrid: undefined,
        gkLocationConfirmed: false,
    },
    isSaving: false,
    validationIssues: [],
    committedFields: [],
    allFieldsCommitted: false,
};

export const WGS_84_PROJECTION = '+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs';

const EASTING_MARGIN_BETWEEN_GKS_DEG = 0.01;

const GK_BOUNDS: BoundingBox[] = [
    {
        x: { min: 19513254.33, max: 19527832.44 },
        y: { min: 6663010.11, max: 6692058.68 },
    },
    {
        x: { min: 20472032.84, max: 20527967.16 },
        y: { min: 6645159.89, max: 6707657.06 },
    },
    {
        x: { min: 21471965.57, max: 21528034.43 },
        y: { min: 6636247.07, max: 7694317.48 },
    },
    {
        x: { min: 22471898.34, max: 22528101.66 },
        y: { min: 6627334.36, max: 7692086.48 },
    },
    {
        x: { min: 23471889.95, max: 23528110.05 },
        y: { min: 6626220.28, max: 7628505.09 },
    },
    {
        x: { min: 24471982.38, max: 24528017.62 },
        y: { min: 6638475.27, max: 7639659.41 },
    },
    {
        x: { min: 25472049.67, max: 25527950.33 },
        y: { min: 6647388.11, max: 7646352.07 },
    },
    {
        x: { min: 26472251.88, max: 26527748.12 },
        y: { min: 6674127.31, max: 7762365.51 },
    },
    {
        x: { min: 27472403.85, max: 27527596.15 },
        y: { min: 6694182.34, max: 7774636.97 },
    },
    {
        x: { min: 28472454.57, max: 28527545.43 },
        y: { min: 6700867.47, max: 7779099.35 },
    },
    {
        x: { min: 29472895.43, max: 29527104.57 },
        y: { min: 6758807.83, max: 7747863.07 },
    },
    {
        x: { min: 30473312.92, max: 30526687.08 },
        y: { min: 6813409.6, max: 7543736.6 },
    },
    {
        x: { min: 31473869.79, max: 31530833.57 },
        y: { min: 6885846.63, max: 7130086.16 },
    },
];

// GK-FIN coordinate systems currently only used for the live display of layout coordinates when editing km post
// positions manually
export const GK_FIN_COORDINATE_SYSTEMS: [Srid, string][] = [...Array(13)].map(
    (_, meridianIndex) => {
        const meridian = 19 + meridianIndex;
        const falseNorthing = meridian * 1e6 + 0.5e6;
        const srid = `EPSG:${3873 + meridianIndex}`;
        const projection = `+proj=tmerc +lat_0=0 +lon_0=${meridian} +k=1 +x_0=${falseNorthing} +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs`;
        return [srid, projection];
    },
);
export const isGk = (srid: Srid): boolean =>
    GK_FIN_COORDINATE_SYSTEMS.some(([gkSrid]) => gkSrid === srid);
export const isFromAnotherGk = (srid1: Srid, srid2: Srid): boolean =>
    isGk(srid1) && isGk(srid2) && srid1 !== srid2;

export const isWithinEastingMargin = (point: GeometryPoint): boolean => {
    const wgs84Point = pointToWgs84(point);
    const gkIndex = GK_FIN_COORDINATE_SYSTEMS.findIndex((gk) => first(gk) === point.srid);
    return (
        !!wgs84Point &&
        Math.abs(wgs84Point?.x - (gkIndex + 19)) < 0.5 + EASTING_MARGIN_BETWEEN_GKS_DEG
    );
};

const pointToWgs84 = (point: GeometryPoint | undefined): Point | undefined => {
    const currentPointProjection = point
        ? GK_FIN_COORDINATE_SYSTEMS.find(([srid]) => srid === point.srid)?.[1]
        : undefined;
    return point && currentPointProjection
        ? proj4(currentPointProjection, WGS_84_PROJECTION).forward({
              x: point.x,
              y: point.y,
          })
        : undefined;
};

export function parseGk(
    gkSrid: string | undefined,
    xStr: string,
    yStr: string,
): GeometryPoint | undefined {
    const foundGkSrid = GK_FIN_COORDINATE_SYSTEMS.find(([srid, _]) => srid === gkSrid)?.[0];
    const x = parseFloatOrUndefined(xStr);
    const y = parseFloatOrUndefined(yStr);
    if (foundGkSrid === undefined || x === undefined || y === undefined) {
        return undefined;
    } else {
        return { x, y, srid: foundGkSrid };
    }
}

function newLinkingKmPost(
    trackNumberId: LayoutTrackNumberId | undefined,
    geometryKmPostLocation: GeometryPoint | undefined,
): KmPostEditFields {
    return {
        kmNumber: '',
        state: undefined,
        trackNumberId: trackNumberId,
        gkLocationX: geometryKmPostLocation ? geometryKmPostLocation.x.toString(10) : '',
        gkLocationY: geometryKmPostLocation ? geometryKmPostLocation.y.toString(10) : '',
        gkSrid: geometryKmPostLocation ? geometryKmPostLocation.srid : undefined,
        gkLocationConfirmed: geometryKmPostLocation !== undefined,
    };
}

const isGkProp = (prop: keyof KmPostEditFields): boolean =>
    prop === 'gkLocationX' ||
    prop === 'gkLocationY' ||
    prop === 'gkSrid' ||
    prop === 'gkLocationConfirmed';

const MANDATORY_FIELDS = [
    'kmNumber',
    'state',
    'trackNumberId',
    'gkLocationX',
    'gkLocationY',
    'gkSrid',
] as const;

function validateLinkingKmPost(state: KmPostEditState): FieldValidationIssue<KmPostEditFields>[] {
    let errors: {
        field: keyof KmPostEditFields;
        reason: string;
        type: FieldValidationIssueType;
    }[] = [
        ...MANDATORY_FIELDS.map((prop) =>
            ((isGkProp(prop) && state.gkLocationEnabled) || !isGkProp(prop)) &&
            isNilOrBlank(state.kmPost[prop])
                ? {
                      field: prop,
                      reason: 'mandatory-field',
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
        ).filter(filterNotEmpty),
    ];

    if (state.kmPost.kmNumber.length > 0) {
        if (!isValidKmNumber(state.kmPost.kmNumber)) {
            errors = [...errors, ...getKmNumberDoesntMatchRegExpError()];
        }
    }

    if (state.trackNumberKmPost && state.existingKmPost?.id !== state.trackNumberKmPost?.id) {
        errors = [...errors, ...getErrorForKmPostExistsOnTrack()];
    }

    if (
        state.gkLocationEnabled &&
        (state.kmPost.gkLocationX !== '' || state.kmPost.gkLocationY !== '')
    ) {
        if (!isValidGkCoordinate(state.kmPost.gkLocationX)) {
            errors = [...errors, ...getGkCoordinateDoesNotParseError('gkLocationX')];
        } else if (!isFaithfullySaveableAsFloat(state.kmPost.gkLocationX)) {
            errors = [...errors, ...getGkCoordinateIsNotFaithfullySaveableError('gkLocationX')];
        }
        if (!isValidGkCoordinate(state.kmPost.gkLocationY)) {
            errors = [...errors, ...getGkCoordinateDoesNotParseError('gkLocationY')];
        } else if (!isFaithfullySaveableAsFloat(state.kmPost.gkLocationY)) {
            errors = [...errors, ...getGkCoordinateIsNotFaithfullySaveableError('gkLocationY')];
        }

        const gkIndex = GK_FIN_COORDINATE_SYSTEMS.findIndex(
            ([srid]) => srid === state.kmPost.gkSrid,
        );
        const gkBounds = GK_BOUNDS[gkIndex];
        const gkX = parseFloatOrUndefined(state.kmPost.gkLocationX);
        const gkY = parseFloatOrUndefined(state.kmPost.gkLocationY);

        const gkPoint = parseGk(
            state.kmPost.gkSrid,
            state.kmPost.gkLocationX,
            state.kmPost.gkLocationY,
        );

        if (gkX !== undefined && gkBounds) {
            const withinWgsEastingMargin = gkPoint ? isWithinEastingMargin(gkPoint) : false;

            if (!rangeContainsInclusive(gkBounds.x, gkX)) {
                errors = [
                    ...errors,
                    {
                        field: 'gkLocationX',
                        reason: 'wrong-easting',
                        type: FieldValidationIssueType.ERROR,
                    },
                    {
                        field: 'gkLocationY',
                        reason: 'wrong-easting',
                        type: FieldValidationIssueType.ERROR,
                    },
                ];
            } else if (rangeContainsInclusive(gkBounds.x, gkX) && !withinWgsEastingMargin) {
                errors = [
                    ...errors,
                    {
                        field: 'gkLocationX',
                        reason: 'wrong-easting-margin',
                        type: FieldValidationIssueType.WARNING,
                    },
                    {
                        field: 'gkLocationY',
                        reason: 'wrong-easting-margin',
                        type: FieldValidationIssueType.WARNING,
                    },
                ];
            }
        }

        if (gkY !== undefined && gkBounds && !rangeContainsInclusive(gkBounds.y, gkY)) {
            errors = [
                ...errors,
                {
                    field: 'gkLocationX',
                    reason: 'wrong-northing',
                    type: FieldValidationIssueType.ERROR,
                },
                {
                    field: 'gkLocationY',
                    reason: 'wrong-northing',
                    type: FieldValidationIssueType.ERROR,
                },
            ];
        }
    }

    return errors;
}

function getKmNumberDoesntMatchRegExpError(): FieldValidationIssue<KmPostEditFields>[] {
    return [
        {
            field: 'kmNumber',
            reason: 'km-post-regexp',
            type: FieldValidationIssueType.ERROR,
        },
    ];
}

function getErrorForKmPostExistsOnTrack(): FieldValidationIssue<KmPostEditFields>[] {
    return [
        ...['kmNumber'].map((prop: keyof KmPostEditFields) => {
            return {
                field: prop,
                reason: 'km-number-already-in-use',
                type: FieldValidationIssueType.ERROR,
            };
        }),
    ];
}

function getGkCoordinateDoesNotParseError(
    field: keyof KmPostEditFields,
): FieldValidationIssue<KmPostEditFields>[] {
    return [
        {
            field,
            reason: 'gk-coordinate-parse',
            type: FieldValidationIssueType.ERROR,
        },
    ];
}

function getGkCoordinateIsNotFaithfullySaveableError(
    field: keyof KmPostEditFields,
): FieldValidationIssue<KmPostEditFields>[] {
    return [
        {
            field,
            reason: 'gk-coordinate-saveable-as-float',
            type: FieldValidationIssueType.ERROR,
        },
    ];
}

const kmPostEditSlice = createSlice({
    name: 'kmPostEdit',
    initialState: initialKmPostEditState,
    reducers: {
        init: (): KmPostEditState => initialKmPostEditState,
        initWithNewKmPost: (
            state: KmPostEditState,
            {
                payload,
            }: PayloadAction<{
                trackNumberId: LayoutTrackNumberId | undefined;
                geometryKmPostLocation: GeometryPoint | undefined;
            }>,
        ): void => {
            state.isNewKmPost = true;
            state.geometryKmPostLocation = payload.geometryKmPostLocation;
            state.kmPost = newLinkingKmPost(payload.trackNumberId, payload.geometryKmPostLocation);
            state.validationIssues = validateLinkingKmPost(state);
            state.gkLocationEnabled = payload.geometryKmPostLocation !== undefined;
        },
        onKmPostLoaded: (
            state: KmPostEditState,
            { payload: existingKmPost }: PayloadAction<LayoutKmPost>,
        ): void => {
            state.existingKmPost = existingKmPost;
            state.kmPost = {
                ...existingKmPost,
                ...(existingKmPost.gkLocation === undefined
                    ? {
                          gkLocationX: '',
                          gkLocationY: '',
                          gkSrid: undefined,
                          gkLocationConfirmed: false,
                      }
                    : {
                          ...saveGkPointToEditingGkPoint(existingKmPost.gkLocation.location),
                          gkLocationConfirmed: existingKmPost.gkLocation.confirmed,
                      }),
            };
            state.gkLocationEnabled = existingKmPost.gkLocation !== undefined;
            state.validationIssues = validateLinkingKmPost(state);
        },
        onUpdateProp: function <TKey extends keyof KmPostEditFields>(
            state: KmPostEditState,
            { payload: propEdit }: PayloadAction<PropEdit<KmPostEditFields, TKey>>,
        ): void {
            if (state.kmPost) {
                state.kmPost[propEdit.key] = propEdit.value;
                state.validationIssues = validateLinkingKmPost(state);

                if (
                    isPropEditFieldCommitted(
                        propEdit,
                        state.committedFields,
                        state.validationIssues,
                    ) &&
                    !state.committedFields.includes(propEdit.key)
                ) {
                    state.committedFields = [...state.committedFields, propEdit.key];
                    if (propEdit.key === 'gkSrid' && state.kmPost.gkLocationX !== '') {
                        state.committedFields = [...state.committedFields, 'gkLocationX'];
                    }
                    if (propEdit.key === 'gkSrid' && state.kmPost.gkLocationY !== '') {
                        state.committedFields = [...state.committedFields, 'gkLocationY'];
                    }
                }
            }
        },
        onStartSaving: (state: KmPostEditState): void => {
            state.isSaving = true;
        },
        onEndSaving: (state: KmPostEditState): void => {
            state.isSaving = false;
        },
        onCommitField: function <TKey extends keyof KmPostEditFields>(
            state: KmPostEditState,
            { payload: key }: PayloadAction<TKey>,
        ): void {
            // Valid value entered for a field, mark that field as committed
            state.committedFields = [...state.committedFields, key];
            if (key === 'gkSrid' && state.kmPost.gkLocationX !== '') {
                state.committedFields = [...state.committedFields, 'gkLocationX'];
            }
            if (key === 'gkSrid' && state.kmPost.gkLocationY !== '') {
                state.committedFields = [...state.committedFields, 'gkLocationY'];
            }
        },
        validate: (state: KmPostEditState): void => {
            if (state.kmPost) {
                state.validationIssues = validateLinkingKmPost(state);
                state.allFieldsCommitted = true;
            }
        },
        onTrackNumberKmPostFound: (
            state: KmPostEditState,
            { payload: tnKmPost }: PayloadAction<LayoutKmPost | undefined>,
        ): void => {
            state.trackNumberKmPost = tnKmPost;
            state.validationIssues = validateLinkingKmPost(state);
        },
        setGkLocationEnabled: (
            state: KmPostEditState,
            { payload: gkLocationEnabled }: PayloadAction<boolean>,
        ): void => {
            state.gkLocationEnabled = gkLocationEnabled;
            state.validationIssues = validateLinkingKmPost(state);
        },
    },
});

export function canSaveKmPost(state: KmPostEditState): boolean {
    return (
        state.kmPost &&
        !state.validationIssues.filter((e) => e.type === 'ERROR').length &&
        !state.isSaving &&
        (state.gkLocationEnabled ? hasValidGkLocation(state) : true)
    );
}

const hasValidGkLocation = (state: KmPostEditState): boolean =>
    state.kmPost.gkLocationX !== '' &&
    state.kmPost.gkLocationY !== '' &&
    state.kmPost.gkSrid !== undefined &&
    state.kmPost.gkLocationConfirmed !== undefined &&
    isValidGkCoordinate(state.kmPost.gkLocationX) &&
    isValidGkCoordinate(state.kmPost.gkLocationY) &&
    isFaithfullySaveableAsFloat(state.kmPost.gkLocationX) &&
    isFaithfullySaveableAsFloat(state.kmPost.gkLocationY);

export function isValidKmNumber(kmNumber: string): boolean {
    const trimmedKmNumber = kmNumber.trim();
    if (trimmedKmNumber.length <= 4) {
        return /^\d{4}$/.test(trimmedKmNumber);
    } else if (trimmedKmNumber.length <= 6) {
        return /^\d{4}[A-Z]{1,2}$/.test(trimmedKmNumber);
    }
    return false;
}

export function isValidGkCoordinate(coordinate: string): boolean {
    return /^[0-9]*\.?[0-9]*$/.test(coordinate);
}

function withoutLeadingZeroes(number: string): string {
    return /^0*(.*)/.exec(number)?.[1] ?? '';
}

function withoutTrailingDecimalZeroes(number: string): string {
    return number.match(/\./)
        ? number.substring(0, number.length - (/([0.]*)$/.exec(number)?.[1]?.length ?? 0))
        : number;
}

function withoutInsignificantZeroes(number: string): string {
    return withoutLeadingZeroes(withoutTrailingDecimalZeroes(number));
}

export function isFaithfullySaveableAsFloat(number: string): boolean {
    return (
        /^[0.]*$/.test(number) ||
        parseFloat(number).toString() === withoutInsignificantZeroes(number)
    );
}

export function editingGkPointToSavePoint(state: KmPostEditState): GeometryPoint {
    return {
        x: parseFloat(expectDefined(state.kmPost.gkLocationX)),
        y: parseFloat(expectDefined(state.kmPost.gkLocationY)),
        srid: expectDefined(state.kmPost.gkSrid),
    };
}

export function saveGkPointToEditingGkPoint(point: GeometryPoint): {
    gkLocationX: string;
    gkLocationY: string;
    gkSrid: Srid;
} {
    return { gkLocationX: point.x.toString(), gkLocationY: point.y.toString(), gkSrid: point.srid };
}

export function kmPostGkPointDiffersFromOriginal(editing: KmPostGkFields, original: GeometryPoint) {
    const originalAsEditing = saveGkPointToEditingGkPoint(original);
    return (
        originalAsEditing.gkLocationX !== editing.gkLocationX ||
        originalAsEditing.gkLocationY !== editing.gkLocationY ||
        original.srid !== editing.gkSrid
    );
}

export function gkLocationIsEdited(state: KmPostEditState): boolean {
    return state.existingKmPost?.gkLocation === undefined
        ? state.kmPost.gkLocationX !== '' || state.kmPost.gkLocationY !== ''
        : kmPostGkPointDiffersFromOriginal(state.kmPost, state.existingKmPost.gkLocation?.location);
}

export function gkLocationSource(state: KmPostEditState): KmPostGkLocationSource | undefined {
    return gkLocationIsEdited(state) ? 'MANUAL' : state.existingKmPost?.gkLocation?.source;
}

export function kmPostSaveRequest(state: KmPostEditState): KmPostSaveRequest {
    const {
        gkLocationX: _delete,
        gkLocationY: _delete2,
        gkSrid: _delete3,
        ...simpleFields
    } = state.kmPost;
    const typedSimpleFieldsWithTrimmedNumber: KmPostSimpleFields = {
        ...simpleFields,
        kmNumber: state.kmPost.kmNumber.trim(),
    };

    return {
        ...typedSimpleFieldsWithTrimmedNumber,
        gkLocation: state.gkLocationEnabled
            ? {
                  location: editingGkPointToSavePoint(state),
                  source: expectDefined(gkLocationSource(state)),
                  confirmed: state.kmPost.gkLocationConfirmed,
              }
            : undefined,
        sourceId:
            gkLocationSource(state) === 'FROM_GEOMETRY'
                ? state.existingKmPost?.sourceId
                : undefined,
    };
}

export const reducer = kmPostEditSlice.reducer;
export const actions = kmPostEditSlice.actions;
