export type UserId = string;
export type RoleCode = string;

export type User = {
    details: UserDetails;
    role: Role;
};

export type UserDetails = {
    id: UserId;
    firstName: string | null;
    lastName: string | null;
    organization: string | null;
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
