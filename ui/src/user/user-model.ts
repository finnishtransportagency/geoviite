export type UserId = string;
export type RoleCode = string;

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
    code: RoleCode;
    name: string;
    description: string;
};
