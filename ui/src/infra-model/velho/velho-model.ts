import { Oid, TimeStamp } from 'common/common-model';

export type PVDocumentStatus = 'NOT_IM' | 'IMPORTED' | 'REJECTED' | 'ACCEPTED';

export type PVProjectGroup = {
    oid: Oid;
    group: string;
    name: string;
};

export type PVProject = {
    oid: Oid;
    group: PVProjectGroup;
    name: string;
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

export type PVAssignment = {
    oid: Oid;
    name: string;
};

export type PVDocumentId = string;

export type PVDocumentHeader = {
    project: PVProject | null;
    assignment: PVAssignment | null;
    document: PVDocument;
};
