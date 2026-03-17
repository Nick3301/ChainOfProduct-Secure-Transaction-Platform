package org.example.secureDocuments;

import javax.crypto.SecretKey;
import java.util.Map;

/**
 * Result of Protect: secureDocument (no embedded signature) + symmetric key K_tx +
 * sender's agreement signature over the canonical secureDocument.
 */
public class ProtectResult {
  private final Map<String, Object> secureDocument;
  private final String senderSignature;
  private final SecretKey transactionKey;

  public ProtectResult(Map<String, Object> secureDocument,
                       String senderSignature,
                       SecretKey transactionKey) {
    this.secureDocument = secureDocument;
    this.senderSignature = senderSignature;
    this.transactionKey = transactionKey;
  }

  public Map<String, Object> getSecureDocument() {
    return secureDocument;
  }

  /** Signature created by the sender over the canonical secureDocument JSON. */
  public String getSenderSignature() {
    return senderSignature;
  }

  public SecretKey getTransactionKey() {
    return transactionKey;
  }
}
