import { Oid, TimeStamp } from 'common/common-model';

export type PVDocumentStatus = 'NOT_IM' | 'SUGGESTED' | 'REJECTED' | 'ACCEPTED';

export type PVProjectGroup = {
    oid: Oid;
    name: string;
    state: string;
};

export type PVProject = {
    oid: Oid;
    name: string;
    state: string;
};

export type PVAssignment = {
    oid: Oid;
    name: string;
    state: string;
};

export type PVDocument = {
    id: PVDocumentId;
    oid: Oid;
    name: string;
    description: string | null;
    type: string;
    state: string;
    category: string;
    group: string;
    modified: TimeStamp;
    status: PVDocumentStatus;
};

export type PVDocumentId = string;

export type PVDocumentHeader = {
    project: PVProject | null;
    projectGroup: PVProjectGroup | null;
    assignment: PVAssignment | null;
    document: PVDocument;
};

export type PVDocumentCount = {
    suggested: number;
    rejected: number;
};
