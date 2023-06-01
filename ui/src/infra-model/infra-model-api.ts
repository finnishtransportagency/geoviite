import {
    API_URI,
    ApiErrorResponse,
    postFormIgnoreError,
    postFormWithError,
    putFormIgnoreError,
    queryParams,
    getWithDefault,
    putIgnoreError,
    getIgnoreError,
} from 'api/api-fetch';
import { GeometryPlan, GeometryPlanId } from 'geometry/geometry-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { getChangeTimes, updatePlanChangeTime } from 'common/change-time-api';
import {
    ExtraInfraModelParameters,
    OverrideInfraModelParameters,
    ValidationResponse,
} from './infra-model-slice';
import {
    PVDocumentCount,
    PVDocumentHeader,
    PVDocumentId,
    PVDocumentStatus,
} from './velho/velho-model';
import { asyncCache } from 'cache/cache';
import { Oid, TimeStamp } from 'common/common-model';
import i18n from 'i18next';

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
const PROJEKTIVELHO_URI = `${INFRAMODEL_URI}/velho-import`;

const pvDocumentHeaderCache = asyncCache<PVDocumentId, PVDocumentHeader | null>();
const pvDocumentHeadersByStateCache = asyncCache<PVDocumentStatus, PVDocumentHeader[]>();

export const inframodelDownloadUri = (planId: GeometryPlanId) => `${INFRAMODEL_URI}/${planId}/file`;
export const projektivelhoDocumentDownloadUri = (docId: PVDocumentId) =>
    `${PROJEKTIVELHO_URI}/${docId}`;

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
        Snackbar.success(i18n.t('infra-model.upload.success'));
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
    const response = await putFormIgnoreError<GeometryPlan>(
        `${INFRAMODEL_URI}/${planId}`,
        formData,
    );
    if (response) {
        Snackbar.success(i18n.t('infra-model.edit.success'));
        updatePlanChangeTime();
    }
    return response;
}

export async function getVelhoDocuments(
    changeTime: TimeStamp,
    status: PVDocumentStatus,
): Promise<PVDocumentHeader[]> {
    const params = queryParams({ status: status });
    return pvDocumentHeadersByStateCache.get(changeTime, status, () =>
        getWithDefault<PVDocumentHeader[]>(`${PROJEKTIVELHO_URI}/documents${params}`, []),
    );
}

export async function getVelhoDocument(
    changeTime: TimeStamp = getChangeTimes().velhoDocument,
    id: PVDocumentId,
): Promise<PVDocumentHeader | null> {
    return pvDocumentHeaderCache.get(changeTime, id, () =>
        getIgnoreError<PVDocumentHeader>(`${PROJEKTIVELHO_URI}/documents/${id}`),
    );
}

export const getVelhoRedirectUrl = (oid: Oid) =>
    getIgnoreError<string>(`${PROJEKTIVELHO_URI}/redirect/${oid}`);

export async function getVelhoDocumentCount(): Promise<PVDocumentCount | null> {
    return getIgnoreError<PVDocumentCount>(`${PROJEKTIVELHO_URI}/documents/count`);
}

export async function rejectVelhoDocument(id: PVDocumentId): Promise<null> {
    return putIgnoreError<PVDocumentStatus, null>(
        `${PROJEKTIVELHO_URI}/documents/${id}/status`,
        'REJECTED',
    ).then((id) => {
        Snackbar.success(i18n.t('velho.file-list.reject-success'));
        return id;
    });
}

export async function restoreVelhoDocument(id: PVDocumentId): Promise<null> {
    return putIgnoreError<PVDocumentStatus, null>(
        `${PROJEKTIVELHO_URI}/documents/${id}/status`,
        'SUGGESTED',
    ).then((id) => {
        Snackbar.success(i18n.t('velho.file-list.restore-success'));
        return id;
    });
}

export const getValidationErrorsForVelhoDocument = async (
    velhoDocumentId: PVDocumentId,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);
    return postFormWithError(
        `${INFRAMODEL_URI}/velho-import/${velhoDocumentId}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export async function importVelhoDocument(
    id: PVDocumentId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | null> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const url = `${INFRAMODEL_URI}/velho-import/${id}`;
    const response = await postFormIgnoreError<GeometryPlanId>(url, formData);
    if (response) {
        Snackbar.success(i18n.t('infra-model.import.success'));
        updatePlanChangeTime();
    }
    return response;
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
