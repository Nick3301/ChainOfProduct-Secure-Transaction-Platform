package org.example.objects;

/**
 * SharedKey represents one entry in the "keys" array, binding a role/company
 * to an encrypted symmetric key for that transaction.
 *
 * {
 *   "role": "seller",
 *   "for": "seller-company-id",
 *   "enc_key": "<base64(Enc_PK_seller(K_tx))>"
 * }
 */
public class SharedKey {

  private final String role;
  private final String forCompany;
  private final String encKey;

  public SharedKey(String role, String forCompany, String encKey) {
    this.role = role;
    this.forCompany = forCompany;
    this.encKey = encKey;
  }

  public String getRole() {
    return role;
  }

  public String getForCompany() {
    return forCompany;
  }

  public String getEncKey() {
    return encKey;
  }
}
