package qupath.edu.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    /**
     * Size of the buffer to read/write data
     */
    private static final int BUFFER_SIZE = 4096;

    private static final Logger logger = LoggerFactory.getLogger(ZipUtil.class);

    /**
     * Zips a given folder and saves the file to specified path.
     * @param folderPath Folder to zip.
     * @param outputPath Where to save zip file.
     * @throws IOException
     */
    public static void zip(Path folderPath, Path outputPath) throws IOException {
        File folder = folderPath.toFile();
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputPath.toFile()));

        if (folder.isFile()) {
            throw new IllegalArgumentException("Path must be a folder.");
        }

        zipDirectory(folder, folder.getName(), zos);

        zos.flush();
        zos.close();
    }

    private static void zipDirectory(File folder, String folderPath, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, folderPath + "/" + file.getName(), zos);
                continue;
            }

            ZipEntry entry = new ZipEntry(folderPath + "/" + file.getName());
            entry.setTime(0);

            zos.putNextEntry(entry);
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read;
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
            }

            bis.close();
            zos.closeEntry();
        }
    }

    /**
     * Extracts a zip file to a directory specified by
     * destDirectory (will be created if does not exists)
     *
     * @param is InputStream consisting of the zip file
     * @param destDirectory Directory where to unzip
     * @throws IOException
     */
    public static void unzip(InputStream is, String destDirectory) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(is, Charset.forName("Cp437"));
        ZipEntry entry = zipIn.getNextEntry();

        while (entry != null) {
            String filePath = destDirectory + "/" + entry.getName();

            if (entry.isDirectory()) {
                Files.createDirectories(Path.of(filePath));
            } else {
                Files.createDirectories(Path.of(filePath).getParent());
                extractFile(zipIn, filePath);
            }

            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }

        zipIn.close();
    }

    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath Path where to save file
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipIn, String filePath) {
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read;

            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }

            bos.close();
            fos.close();
        } catch (IOException e) {
            logger.error("Error while extracting file", e);
        }
    }
}
