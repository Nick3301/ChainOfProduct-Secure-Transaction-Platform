package org.example.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Utility class for file operations
 */
public class FileUtils {
    
    /**
     * Reads binary file content into byte array
     */
    public static byte[] readFile(String path) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] content = new byte[fis.available()];
            fis.read(content);
            return content;
        }
    }
}