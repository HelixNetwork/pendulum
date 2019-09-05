package net.helix.hlx.utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class HelixIOUtils extends IOUtils {

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                if (it != null) {
                    it.close();
                }
            } catch (Exception ignored) {
                System.out.println("Silent exception occured");
            }
        }
    }

    public static void saveLogs() {
        String dateParsed = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();
        String rootDir = System.getProperty("user.dir");
        String fileSeperator = System.getProperty("file.separator");
        String logbackXmlFilepath = String.join(
                fileSeperator, rootDir, "src", "main", "resources", "logback-save.xml"
        );
        String logsDir = String.join(fileSeperator, rootDir, "logs");
        String logName = "log-" + dateParsed + "-" + uuidString + ".log";
        String logFilepath =   String.join(fileSeperator, rootDir, "logs", logName);
        System.setProperty("log.name", logFilepath);
        System.setProperty("logback.configurationFile", logbackXmlFilepath);
        File pathToLogDir = Paths.get(logsDir).toFile();
        // check whether path to logs exists and logs is a directory.
        if (!pathToLogDir.exists() || !pathToLogDir.isDirectory()) {
            pathToLogDir.mkdir();
        }
    }
}