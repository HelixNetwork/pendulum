package net.helix.sbx.utils;


import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelixIOUtils extends IOUtils {

    private static final Logger log = LoggerFactory.getLogger(HelixIOUtils.class);

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                if (it != null) {
                    it.close();
                }
            } catch (Exception ignored) {
                log.debug("Silent exception occured", ignored);
            }
        }
    }
}
