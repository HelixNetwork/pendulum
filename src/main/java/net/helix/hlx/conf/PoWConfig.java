package net.helix.hlx.conf;

public interface PoWConfig {
    /**
     * @return {@value PoWConfig.Descriptions#POW_THREADS}
     */
    int getPowThreads();

    /**
     * Field descriptions
     */
    interface Descriptions {
        String POW_THREADS = "Number of threads to use for proof-of-work calculation";
    }
}
