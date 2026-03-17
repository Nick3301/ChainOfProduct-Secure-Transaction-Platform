package org.example.objects;

/**
 * ShareLog is the class that defines the format of the JSON objects that define a sharing
 * permission, with the party who shared and the party who the transaction was shared with
 */
public class ShareLog {
  private final String sharedBy;
  private final String sharedWith;
  private final String signature;

  public ShareLog(String sharedBy, String sharedWith, String signature) {
    this.sharedBy = sharedBy;
    this.sharedWith = sharedWith;
    this.signature = signature;
  }

  public String getSharedBy() {
    return sharedBy;
  }
  public String getSharedWith() {
    return sharedWith;
  }
  public String getSignature() {
    return signature;
  }
}
