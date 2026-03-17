package org.example.objects;

/**
 * ShareRequest bundles a ShareLog (who shared with who) and
 * a SharedKey (encrypted symmetric key for the 3rd party).
 */
public class ShareRequest {

  private final ShareLog shareLog;
  private final SharedKey sharedKey;

  public ShareRequest(ShareLog shareLog, SharedKey sharedKey) {
    this.shareLog = shareLog;
    this.sharedKey = sharedKey;
  }

  public ShareLog getShareLog() {
    return shareLog;
  }

  public SharedKey getSharedKey() {
    return sharedKey;
  }
}
