import {
    API_URI,
    ApiErrorResponse,
    getLocalizedIssue,
    getNonNull,
    postFormNonNull,
    postFormNonNullAdt,
    putFormNonNullAdt,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { GeometryPlanId } from 'geometry/geometry-model';
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

export const EMPTY_VALIDATION_RESPONSE: ValidationResponse = {
    geometryValidationIssues: [],
    geometryPlan: undefined,
    planLayout: undefined,
};

const INFRAMODEL_URI = `${API_URI}/inframodel`;
const PROJEKTIVELHO_URI = `${INFRAMODEL_URI}/projektivelho`;
const PROJEKTIVELHO_REDIRECT_URI = '/redirect/projektivelho';

const pvDocumentHeaderCache = asyncCache<PVDocumentId, PVDocumentHeader | undefined>();
const pvDocumentHeadersByStateCache = asyncCache<PVDocumentStatus, PVDocumentHeader[]>();

export const inframodelDownloadUri = (planId: GeometryPlanId) => `${INFRAMODEL_URI}/${planId}/file`;
export const projektivelhoDocumentDownloadUri = (docId: PVDocumentId) =>
    `${PROJEKTIVELHO_URI}/${docId}`;

const defaultValidationIssueHandler = (
    response: ApiErrorResponse | undefined,
): ValidationResponse => ({
    ...EMPTY_VALIDATION_RESPONSE,
    geometryValidationIssues: [
        {
            localizationKey: getLocalizedIssue(response, 'error.infra-model.request-failed'),
            issueType: 'REQUEST_ERROR',
        },
    ],
});

export const getGeometryValidationIssuesForInfraModelFile = async (
    file?: File,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    if (file) {
        const formData = createFormData(file, undefined, overrideParameters);
        return postFormNonNullAdt<ValidationResponse>(`${INFRAMODEL_URI}/validate`, formData).then(
            (r) => (r.isOk() ? r.value : defaultValidationIssueHandler(r.error)),
        );
    } else {
        return Promise.resolve({
            ...EMPTY_VALIDATION_RESPONSE,
            message: 'No file',
        });
    }
};

export const getValidationIssuesForGeometryPlan = async (
    planId: GeometryPlanId,
    overrideParameters: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);

    return postFormNonNullAdt<ValidationResponse>(
        `${INFRAMODEL_URI}/${planId}/validate`,
        formData,
    ).then((r) => (r.isOk() ? r.value : defaultValidationIssueHandler(r.error)));
};

export const saveInfraModelFile = async (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> => {
    const formData = createFormData(file, extraParameters, overrideParameters);
    return await postFormNonNull<GeometryPlanId>(INFRAMODEL_URI, formData);
};

export async function updateGeometryPlan(
    planId: GeometryPlanId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const response = await putFormNonNullAdt<GeometryPlanId>(
        `${INFRAMODEL_URI}/${planId}`,
        formData,
    );

    if (response.isOk()) {
        Snackbar.success('infra-model.edit.success');
        await updatePlanChangeTime();
    }

    return response.isOk() ? response.value : undefined;
}
export async function hidePlan(planId: GeometryPlanId): Promise<GeometryPlanId | undefined> {
    return putNonNull<boolean, GeometryPlanId>(`${INFRAMODEL_URI}/${planId}/hidden`, true).then(
        (id) => updatePlanChangeTime().then((_) => id),
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

export const getPVFilesRedirectUrl = (
    projectGroupOid?: Oid,
    projectOid?: Oid,
    assignmentOid?: Oid,
    documentOid?: Oid,
) => {
    const params = queryParams({
        projectGroup: projectGroupOid,
        project: projectOid,
        assignment: assignmentOid,
        document: documentOid,
    });

    return `${PROJEKTIVELHO_REDIRECT_URI}/files${params}`;
};

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
    return putNonNull<PVDocumentStatus, undefined>(
        `${PROJEKTIVELHO_URI}/documents/${ids}/status`,
        'REJECTED',
    ).then((ids) => {
        Snackbar.success('projektivelho.file-list.reject-success');
        return ids;
    });
}

export async function restorePVDocuments(id: PVDocumentId[]): Promise<undefined> {
    return putNonNull<PVDocumentStatus, undefined>(
        `${PROJEKTIVELHO_URI}/documents/${id}/status`,
        'SUGGESTED',
    ).then((id) => {
        Snackbar.success('projektivelho.file-list.restore-success');
        return id;
    });
}

export const getValidationIssuesForPVDocument = async (
    pvDocumentId: PVDocumentId,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = createFormData(undefined, undefined, overrideParameters);
    const result = await postFormNonNullAdt<ValidationResponse>(
        `${PROJEKTIVELHO_URI}/documents/${pvDocumentId}/validate`,
        formData,
    );

    return result.isOk() ? result.value : defaultValidationIssueHandler(result.error);
};

export async function importPVDocument(
    id: PVDocumentId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const url = `${PROJEKTIVELHO_URI}/documents/${id}`;
    const response = await postFormNonNullAdt<GeometryPlanId>(url, formData);

    if (response.isOk()) {
        Snackbar.success('infra-model.import.success');
        await updatePlanChangeTime();

        return response.value;
    } else {
        return undefined;
    }
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
