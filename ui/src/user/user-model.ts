export type UserId = string;
export type RoleCode = 'operator' | 'browser';

export const PRIV_VIEW_BASIC = 'view-basic';
export const PRIV_VIEW_PV_DOCUMENTS = 'view-pv-documents';
export const PRIV_VIEW_GEOMETRY_FILE = 'view-geometry-file';
export const PRIV_VIEW_PUBLICATION = 'view-publication';
export const PRIV_DOWNLOAD_PUBLICATION = 'download-publication';
export const PRIV_VIEW_LAYOUT = 'view-layout';
export const PRIV_VIEW_LAYOUT_DRAFT = 'view-layout-draft';
export const PRIV_EDIT_LAYOUT = 'edit-layout';
export const PRIV_VIEW_GEOMETRY = 'view-geometry';
export const PRIV_EDIT_GEOMETRY_FILE = 'edit-geometry-file';
export const PRIV_DOWNLOAD_GEOMETRY = 'download-geometry';

export type PrivilegeCode =
    | typeof PRIV_VIEW_BASIC
    | typeof PRIV_VIEW_PV_DOCUMENTS
    | typeof PRIV_VIEW_GEOMETRY_FILE
    | typeof PRIV_VIEW_PUBLICATION
    | typeof PRIV_DOWNLOAD_PUBLICATION
    | typeof PRIV_VIEW_LAYOUT
    | typeof PRIV_VIEW_LAYOUT_DRAFT
    | typeof PRIV_EDIT_LAYOUT
    | typeof PRIV_VIEW_GEOMETRY
    | typeof PRIV_EDIT_GEOMETRY_FILE
    | typeof PRIV_DOWNLOAD_GEOMETRY;

export type User = {
    details: UserDetails;
    role: Role;
};

export type UserDetails = {
    id: UserId;
    firstName?: string;
    lastName?: string;
    organization?: string;
    userName: string;
};

export type Role = {
    code: RoleCode;
    name: string;
    privileges: Privilege[];
};

export type Privilege = {
    code: PrivilegeCode;
    name: string;
    description: string;
};

export const userHasPrivilege = (privileges: PrivilegeCode[], privilege: PrivilegeCode) =>
    privileges.some((priv) => priv === privilege);
