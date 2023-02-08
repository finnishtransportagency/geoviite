import {
    API_URI,
    ApiErrorResponse,
    postFormIgnoreError,
    postFormWithError,
    putFormIgnoreError,
} from 'api/api-fetch';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { GeometryPlan, GeometryPlanId } from 'geometry/geometry-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { updatePlanChangeTime } from 'common/change-time-api';

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

export const INFRAMODEL_URI = `${API_URI}/inframodel`;

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
    formData: FormData,
): Promise<ValidationResponse> => {
    return postFormWithError<ValidationResponse, ValidationResponse>(
        `${INFRAMODEL_URI}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export const getValidationErrorsForGeometryPlan = async (
    planId: GeometryPlanId,
    formData: FormData,
): Promise<ValidationResponse> => {
    return postFormWithError(
        `${INFRAMODEL_URI}/${planId}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export const saveInfraModelFile = async (formData: FormData): Promise<InsertResponse | null> => {
    const response = await postFormIgnoreError<InsertResponse>(INFRAMODEL_URI, formData);
    if (response) {
        Snackbar.success(`IM-tiedosto tallennettu`);
        updatePlanChangeTime();
    }
    return response;
};

export async function updateGeometryPlan(
    planId: GeometryPlanId,
    formData: FormData,
): Promise<GeometryPlan | null> {
    const r = await putFormIgnoreError<GeometryPlan>(`${INFRAMODEL_URI}/${planId}`, formData);
    updatePlanChangeTime();
    return r;
}
