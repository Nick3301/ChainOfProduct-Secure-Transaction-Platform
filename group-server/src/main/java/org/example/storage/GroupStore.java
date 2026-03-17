package org.example.storage;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GroupStore {
  private static final String FILE_PATH = "groups.json";
  private JsonObject data;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public GroupStore() {
    try {
      load();
    } catch (IOException e) {
      initializeEmpty();
    }
  }

  private void initializeEmpty() {
    data = new JsonObject();
    JsonArray groups = new JsonArray();
    data.add("groups", groups);
    try {
      save();
    } catch (IOException e) {
      System.err.println("Failed to initialize groups file: " + e.getMessage());
    }
  }

  public void load() throws IOException {
    lock.writeLock().lock();
    try {
      Path path = Path.of(FILE_PATH);
      if (!Files.exists(path)) {
        throw new IOException("File not found");
      }
      String json = Files.readString(path);
      data = JsonParser.parseString(json).getAsJsonObject();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void save() throws IOException {
    lock.readLock().lock();
    try {
      String json = gson.toJson(data);
      Files.writeString(Path.of(FILE_PATH), json);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void addGroup(String groupName, List<String> members) throws IOException {
    lock.writeLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");

      // Check if group already exists
      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        if (group.get("groupName").getAsString().equals(groupName)) {
          throw new IllegalArgumentException("Group with name '" + groupName + "'' already exists");
        }
      }

      JsonObject newGroup = new JsonObject();
      newGroup.addProperty("groupName", groupName);

      JsonArray membersArray = new JsonArray();
      members.forEach(membersArray::add);
      newGroup.add("members", membersArray);

      groups.add(newGroup);
      save();
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void addMember(String username, String groupName) throws IOException {
    lock.writeLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        if (group.get("groupName").getAsString().equals(groupName)) {
          JsonArray members = group.getAsJsonArray("members");
          if (members.contains(new JsonPrimitive(username))) {
            throw new IllegalArgumentException(
                "User '" + username + "' is already a member of group '" + groupName + "'");
          }
          members.add(username);
          save();
          return;
        }
      }
      throw new IllegalArgumentException("Group with name '" + groupName + "' does not exist");
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void removeMember(String username, String groupName) throws IOException {
    lock.writeLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        if (group.get("groupName").getAsString().equals(groupName)) {
          JsonArray members = group.getAsJsonArray("members");
          if (members.remove(new JsonPrimitive(username))) {
            save();
            return;
          }
          throw new IllegalArgumentException(
              "User '" + username + "' is not a member of group '" + groupName + "'");
        }
      }
      throw new IllegalArgumentException("Group with name '" + groupName + "' does not exist");
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean isMember(String username, String groupName) {
    lock.readLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        if (group.get("groupName").getAsString().equals(groupName)) {
          JsonArray members = group.getAsJsonArray("members");
          return members.contains(new JsonPrimitive(username));
        }
      }
      return false;
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<String> getGroupMembers(String groupName) {
    lock.readLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        if (group.get("groupName").getAsString().equals(groupName)) {
          JsonArray members = group.getAsJsonArray("members");
          List<String> memberList = new ArrayList<>();
          members.forEach(m -> memberList.add(m.getAsString()));
          return memberList;
        }
      }
      return Collections.emptyList();
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<String> getAllGroupNames() {
    lock.readLock().lock();
    try {
      JsonArray groups = data.getAsJsonArray("groups");
      List<String> groupNames = new ArrayList<>();

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();
        groupNames.add(group.get("groupName").getAsString());
      }

      return groupNames;
    } finally {
      lock.readLock().unlock();
    }
  }

  public String isInGroups(String username, List<String> groupNames) throws IOException {
    lock.readLock().lock();
    try {
      if (!data.has("groups")) {
        return null;
      }
      JsonArray groups = data.getAsJsonArray("groups");

      for (JsonElement elem : groups) {
        JsonObject group = elem.getAsJsonObject();

        String currentGroupName = group.get("groupName").getAsString();
        if (groupNames.contains(currentGroupName)) {
          JsonArray members = group.getAsJsonArray("members");
          for (JsonElement member : members) {
            if (member.getAsString().equals(username)) {
              System.out.println("[GroupStore] User: '" + username + "' found in group: '" + currentGroupName + "'");
              return currentGroupName;
            }
          }
        }
      }
      return null;
    } finally {
      lock.readLock().unlock();
    }
  }

}
