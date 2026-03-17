package org.example.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * EncryptedTransaction is the class that defines the format of the JSON transaction objects that
 * are received from and sent to clients
 */
public class EncryptedTransaction {
  private final String transactionId;
  private final String buyer;
  private final String seller;
  private final String createdBy;
  private final String encryptedContent;
  private TransactionMetadata metadata;

  public EncryptedTransaction(String transactionId, String seller, String buyer, String createdBy, String encryptedContent,
                              TransactionMetadata metadata) {
    this.transactionId = transactionId;
    this.seller = seller;
    this.buyer = buyer;
    this.createdBy = createdBy;
    this.encryptedContent = encryptedContent;
    this.metadata = metadata;
  }

  public EncryptedTransaction(String transactionId, String seller, String buyer, String createdBy, String encryptedContent) {
    this.transactionId = transactionId;
    this.seller = seller;
    this.buyer = buyer;
    this.createdBy = createdBy;
    this.encryptedContent = encryptedContent;
    this.metadata = new TransactionMetadata(new TransactionMetadata.TransactionSignatures());
  }

  public EncryptedTransaction() {
    this.transactionId = null;
    this.seller = null;
    this.buyer = null;
    this.createdBy = null;
    this.encryptedContent = null;
    this.metadata = new TransactionMetadata(new TransactionMetadata.TransactionSignatures());
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getBuyer() {
    return buyer;
  }

  public String getSeller() {
    return seller;
  }

   public String getCreatedBy() {
    return createdBy;
  }

  public String getEncryptedContent() {
    return encryptedContent;
  }

  public TransactionMetadata getMetadata() {
    return metadata;
  }

  public String getBuyerSignature() {
    return this.metadata.getSignatures().getBuyerSignature();
  }

  public String getSellerSignature() {
    return this.metadata.getSignatures().getSellerSignature();
  }

  public List<ShareLog> getShareList() {
    return this.metadata.getSharedWith();
  }

  public void addShareLog(ShareLog shareLog) {
    this.metadata.addShareLog(shareLog);
  }

  public void addBuyerSignature(String buyerSignature) {
    this.metadata.getSignatures().addBuyerSignature(buyerSignature);
  }

  public void addSellerSignature(String sellerSignature) {
    this.metadata.getSignatures().addSellerSignature(sellerSignature);
  }

  public boolean signedByBuyer() {
    return this.metadata.getSignatures().signedByBuyer();
  }

  public boolean signedBySeller() {
    return this.metadata.getSignatures().signedBySeller();
  }

  public List<SharedKey> getSharedKeys() {
    return this.metadata.getSharedKeys();
  }

  public void addSharedKey(SharedKey sharedKey) {
    this.metadata.addSharedKey(sharedKey);
  }

  public List<ShareLog> getGroupShareList() {
    return this.metadata.getGroupShares();
  }

  public void addGroupShareLog(ShareLog groupShareLog) {
    this.metadata.addGroupShareLog(groupShareLog);
  }

  public static class TransactionMetadata {
    private TransactionSignatures signatures;
    private List<ShareLog> sharedWith = new ArrayList<>();
    private List<SharedKey> sharedKeys = new ArrayList<>();
    private List<ShareLog> groupShares = new ArrayList<>();

    public TransactionMetadata() {
    }

    public TransactionMetadata(TransactionSignatures signatures) {
      this.signatures = signatures;
      this.sharedWith = new ArrayList<>();
      this.sharedKeys = new ArrayList<>();
      this.groupShares = new ArrayList<>();
    }

    private TransactionSignatures getSignatures() {
      return this.signatures;
    }

    private List<ShareLog> getSharedWith() {
      return this.sharedWith;
    }

    private void addShareLog(ShareLog shareLog) {
      this.sharedWith.add(shareLog);
    }

    private List<ShareLog> getGroupShares() {
      return this.groupShares;
    }

    private void addGroupShareLog(ShareLog shareLog) {
      this.groupShares.add(shareLog);
    }

    private List<SharedKey> getSharedKeys() {
      return this.sharedKeys;
    }

    private void addSharedKey(SharedKey sharedKey) {
      this.sharedKeys.add(sharedKey);
    }

    public static class TransactionSignatures {
      private String buyerSignature;
      private String sellerSignature;

      public TransactionSignatures() {
        this.buyerSignature = null;
        this.sellerSignature = null;
      }

      private String getBuyerSignature() {
        return this.buyerSignature;
      }

      private String getSellerSignature() {
        return this.sellerSignature;
      }

      private void addBuyerSignature(String buyerSignature) {
        this.buyerSignature = buyerSignature;
      }

      private void addSellerSignature(String sellerSignature) {
        this.sellerSignature = sellerSignature;
      }

      private boolean signedByBuyer() {
        return this.buyerSignature != null;
      }

      private boolean signedBySeller() {
        return this.sellerSignature != null;
      }
    }
  }
}
