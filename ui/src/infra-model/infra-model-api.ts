import {
    API_URI,
    getNonNull,
    postFormNonNull,
    putFormNonNull,
    putNonNull,
    queryParams,
} from 'api/api-fetch';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
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
export const inframodelBatchDownloadUri = (planIds: GeometryPlanId[]) =>
    `${INFRAMODEL_URI}/batch${queryParams({ ids: planIds })}`;
export const projektivelhoDocumentDownloadUri = (docId: PVDocumentId) =>
    `${PROJEKTIVELHO_URI}/${docId}`;

export const getGeometryValidationIssuesForInfraModelFile = async (
    file?: File,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    if (file) {
        const formData = createFormData(file, undefined, overrideParameters);
        return postFormNonNull<ValidationResponse>(`${INFRAMODEL_URI}/validate`, formData);
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

    return await postFormNonNull<ValidationResponse>(
        `${INFRAMODEL_URI}/${planId}/validate`,
        formData,
    );
};

export const saveInfraModelFile = async (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> => {
    const formData = createFormData(file, extraParameters, overrideParameters);
    return await postFormNonNull<GeometryPlanId>(INFRAMODEL_URI, formData).then((r) => {
        Snackbar.success('infra-model.upload.success');
        updatePlanChangeTime();
        return r;
    });
};

export async function updateGeometryPlan(
    planId: GeometryPlanId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    return await putFormNonNull<GeometryPlanId>(`${INFRAMODEL_URI}/${planId}`, formData).then(
        (r) => {
            Snackbar.success('infra-model.edit.success');
            updatePlanChangeTime();
            return r;
        },
    );
}

export async function hidePlan(planId: GeometryPlanId): Promise<GeometryPlanId | undefined> {
    return putNonNull<boolean, GeometryPlanId>(`${INFRAMODEL_URI}/${planId}/hidden`, true).then(
        (id) => updatePlanChangeTime().then((_) => id),
    );
}

export const updatePlanApplicability = async (
    planId: GeometryPlanId,
    planApplicability: PlanApplicability | undefined,
): Promise<GeometryPlanId | undefined> =>
    putNonNull<PlanApplicability | undefined, GeometryPlanId>(
        `${INFRAMODEL_URI}/${planId}/applicability`,
        planApplicability,
    ).then((id) => updatePlanChangeTime().then((_) => id));

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
    return await postFormNonNull<ValidationResponse>(
        `${PROJEKTIVELHO_URI}/documents/${pvDocumentId}/validate`,
        formData,
    );
};

export async function importPVDocument(
    id: PVDocumentId,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<GeometryPlanId | undefined> {
    const formData = createFormData(undefined, extraParameters, overrideParameters);
    const url = `${PROJEKTIVELHO_URI}/documents/${id}`;
    return await postFormNonNull<GeometryPlanId>(url, formData).then((r) => {
        Snackbar.success('infra-model.import.success');
        updatePlanChangeTime();
        return r;
    });
}

const createFormData = (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
) => {
    const formData = new FormData();
    const trimmedExtraParameters = extraParameters && {
        ...extraParameters,
        name: extraParameters.name?.trim(),
    };
    if (file) formData.set('file', file);
    if (overrideParameters) formData.set('override-parameters', createJsonBlob(overrideParameters));
    if (trimmedExtraParameters)
        formData.set('extrainfo-parameters', createJsonBlob(trimmedExtraParameters));

    return formData;
};

const createJsonBlob = (object: unknown) => {
    return new Blob([JSON.stringify(object)], { type: 'application/json' });
};
