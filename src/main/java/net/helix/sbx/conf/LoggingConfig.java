package net.helix.sbx.conf;

public interface LoggingConfig extends Config {
    /**
     * @return {@value Descriptions#SAVELOG}
     */
    boolean getSaveLog();

    /**
     * @return {@value Descriptions#SAVELOG_BASE_PATH}
     */
    String getSaveLogBasePath();

    /**
     * @return {@value Descriptions#SAVELOG_XML_FILE}
     */
    String getSaveLogXMLFile();

    interface Descriptions {
        String SAVELOG = "Boolean variable that indicates whether the log is written to filesystem.";
        String SAVELOG_BASE_PATH = "Base path for the savelog file.";
        String SAVELOG_XML_FILE = "Logback xml file for savelog.";
    }
}
