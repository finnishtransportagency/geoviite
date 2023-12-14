export type UserId = string;
export type RoleCode = 'operator' | 'browser';
export type PrivilegeCode =
    | 'all-write'
    | 'ui-read'
    | 'inframodel-download'
    | 'dataproduct-download'
    | 'publications-download';

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
