import {
    API_URI,
    ApiErrorResponse,
    getNonNull,
    postFormIgnoreError,
    postFormWithError,
    putFormIgnoreError,
    putIgnoreError,
    queryParams,
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
} from './projektivelho/pv-model';
import { asyncCache } from 'cache/cache';
import { Oid, TimeStamp } from 'common/common-model';
import i18n from 'i18next';

export interface InsertResponse {
    message: string;
    planId?: number;
}

export const EMPTY_VALIDATION_RESPONSE: ValidationResponse = {
    validationErrors: [],
    geometryPlan: undefined,
    planLayout: undefined,
};

const INFRAMODEL_URI = `${API_URI}/inframodel`;
const PROJEKTIVELHO_URI = `${INFRAMODEL_URI}/projektivelho`;

const pvDocumentHeaderCache = asyncCache<PVDocumentId, PVDocumentHeader | undefined>();
const pvDocumentHeadersByStateCache = asyncCache<PVDocumentStatus, PVDocumentHeader[]>();
const pvRedirectUrlCache = asyncCache<string, string | undefined>();

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
): Promise<InsertResponse | undefined> => {
    const formData = createFormData(file, extraParameters, overrideParameters);
    const response = await postFormIgnoreError<InsertResponse>(INFRAMODEL_URI, formData);
    if (response) {
        Snackbar.success(i18n.t('infra-model.upload.success'), {
            qaId: 'infra-model-import-upload__success-toast',
        });
        updatePlanChangeTime();
    }
    return response;
};

export async function updateGeometryPlan(
    planId: GeometryPlanId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlan | undefined> {
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
export async function hidePlan(planId: GeometryPlanId): Promise<GeometryPlanId | undefined> {
    return putIgnoreError<boolean, GeometryPlanId>(`${INFRAMODEL_URI}/${planId}/hidden`, true).then(
        (id) => {
            updatePlanChangeTime();
            return id;
        },
    );
}

export async function getPVDocuments(
    changeTime: TimeStamp,
    status: PVDocumentStatus,
): Promise<PVDocumentHeader[]> {
    const params = queryParams({ status: status });
    return pvDocumentHeadersByStateCache.get(changeTime, status, () =>
        getNonNull<PVDocumentHeader[]>(`${PROJEKTIVELHO_URI}/documents${params}`),
    );
}

export const getPVRedirectUrl = (changeTime: TimeStamp, oid: Oid) =>
    pvRedirectUrlCache.get(changeTime, oid, () =>
        getNonNull<string>(`${PROJEKTIVELHO_URI}/redirect/${oid}`),
    );

export async function getPVDocument(
    changeTime: TimeStamp = getChangeTimes().pvDocument,
    id: PVDocumentId,
): Promise<PVDocumentHeader | undefined> {
    return pvDocumentHeaderCache.get(changeTime, id, () =>
        getNonNull<PVDocumentHeader>(`${PROJEKTIVELHO_URI}/documents/${id}`),
    );
}

export async function getPVDocumentCount(): Promise<PVDocumentCount | undefined> {
    return getNonNull<PVDocumentCount>(`${PROJEKTIVELHO_URI}/documents/count`);
}

export async function rejectPVDocuments(ids: PVDocumentId[]): Promise<undefined> {
    return putIgnoreError<PVDocumentStatus, undefined>(
        `${PROJEKTIVELHO_URI}/documents/${ids}/status`,
        'REJECTED',
    ).then((ids) => {
        Snackbar.success(i18n.t('projektivelho.file-list.reject-success'));
        return ids;
    });
}

export async function restorePVDocument(id: PVDocumentId): Promise<undefined> {
    return putIgnoreError<PVDocumentStatus, undefined>(
        `${PROJEKTIVELHO_URI}/documents/${id}/status`,
        'SUGGESTED',
    ).then((id) => {
        Snackbar.success(i18n.t('projektivelho.file-list.restore-success'));
        return id;
    });
}

export const getValidationErrorsForPVDocument = async (
    pvDocumentId: PVDocumentId,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);
    return postFormWithError(
        `${PROJEKTIVELHO_URI}/documents/${pvDocumentId}/validate`,
        formData,
        defaultValidationErrorHandler,
    );
};

export async function importPVDocument(
    id: PVDocumentId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const url = `${PROJEKTIVELHO_URI}/documents/${id}`;
    const response = await postFormIgnoreError<GeometryPlanId>(url, formData);
    if (response) {
        Snackbar.success(i18n.t('infra-model.import.success'), {
            qaId: 'infra-model-import-upload__success-toast',
        });
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
