package org.example.objects;

/**
 * GroupMembershipStatus represents whether a given username
 * is a member of the specified group.
 */
public class GroupMembershipStatus {
  private final String groupName;
  private final String username;
  private final boolean memberStatus;

  public GroupMembershipStatus(String groupName, String username, boolean memberStatus) {
    this.groupName = groupName;
    this.username = username;
    this.memberStatus = memberStatus;
  }

  public String getGroupName() {
    return groupName;
  }
  public String getUsername() {
    return username;
  }
  public boolean isMember() {
    return memberStatus;
  }
}
