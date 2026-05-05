export interface CreateUserGroupRequestModel {
  userGroupName: string;
  password: string;
}

export interface EditUserGroupRequest {
  userGroupName: string;
  newPassword: string;
}

export interface JoinUserGroupRequestModel {
  userGroupName: string;
  password: string;
}

