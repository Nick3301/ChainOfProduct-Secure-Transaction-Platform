package org.example.secureDocuments;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.example.utils.JsonUtils;

/**
 * Command-line interface for secure documents library.
 *
 * Directory-based I/O:
 *
 * Usage:
 *   securedocs help
 *   securedocs protect <input-file> <sender-private-key> <output-dir>
 *   securedocs check <input-dir> <sender-public-key>
 *   securedocs unprotect <input-dir> <sender-public-key> <output-file>
 *
 * protect output files:
 *   - secure_document.json
 *   - signature.txt
 *   - transaction_key.b64
 */
public class SecureDocumentsCLI {

    private static final String TOOL_NAME = "securedocs";

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                printHelp();
                System.exit(1);
            }

            String command = args[0].toLowerCase();

            switch (command) {
                case "help" -> printHelp();
                case "protect" -> {
                    if (args.length < 3) {
                        System.err.println("[ERROR] Usage: protect <input-file> <sender-private-key>");
                        System.exit(1);
                    }
                    handleProtect(args[1], args[2]);
                }
                case "check" -> {
                    if (args.length < 4) {
                        System.err.println("[ERROR] Usage: check <secure-doc> <signature> <sender-public-key>");
                        System.exit(1);
                    }
                    handleCheck(args[1], args[2], args[3]);
                }
                case "unprotect" -> {
                    if (args.length < 5) {
                        System.err.println("[ERROR] Usage: unprotect <secure-doc> <signature> <transaction-key> <sender-public-key>");
                        System.exit(1);
                    }
                    handleUnprotect(args[1], args[2], args[3], args[4]);
                }
                default -> {
                    System.err.println("Unknown command: " + command);
                    System.err.println("Use '" + TOOL_NAME + " help' to see available commands.");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.printf("""
            Secure Documents Library - Command-Line Interface

            USAGE:
              %s help
              %s protect <input-file> <sender-private-key>
              %s check <secure-doc> <signature> <sender-public-key>
              %s unprotect <secure-doc> <signature> <transaction-key> <sender-public-key>

            COMMANDS:
              protect <input-file> <sender-private-key>
                  Protect a document and write the result into 'output/' directory.
                  Output files are named based on input file:
                    - <basename>_secure.json
                    - <basename>_signature.txt
                    - <basename>_key.b64

              check <secure-doc> <signature> <sender-public-key>
                  Verify signature using <sender-public-key> against the secure document.
                  Arguments:
                    <secure-doc> - Path to secure document JSON file
                    <signature>  - Path to signature file
                    <sender-public-key> - Path to sender's public key (.der)

              unprotect <secure-doc> <signature> <transaction-key> <sender-public-key>
                  Verify signature, decrypt with transaction key, verify integrity hash,
                  and write the original document JSON to 'output/' directory.
                  Arguments:
                    <secure-doc>      - Path to secure_document.json
                    <signature>       - Path to signature file
                    <transaction-key> - Path to transaction key file (Base64)
                    <sender-public-key> - Path to sender's public key (.der)

            EXAMPLES:
              %s protect transaction.json keys/alice_private.der
              %s check transaction_secure.json transaction_signature.txt alice_public.der
              %s unprotect transaction_secure.json transaction_signature.txt transaction_key.b64 alice_public.der
            """,
            TOOL_NAME, TOOL_NAME, TOOL_NAME
        );
    }

    private static void handleProtect(String inputFile, String senderPrivateKey) throws Exception {
        String outputDir = "output";

        requireFileExists(inputFile, "Input file does not exist");
        requireFileExists(senderPrivateKey, "Private key file does not exist");

        Path outDirPath = Paths.get(outputDir);
        Files.createDirectories(outDirPath);
        requireDirectory(outDirPath, "Output path is not a directory");

        Map<String, Object> document = readJsonMap(Paths.get(inputFile));

        System.out.println("Protecting document...");
        ProtectResult result = Protect.protect(document, senderPrivateKey);

        String transactionKeyBase64 = Base64.getEncoder().encodeToString(result.getTransactionKey().getEncoded());

        // Derive output filenames from input file
        String inputFileName = Paths.get(inputFile).getFileName().toString();
        String baseName = inputFileName.contains(".") 
            ? inputFileName.substring(0, inputFileName.lastIndexOf("."))
            : inputFileName;
        
        Path secureDocPath = outDirPath.resolve(baseName + "_secure.json");
        Files.writeString(secureDocPath, JsonUtils.toSortedJson(result.getSecureDocument()), StandardCharsets.UTF_8);

        Path sigPath = outDirPath.resolve(baseName + "_signature.txt");
        Files.writeString(sigPath, result.getSenderSignature(), StandardCharsets.UTF_8);

        Path keyPath = outDirPath.resolve(baseName + "_key.b64");
        Files.writeString(keyPath, transactionKeyBase64, StandardCharsets.UTF_8);

        System.out.printf("""
            Document protected successfully!
            Wrote:
              %s
              %s
              %s
            
            IMPORTANT: Protect the transaction key file. It enables decryption.
            """, secureDocPath, sigPath, keyPath);
    }

    private static void handleCheck(String secureDocPath, String signaturePath, String senderPublicKey) throws Exception {
        requireFileExists(secureDocPath, "Secure document file does not exist");
        requireFileExists(signaturePath, "Signature file does not exist");
        requireFileExists(senderPublicKey, "Public key file does not exist");

        Map<String, Object> secureDocument = readJsonMap(Paths.get(secureDocPath));
        String signature = readTrimmedString(Paths.get(signaturePath), "Signature file is empty");

        System.out.println("Verifying document signature...");
        boolean isValid = Check.check(secureDocument, signature, senderPublicKey);

        if (isValid) {
            System.out.println("Signature is VALID");
            System.out.println("The document is authentic and has not been tampered with.");
            System.exit(0);
        } else {
            System.out.println("Signature is INVALID");
            System.out.println("The document may have been tampered with or the signature/public key is incorrect.");
            System.exit(1);
        }
    }

    private static void handleUnprotect(String secureDocPath, String signaturePath, 
                                        String transactionKeyPath, String senderPublicKey) throws Exception {
        
        Path secureDocFile = Paths.get(secureDocPath);
        Path signatureFile = Paths.get(signaturePath);
        Path transactionKeyFile = Paths.get(transactionKeyPath);

        requireFileExists(secureDocPath, "Secure document file does not exist");
        requireFileExists(signaturePath, "Signature file does not exist");
        requireFileExists(transactionKeyPath, "Transaction key file does not exist");
        requireFileExists(senderPublicKey, "Public key file does not exist");

        // Derive output filename from secure document
        String secureDocFileName = secureDocFile.getFileName().toString();
        String outputFileName = secureDocFileName.replace("_secure.json", ".json");
        
        Path outputDir = Paths.get("output");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(outputFileName);

        Map<String, Object> secureDocument = readJsonMap(secureDocFile);
        String signature = readTrimmedString(signatureFile, "Signature file is empty");
        String keyB64 = readTrimmedString(transactionKeyFile, "Transaction key file is empty");

        SecretKey transactionKey = decodeAesKeyFromBase64(keyB64);

        System.out.println("Unprotecting document...");
        Map<String, Object> originalDocument = Unprotect.unprotect(
            secureDocument,
            transactionKey,
            signature,
            senderPublicKey
        );

        Files.writeString(outputFile, JsonUtils.toSortedJson(originalDocument), StandardCharsets.UTF_8);

        System.out.printf("""
            Document unprotected successfully!
            Original document saved to: %s
            Signature verified
            Content integrity verified
            """, outputFile);
    }

    private static void requireFileExists(String path, String messageIfMissing) {
        if (!new File(path).exists()) {
            throw new IllegalArgumentException(messageIfMissing + ": " + path);
        }
    }

    private static void requireDirectoryExists(Path dir, String messageIfMissing) {
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException(messageIfMissing + ": " + dir);
        }
        requireDirectory(dir, "Expected a directory");
    }

    private static void requireDirectory(Path dir, String messageIfNotDir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(messageIfNotDir + ": " + dir);
        }
    }

    private static Map<String, Object> readJsonMap(Path path) throws Exception {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Required file not found: " + path);
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return JsonUtils.fromJson(json);
    }

    private static String readTrimmedString(Path path, String emptyMessage) throws Exception {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Required file not found: " + path);
        }
        String s = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage + ": " + path);
        }
        return s;
    }

    private static SecretKey decodeAesKeyFromBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("Transaction key cannot be null or empty");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Transaction key is not valid Base64", e);
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
