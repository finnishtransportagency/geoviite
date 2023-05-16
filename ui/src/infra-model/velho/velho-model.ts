import { Oid, TimeStamp } from 'common/common-model';

export type VelhoFileStatus = 'NOT_IM' | 'IMPORTED' | 'REJECTED' | 'ACCEPTED';

export type VelhoEncoding = {
    code: string;
    name: string;
};

export type VelhoProjectGroup = {
    oid: Oid;
    group: string;
    name: string;
};

export type VelhoProject = {
    oid: Oid;
    group: VelhoProjectGroup;
    name: string;
};

export type VelhoDocument = {
    id: VelhoDocumentId;
    oid: Oid;
    name: string;
    description: string | null;
    type: VelhoEncoding;
    modified: TimeStamp;
    status: VelhoFileStatus;
};

export type VelhoAssignment = {
    oid: Oid;
    name: string;
};

export type VelhoDocumentId = string;

export type VelhoDocumentHeader = {
    project: VelhoProject;
    assignment: VelhoAssignment;
    materialGroup: VelhoEncoding;
    document: VelhoDocument;
};
