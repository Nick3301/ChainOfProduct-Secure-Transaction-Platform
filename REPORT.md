# A40 ChainOfProduct Project Report

## Table of Contents

#### [1. Introduction](#1-introduction)
- [1.1 Business scenario](#11-business-scenario)
- [1.2 Security challenge](#12-security-challenge)
- [1.3 Attacker Model](#13-attacker-model)

#### [2. Project Development](#2-project-development-1)

- [2.1. Secure Document Format](#21-secure-document-format)
  - [2.1.1. Design](#211-design)
  - [2.1.2. Implementation](#212-implementation)
  - [2.1.3. Strategic Disclosure and Privacy Logic](#213-strategic-disclosure-and-privacy-logic)
- [2.2. Infrastructure](#22-infrastructure)
  - [2.2.1. Network and Machine Setup](#221-network-and-machine-setup)
  - [2.2.2. Transaction Server Operations](#222-transaction-server-operations)
  - [2.2.3. Server Communication Security](#223-server-communication-security)
- [2.3. Security Challenge](#23-security-challenge)
  - [2.3.1. Challenge Overview](#231-challenge-overview)
  - [2.3.2. Solution Design and Implementation](#232-solution-design-and-implementation)

#### [3. Conclusion](#3-conclusion)

## 1. Introduction

### 1.1 Business scenario

The project focuses on securing a "Chain of Product" (CoP) ecosystem - a network of companies that produce and deliver a
product, from raw materials to the final customer. In this specific scenario, the smartphone company Samchunga
operates within a network marked by inherent mutual distrust. For the supply chain to function, participating businesses
must rigorously protect their trade secrets, such as pricing models, supply volumes, and partnership details.

To bridge this gap between collaboration and secrecy, the network utilizes a public third-party service to record and validate Delivery-vs-Payment (DvP) transactions. These transactions are represented as JSON documents containing sensitive commercial data, including buyer and seller identities, product types, and monetary amounts. The fundamental business challenge lies in the dual need for privacy and transparency: the service must keep transaction details confidential from the public while simultaneously allowing businesses to selectively disclose information to specific parties. Therefore, the primary objective is to implement a security infrastructure that guarantees the confidentiality and integrity of these documents while managing complex access rights among distrusting entities.

### 1.2 Security challenge

The central challenge is to implement a system that ensures trust in an environment full of distrust. To achieve this goal, the system must, specifically, address four security requirements (SR):

- **Confidentiality between the buyer and the seller**: only the buyer, the seller and the parties to which the transaction was disclosed to can see the transaction.
- **Authentication on sharing**: only the seller and the buyer can share transaction details with other parties.
- **Integrity of the transaction**: the seller and the buyer can verify that the transaction information was not tampered with.
- **Integrity of the sharing list**: the seller can verify with whom the buyer shared the transaction with and vice-versa.

### 1.3 Attacker Model

**Trust Assumptions:**

1. **Fully Trusted Entities:**
    - **Group Server**: Trusted to correctly manage group memberships, enforce access control policies, and verify membership before releasing transaction keys. Assumed to be honest and not collude with malicious parties.

2. **Partially Trusted Entities:**
    - **Transaction Server & Database**: Trusted to store and forward encrypted transactions without tampering, but cannot access transaction plaintext since all data is encrypted. May attempt to observe access patterns or
      metadata.
    - **Transaction Creators (Seller/Buyer)**: Fully trusted to create but partially trusted to disclose their own transactions. Have legitimate authority to decide which groups or individuals can access transaction data.
    - **Group Members**: Users who belong to groups. Expected to follow protocols when accessing shared transactions, but may attempt to access transactions outside their authorized scope or share transaction keys with unauthorized parties.

3. **Untrusted Entities:**
    - **External Parties**: Any entity not registered in the system cannot access transaction data or group information.
    - **Network Adversaries**: Cannot decrypt intercepted traffic due to RSA encryption, but may observe communication
      patterns between clients and servers.

**Attacker Capabilities:**

The attacker is assumed to have the following capabilities:

- **Network Access**: Intercept, replay, or modify messages between clients and servers
- **Client Compromise**: Extract stored keys or credentials from compromised client machines
- **Server Communication**: Send arbitrary requests to exploit authentication or authorization flaws
- **Timing Analysis**: Observe group creation, membership changes, and transaction access patterns
- **Transaction Server Access**: View stored transactions and encrypted payloads without decryption ability

**Attacker Limitations:**

The attacker **cannot**:

- Break RSA-2048 encryption or forge RSA signatures
- Compromise the Group Server directly (protected infrastructure)
- Decrypt the encrypted payload containing groupName and transactionKey without the Group Server's private key
- Modify the Group Server's group membership storage (GroupStore) without detection
- Forge valid JWT authentication tokens
- Impersonate users during membership validation checks

**Attack Scenarios Considered:**

1. **Unauthorized Group Access**: Attacker attempts to request transaction keys for groups they don't belong to - mitigated by Group Server membership validation
2. **Encrypted Payload Tampering**: Attacker modifies the encrypted payload sent to Group Server - detected when decryption fails or produces invalid JSON
3. **Timing Attacks on Authentication**: Attacker attempts to infer valid usernames or passwords by measuring response times during authentication - mitigated by using constant-time comparison operations for password verification, ensuring authentication failures take the same amount of time regardless of where the mismatch occurs
4. **Transaction Server Metadata Leakage**: Attacker analyzes transaction metadata to infer group names - mitigated by encrypting groupName inside the payload
5. **Replay Attacks**: Attacker reuses old encrypted payloads - allowed by design since membership is checked dynamically at access time

## 2. Project Development

### 2.1. Secure Document Format

#### 2.1.1. Design

**Custom Cryptographic Library Design**

Our cryptographic library implements a secure document protection system specifically designed for the Chain of Product (CoP) business scenario. The design has transitioned from a pure asymmetric model to a Hybrid Cryptographic System to support efficiency, selective disclosure, and multi-party agreement.

**Design Rationale:**

The library addresses the four core security requirements through a multi-layered approach:

1. **Hybrid Encryption for Confidentiality [SR1]**: Each DvP transaction is encrypted using a fresh, randomly generated 256-bit AES symmetric key, referred to as $K_{tx}$. This key is never stored in plaintext. Instead, it is wrapped (encrypted) using the RSA-2048 public keys of authorized recipients. This allows a single encrypted payload to be accessed by multiple parties (Buyer, Seller, Third Parties) by providing each with their own RSA-encrypted version of $K_{tx}$.

2. **Agreement through Digital Signatures [SR2]**: To establish mutual consent, the system supports a dual-signature model. The "Secure Document" (containing the metadata and the encrypted content) is signed independently by both the seller and the buyer using RSA-SHA256. The Transaction Server prevents third-party access until both required signatures are present in the metadata.

3. **Two-Layer Integrity Protection [SR3]**:
    - **Inner Layer**: A SHA-256 integrity hash of the raw transaction JSON is included inside the encrypted AES payload. This ensures that once decrypted, the recipient can verify the content has not been tampered with.
    - **Outer Layer**: Digital signatures are computed over the "Secure Document" envelope (metadata + cipher), preventing tampering with the routing information or the ciphertext itself.

4. **Selective Disclosure and Audit Logs [SR4]**: The document metadata contains SharedKey entries and ShareLogs. Each share event is signed by the discloser (Buyer or Seller), creating a verifiable audit trail of who shared the transaction, with whom, and at what time.

**Security Architecture:**

The system follows an Envelope-based model where the transaction data is sealed, and access keys are attached as metadata:

- **Client Side**: Generates $K_{tx}$, encrypts content, signs envelope, wraps $K_{tx}$ for recipients
- **Server Side**: Validates signatures, enforces permissions and stores the encrypted envelope
- **Key Management**: 2048-bit RSA keys in PKCS#8 (private) and X.509 (public) formats

**Complete Example of Protected DvP Transaction:**

Original DvP Transaction:

```json
{
  "id": 123,
  "timestamp": 17663363400,
  "seller": "Ching Chong Extractions",
  "buyer": "Lays Chips",
  "product": "Indium",
  "units": 40000,
  "amount": 90000000
}
```

**Communication Example:**

**Seller → Server** (Transaction Submission):

```json
{
  "transactionId": "123",
  "seller": "Samchunga",
  "buyer": "Lays Chips",
  "createdBy": "Samchunga",
  "encryptedContent": "{\"content_cipher\":\"kF8rX9...\",\"receiver\":\"Lays Chips\",\"sender\":\"Samchunga\",\"seq_num\":\"1\",\"timestamp\":\"17663363400\"}",
  "metadata": {
    "sharedKeys": [
      {
        "keyType": "seller",
        "forCompany": "Samchunga",
        "encKey": "mE2tY8..."
      },
      {
        "keyType": "buyer",
        "forCompany": "Lays Chips",
        "encKey": "pO3sF7..."
      }
    ],
    "shareList": [],
    "groupShareList": [],
    "sellerSignature": "zD8gH0jM2pQ5uI9wR...",
    "buyerSignature": null
  }
}
```

**Field Descriptions:**

- `createdBy`: Entity (buyer or seller) that initiated storage request
- `encryptedContent`: JSON-stringified Secure Document with AES-encrypted payload and routing metadata
- `sharedKeys`: List of $K_{tx}$ copies, each RSA-encrypted for specific recipients
- `sellerSignature` / `buyerSignature`: RSA signatures over sorted `encryptedContent` JSON
- `content_cipher`: AES-256 encrypted block containing the transaction and its SHA-256 hash

**Server → Buyer** (Filtered Response):

```json
{
  "transactionId": "123",
  "seller": "Samchunga",
  "buyer": "Lays Chips",
  "createdBy": "Samchunga",
  "encryptedContent": "{\"content_cipher\":\"kF8rX9...\",\"receiver\":\"Lays Chips\",\"sender\":\"Samchunga\",\"seq_num\":\"1\",\"timestamp\":\"17663363400\"}",
  "metadata": {
    "sharedKeys": [
      {
        "keyType": "buyer",
        "forCompany": "Lays Chips",
        "encKey": "pO3sF7..."
      }
    ],
    "shareList": [],
    "groupShareList": [],
    "sellerSignature": null,
    "buyerSignature": "aB5cH8jK2pL0u..."
  }
}
```

**Justification for the Filtered View**:

- **Key Isolation**: The sharedKeys list is filtered to only include the $K_{tx}$ wrapped for "Lays Chips". This prevents the Buyer from seeing or harvesting the encrypted keys intended for the Seller or other third parties.
- **Signature Isolation**: Although both parties may have signed the transaction, the sellerSignature is set to null in the response to the Buyer. The reasoning for this choice can be found in section [2.1.3.](#213-strategic-disclosure-and-privacy-logic)

**Server → Third Party** (Filtered Response):

```json
{
  "transactionId": "123",
  "seller": "Samchunga",
  "buyer": "Lays Chips",
  "createdBy": "Samchunga",
  "encryptedContent": "{\"content_cipher\":\"kF8rX9...\",\"receiver\":\"Lays Chips\",\"sender\":\"Samchunga\",\"seq_num\":\"1\",\"timestamp\":\"17663363400\"}",
  "metadata": {
    "sharedKeys": [
      {
        "keyType": "third_party",
        "forCompany": "Auditor_X",
        "encKey": "rT9vP2..."
      }
    ],
    "shareList": null,
    "groupShareList": null,
    "sellerSignature": null,
    "buyerSignature": null
  }
}
```

**Justification for the Third-Party Filtered View:**:

- **Zero-Knowledge of the Network**: Both shareList and groupShareList are cleared (set to null or empty). This ensures the third party cannot see who else has access to the transaction, preserving the privacy of the broader audit chain.
- **Signature Blackout**: Both sellerSignature and buyerSignature are stripped. Since third parties only need to read the content, the server does not allow them to hold the cryptographic proof of the primary agreement, preventing them from re-sharing the "signed package" outside the system.
- **Targeted Key Delivery**: The sharedKeys list contains only the single key wrapped for that specific auditor (or group). The third party never sees the encrypted keys belonging to the Buyer or Seller.

#### 2.1.2. Implementation

The security library and transaction clients were implemented using Java 21 and the Java Cryptography Architecture (JCA). The choice of native JCA ensures that the system relies on audited, standard-compliant cryptographic providers, which is essential for a high-stakes commercial environment.

**Implementation process**:

- **Standardized Key Wrapping (RSA-OAEP/PKCS1)**: The usage of RSA/ECB/PKCS1Padding enables secure $K_{tx}$ transport to multiple recipients without re-encrypting transaction data
- **Deterministic Serialization (Alphabetical Sorting)**: `JsonUtils.sortDocument` ensures alphabetical JSON field ordering before signing/verifying, preventing signature mismatch errors across platforms
- **Encapsulated Integrity**: SHA-256 hash inside encrypted payload provides immediate content verification after decryption, providing a secondary layer of defense against subtle database corruption or sophisticated bit-flipping attacks.

#### 2.1.3. Strategic Disclosure and Privacy Logic

The `filterMetadata` logic in `ServerResource` implements Least Privilege:

1. **Metadata Filtering for Privacy**:
    - **Buyer/Seller View**: When the primary parties fetch a transaction, they receive the full `sharedKeys` list and complete audit trail of `shareLogs`, allowing them to verify exactly who has been granted access (SR4)
    - **Third-Party/Group View**: When a third party or group member fetches a transaction, the server filters the metadata to include only the `SharedKey` specifically intended for that recipient
    - **Why**: This prevents third parties from harvesting the public keys or identifying other business partners involved in the transaction, ensuring an auditor can verify their specific access without gaining unnecessary insight into the broader supply chain network

2. **Non-Repudiation of Disclosure**:
  - Every share requires signed `ShareLog` covering `TransactionID`, `Timestamp`, `SharedBy`, `SharedWith`
  - **Why**: This ensures that neither the buyer nor the seller can deny sharing the transaction. If a data leak occurs, the signed audit trail in the metadata provides cryptographic proof of who authorized the disclosure, preventing blame-shifting between distrusting partners.

3. **Signature-Based Agreement Enforcement**:
  - Third-party access blocked until both buyer and seller signatures present
  - **Why**: This prevents "half-baked" or disputed transactions from being audited or acted upon by group members until both primary parties have cryptographically agreed to the terms.

4. **Signature Isolation**:
  - Only recipient's own signature returned to prevent obtaining complete signed packages
  - **Why**: This prevents any single party from obtaining a "complete" signed transaction package that could be verified independently outside the infrastructure, reducing the risk of unauthorized redistribution or misuse.

### 2.2. Infrastructure

#### 2.2.1. Network and Machine Setup

The system is deployed on a segmented network to ensure isolation between the clients, the application logic, and the data storage. The following image illustrates the network infrastructure.

![network_infrastructure](img/diagrams/network_infrastructure.png)

The following table details the configuration of each machine, specifically the network adapters and IP addresses assigned to the distinct subnets.

| Machine Name                             | eth0 | eth1                       | eth2         | eth3         |
|:-----------------------------------------|:-----|:---------------------------|:-------------|:-------------|
| **Client**                               | NAT  | 192.168.0.x (default x=10) | X            | X            |
| **Client-Servers Firewall**              | NAT  | 192.168.0.30               | 192.168.3.30 | 192.168.4.30 |
| **Transaction Server**                   | NAT  | 192.168.3.20               | 192.168.1.20 | X            |
| **Group Server**                         | NAT  | 192.168.4.20               | X            | X            |
| **Transaction Server-Database Firewall** | NAT  | 192.168.1.30               | 192.168.2.30 | X            |
| **Database**                             | NAT  | 192.168.2.40               | X            | X            |

Based on these subnets, the valid communication channels are restricted to the following flows:

- Clients &harr; Clients-Servers Firewall
- Clients-Servers Firewall &harr; Transaction Server
- Clients-Servers Firewall &harr; Group Server
- Transaction Server &harr; Transaction Server-Database Firewall
- Transaction Server-Database Firewall &harr; Database

**Technologies:**

Distributed Java architecture with PostgreSQL 16 database in Docker:

- **Communication Framework (JAX-RS / Jersey)**: RESTful endpoints with `ContainerRequestFilters` for JWT validation
- **Data Serialization (GSON)**: JSON payload processing
- **Security & Cryptography**:
  - **JJWT (Java JWT)**: Secure JWT token generation and signing
  - **Java Cryptography Architecture (JCA)**: PBKDF2 password hashing and RSA/AES cryptographic operations
  *(more details on the protection of documents [here](#21-secure-document-format))*.
  - **JDBC PreparedStatements**: SQL injection prevention through query parameterization

#### 2.2.2. Transaction Server Operations

The Transaction Server exposes two sets of RESTful API endpoints to handle authentication and transaction operations:

**Authentication Endpoints (`/auth`):**

| Method | Endpoint          | Description                                      | Sequence flow diagram          |
|--------|-------------------|--------------------------------------------------|--------------------------------|
| `POST` | `/auth/register`  | Register new user with username and password     | [Image](img/diagrams/register.png) |
| `POST` | `/auth/login`     | Authenticate user and receive JWT token          | [Image](img/diagrams/login.png)    |

**Transaction Operations Endpoints (`/chain-of-product`):**

| Method | Endpoint                                      | Description                                                       | Sequence flow diagram                                                                                    |
|--------|-----------------------------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `POST` | `/chain-of-product`                           | Store new encrypted transaction in database                       | [Image](img/diagrams/store_transaction.png)                                                              |
| `GET`  | `/chain-of-product/{transactionId}`           | Fetch transaction with filtered metadata based on user role       | [Image](img/diagrams/fetch_transaction.png) and [Image](img/diagrams/fetch_transaction_group.png)(user in group) |
| `POST` | `/chain-of-product/sign/{transactionId}`      | Add seller or buyer signature to existing transaction             | [Image](img/diagrams/sign_transaction.png)                                                                   |
| `POST` | `/chain-of-product/share/{transactionId}`     | Share transaction with individual third party                     | [Image](img/diagrams/share_transaction.png)                                                                  |
| `POST` | `/chain-of-product/group-share/{transactionId}` | Share transaction with entire group (key encrypted for Group Server) | [Image](img/diagrams/share_transaction_group.png)                                                            |

All transaction endpoints (except `/auth`) require a valid JWT token in the `Authorization` header, obtained through the login endpoint. The server validates user permissions before executing each operation:

- **Store/Sign**: Only seller or buyer can perform these operations
- **Share**: Only seller or buyer can share their own transactions
- **Fetch**: Access granted to seller, buyer, or authorized third parties/group members

#### 2.2.3. Server Communication Security

In the context of this project, we assume that all necessary keys, certificates, and trust stores are pre-configured and accessible by the relevant machines.

The communication between all parties is supported by SSL/TLS. It encrypts the communication channels, preventing Man-In-The-Middle attacks. The configuration of the channels works as the following:

- Clients trust both Transaction Server and Group Server.
- Transaction Server trusts Group Server.
- Transaction Server and Database trust each other on a mutual TLS communication.

To establish even more security on the communication channels, each machine of the system (not the Clients) has firewall rules set:

- **Firewall between the Clients and the Servers**:
    - Allows Client-Server communication when incoming requests hit the firewall's `192.168.0.0/24` subnet port.
      Forwards the incoming Client's requests that are directed to the right ports - `8081` for the Group Server and `8443` for the Transaction Server - to the respective Server. This way, the Servers' IP addresses are hidden to the Clients. All the request coming from different subnets are dropped.
    - Acts as a gateway for the communication between the Transaction Server and the Group Server, as the Servers are hosted on different subnets.
- **Transaction Server**:
    - Allows incoming requests from the Clients subnet `192.168.0.0/24` that are redirected by the firewall to the `8443` port.
- **Group Server**:
    - Allows incoming requests from the Clients subnet `192.168.0.0/24` that are redirected by the firewall to the `8081` port.
    - Allows incoming requests from the Transaction Server subnet `192.168.3.0/24` that reach the `8081` port.
- **Firewall between the Transaction Server and the Database**:
    - Allows communication incoming from the Transaction Server IP address `192.168.1.20` outgoing to the Database IP address `192.168.2.40` to the `5432` port.
- **Database**:
    - Allows incoming requests only from the Transaction Server IP address `192.168.1.20` to the `5432` port.

With all these rules set, requests coming from outside networks are not allowed in all the machines, ensuring security in the communication channels.

### 2.3. Security Challenge

#### 2.3.1. Challenge Overview

The security challenge introduces **group-based transaction disclosure** to address the limitations of individual partner disclosure. In the original design, each transaction disclosure required explicitly specifying individual partners, which becomes laborious when dealing with multiple collaborating entities.

**New Requirements:**

1. **Group Management**: The system must support creating groups of partners consisting of already registered users
2. **Group-Based Disclosure**: Transactions can be disclosed to an entire group rather than individual users
3. **Dynamic Membership Tracking**: A separate server must dynamically track groups and members to enforce access rules valid at the moment of access
4. **Transparent Access**: All partners of a group can read and validate transaction information once disclosed to that group

**Impact on Original Design:**

The original point-to-point encryption model (seller → buyer → individual third parties) remains intact, with the addition of a **group-based secret sharing mechanism**:

- New **Group Server** validates membership and controls transaction key access, allowing transactions to be disclosed to groups of user 
- The Group Server acts as a trusted gatekeeper that controls access to transaction keys based on current group membership
- The Transaction Server continues to handle individual disclosures and stores group-based shares, while delegating membership authorization to the Group Server
- Group names encrypted with Group Server's public key, hidden from Transaction Server metadata

#### 2.3.2. Solution Design and Implementation

**Architecture Overview:**

The solution introduces a dedicated **Group Server** running independently from the main Transaction Server. This architectural separation ensures that group management and membership validation remain independent from transaction storage, following the principle of separation of concerns and enabling dynamic access control based on current group membership.

**Key Design Principle: Gatekeeper Architecture**

- Centralized gatekeeper model with Group Server maintaining single RSA-2048 key pair.
- Transaction keys encrypted with the targer group name in a JSON payload using Group Server's public key.

**Advantages:**
1. **Dynamic Access Control**: Immediate membership changes without re-encryption
2. **Simplified Key Management**: No per-group key distribution or rotation
3. **Metadata Protection**: Group names hidden from Transaction Server, preventing metadata leakage and unauthorized modification of group associations
4. **Centralized Authorization**: Single enforcement point for group access

**Components:**

1. **Group Server (group-server/)**
    - **GroupStore**: Thread-safe file-based storage (`groups.json`) that maps group names to member lists
    - **RSA Key Pair**: Single 2048-bit RSA key pair for the Group Server (`group_server_public.der`,
      `group_server_private.der`)
    - **REST API**: Exposes endpoints for group management, membership queries, and transaction key decryption

2. **GroupStore Implementation**
    - Loads group data at startup into memory for fast membership lookups
    - Persists changes immediately to disk using Gson JSON serialization
    - Thread-safe operations using `ReentrantReadWriteLock` for concurrent access
    - Supports group creation, member addition/removal, and membership validation

**REST API Endpoints:**

| Method   | Endpoint                                              | Description                                     | Sequence flow diagram                     |
|----------|-------------------------------------------------------|-------------------------------------------------|-------------------------------------------|
| `POST`   | `/group-server/groups/{groupName}`                    | Create a new group with initial member list     | [Image](img/diagrams/create_group.png)        |
| `GET`    | `/group-server/groups/{groupName}/members/{username}` | Check if username is member of group            | [Image](img/diagrams/is_member_group.png)     |
| `POST`   | `/group-server/groups/{groupName}/members/{username}` | Add member to existing group                    | [Image](img/diagrams/add_member_group.png)    |
| `DELETE` | `/group-server/groups/{groupName}/members/{username}` | Remove member from group                        | [Image](img/diagrams/remove_member_group.png) |
| `GET`    | `/group-server/public-key`                            | Retrieve Group Server's public key (Base64)     |                                           |
| `POST`   | `/group-server/decrypt?username={user}`               | Decrypt transaction key if user is group member |                                           |

**Security Properties Achieved:**

| Security Requirement                  | Implementation                                                           |
|---------------------------------------|--------------------------------------------------------------------------|
| **SR1: Group Confidentiality**        | Only verified group members receive transaction key from Group Server    |
| **SR2: Authentication on Disclosure** | Only seller/buyer can share transactions (verified via JWT tokens)       |
| **SR3: Transaction Integrity**        | SHA-256 hashes included in encrypted transaction, verified on decryption |
| **SR4: Disclosure Audit Trail**       | Transaction metadata records SharedKey entries for audit                 |
| **Dynamic Access Control**            | Membership checked at access time, changes take effect immediately       |
| **Metadata Protection**               | Group names encrypted, hidden from Transaction Server                    |

**Thread Safety and Concurrency:**

The Group Server uses Jersey's multi-threaded HTTP server with proper synchronization:

- **Concurrent Reads**: Multiple membership checks can execute simultaneously
- **Serialized Writes**: Group creation and membership modifications are serialized
- **Persistent Storage**: All changes immediately persisted to disk to prevent data loss

## 3. Conclusion

Our project successfully implemented a comprehensive security infrastructure for protecting commercial transactions in a supply chain ecosystem characterized by mutual distrust.

All security requirements were fully satisfied:
- Confidentiality between buyer and seller is ensured through AES encryption with unique per-transaction keys, where only authorized parties can decrypt transaction content.
- Authentication on sharing is enforced through RSA digital signatures and JWT tokens, preventing unauthorized disclosure.
- Transaction integrity is maintained via SHA-256 hashes embedded within encrypted payloads, with automatic verification detecting any tampering. 
- Integrity of sharing lists is preserved through immutable audit trails recording all disclosure events with cryptographic signatures. 

The security challenge extended this foundation with group-based transaction disclosure while maintaining all original security guarantees.

Future enhancements: PKI for key distribution, key revocation mechanisms, improving scalability through database-backed Group Server storage and a web-based interface to improve usability.

This project demonstrates that security emerges from careful design across all system layers, combining cryptographic theory with practical software engineering. The hands-on experience of implementing end-to-end encryption, managing keys, securing network communications, and designing authorization protocols provides valuable insights into building trustworthy systems in zero-trust environments.

----
