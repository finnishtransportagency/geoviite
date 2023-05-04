import { API_URI, getWithDefault, queryParams } from 'api/api-fetch';
import { TimeStamp } from 'common/common-model';

export type VelhoFileStatus = 'REJECTED' | 'ACCEPTED' | 'PENDING';

export type VelhoEncoding = {
    code: string;
    name: string;
};

export type VelhoProject = {
    group: string;
    name: string;
};

export type VelhoDocument = {
    name: string;
    description: string;
    type: VelhoEncoding;
    modified: TimeStamp;
    status: VelhoFileStatus;
};

export type VelhoDocumentId = string;

export type VelhoDocumentHeader = {
    id: VelhoDocumentId;
    project: VelhoProject;
    assignment: string;
    materialGroup: string;
    document: VelhoDocument;
};

const VELHO_URI = `${API_URI}/velho`;

export async function getVelhoDocuments(status: VelhoFileStatus): Promise<VelhoDocumentHeader[]> {
    const params = queryParams({ status: status });
    return getWithDefault<VelhoDocumentHeader[]>(`${VELHO_URI}/document-headers${params}`, []);
}
