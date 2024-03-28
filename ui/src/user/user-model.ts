export type UserId = string;
export type RoleCode = 'operator' | 'browser' | 'consultant' | 'authority' | 'team';

export const VIEW_BASIC = 'view-basic';
export const VIEW_PV_DOCUMENTS = 'view-pv-documents';
export const VIEW_GEOMETRY_FILE = 'view-geometry-file';
export const VIEW_PUBLICATION = 'view-publication';
export const DOWNLOAD_PUBLICATION = 'download-publication';
export const VIEW_LAYOUT = 'view-layout';
export const VIEW_LAYOUT_DRAFT = 'view-layout-draft';
export const EDIT_LAYOUT = 'edit-layout';
export const VIEW_GEOMETRY = 'view-geometry';
export const EDIT_GEOMETRY_FILE = 'edit-geometry-file';
export const DOWNLOAD_GEOMETRY = 'download-geometry';

export type PrivilegeCode =
    | typeof VIEW_BASIC
    | typeof VIEW_PV_DOCUMENTS
    | typeof VIEW_GEOMETRY_FILE
    | typeof VIEW_PUBLICATION
    | typeof DOWNLOAD_PUBLICATION
    | typeof VIEW_LAYOUT
    | typeof VIEW_LAYOUT_DRAFT
    | typeof EDIT_LAYOUT
    | typeof VIEW_GEOMETRY
    | typeof EDIT_GEOMETRY_FILE
    | typeof DOWNLOAD_GEOMETRY;

export type User = {
    details: UserDetails;
    role: Role;
    availableRoles: Role[];
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
