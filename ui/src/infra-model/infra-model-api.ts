import {
    API_URI,
    ApiErrorResponse,
    postFormIgnoreError,
    postFormWithError,
    putFormIgnoreError,
    queryParams,
    getWithDefault,
} from 'api/api-fetch';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { GeometryPlan, GeometryPlanId } from 'geometry/geometry-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { updatePlanChangeTime } from 'common/change-time-api';
import { ExtraInfraModelParameters, OverrideInfraModelParameters } from './infra-model-slice';
import { VelhoDocumentHeader, VelhoDocumentId, VelhoFileStatus } from './velho/velho-model';
import { asyncCache } from 'cache/cache';
import { TimeStamp } from 'common/common-model';

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

export interface InsertResponse {
    message: string;
    planId: number | null;
}

export const EMPTY_VALIDATION_RESPONSE: ValidationResponse = {
    validationErrors: [],
    geometryPlan: null,
    planLayout: null,
};

const INFRAMODEL_URI = `${API_URI}/inframodel`;
const VELHO_URI = `${INFRAMODEL_URI}/velho-import`;

const documentHeadersCache = asyncCache<string, VelhoDocumentHeader[]>();

export const inframodelDownloadUri = (planId: GeometryPlanId) => `${INFRAMODEL_URI}/${planId}/file`;

const defaultValidationErrorHandler = (response: ApiErrorResponse): ValidationResponse => ({
    ...EMPTY_VALIDATION_RESPONSE,
    validationErrors: [
        {
            localizationKey: response.localizedMessageKey || 'error.infra-model.request-failed',
            errorType: 'REQUEST_ERROR',
        },
    ],
});

export const getValidationErrorsForInfraModelFile = async (
    file?: File,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    if (file) {
        const formData = createFormData(file, undefined, overrideParameters);
        return postFormWithError<ValidationResponse, ValidationResponse>(
            `${INFRAMODEL_URI}/validate`,
            formData,
            defaultValidationErrorHandler,
        );
    } else {
        return Promise.resolve({
            ...EMPTY_VALIDATION_RESPONSE,
            message: 'No file',
        });
    }
};

export const getValidationErrorsForGeometryPlan = async (
    planId: GeometryPlanId,
    overrideParameters: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);
    return postFormWithError(
        `${INFRAMODEL_URI}/${planId}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export const saveInfraModelFile = async (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<InsertResponse | null> => {
    const formData = createFormData(file, extraParameters, overrideParameters);
    const response = await postFormIgnoreError<InsertResponse>(INFRAMODEL_URI, formData);
    if (response) {
        Snackbar.success(`IM-tiedosto tallennettu`);
        updatePlanChangeTime();
    }
    return response;
};

export async function updateGeometryPlan(
    planId: GeometryPlanId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlan | null> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const r = await putFormIgnoreError<GeometryPlan>(`${INFRAMODEL_URI}/${planId}`, formData);
    updatePlanChangeTime();
    return r;
}

export async function getVelhoDocuments(
    changeTime: TimeStamp,
    status: VelhoFileStatus,
): Promise<VelhoDocumentHeader[]> {
    const params = queryParams({ status: status });
    return documentHeadersCache.get(changeTime, status, () =>
        getWithDefault<VelhoDocumentHeader[]>(`${VELHO_URI}/documents${params}`, []),
    );
}

export async function rejectVelhoDocument(id: VelhoDocumentId): Promise<null> {
    console.log('Reject', id);
    return null;
}

export const getValidationErrorsForVelhoDocument = async (
    velhoDocumentId: VelhoDocumentId,
    overrideParameters: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);
    return postFormWithError(
        `${INFRAMODEL_URI}/velho-import/${velhoDocumentId}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export async function importVelhoDocument(
    id: VelhoDocumentId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | null> {
    const _formData = createFormData(undefined, extraParameters, overrideParameters);
    console.log('Import', id, 'extra', extraParameters, 'override', overrideParameters);
    return null;
}

const createFormData = (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
) => {
    const formData = new FormData();

    if (file) formData.set('file', file);
    if (overrideParameters) formData.set('override-parameters', createJsonBlob(overrideParameters));
    if (extraParameters) formData.set('extrainfo-parameters', createJsonBlob(extraParameters));

    return formData;
};

const createJsonBlob = (object: unknown) => {
    return new Blob([JSON.stringify(object)], { type: 'application/json' });
};
