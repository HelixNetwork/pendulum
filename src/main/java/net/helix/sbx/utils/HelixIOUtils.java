package net.helix.sbx.utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        String date_parsed = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        String root_dir = System.getProperty("user.dir");
        String slash = System.getProperty("file.separator");
        String logback_xml_filepath = root_dir + slash + "src" + slash + "main" + slash + "resources" + slash + "logback-save.xml";
        String logs_dir = root_dir + slash + "logs";
        String log_filepath = root_dir + slash + "logs" + slash + "LOG__"+date_parsed+"__.log";

        System.setProperty("log.name", log_filepath);
        System.setProperty("logback.configurationFile", logback_xml_filepath);

        File path_to_log_dir = Paths.get(logs_dir).toFile();

        // check whether path to logs exists and logs is a directory.
        if (!path_to_log_dir.exists() || !path_to_log_dir.isDirectory()) {
            path_to_log_dir.mkdir();
        }
    }
}
