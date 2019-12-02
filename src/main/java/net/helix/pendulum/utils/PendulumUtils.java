package net.helix.pendulum.utils;

import net.helix.pendulum.model.Hash;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PendulumUtils {
    private static final Logger log = LoggerFactory.getLogger(PendulumUtils.class);

    public static List<String> splitStringToImmutableList(String string, String regexSplit) {
        return Arrays.stream(string.split(regexSplit))
                .filter(StringUtils::isNoneBlank)
                .collect(Collectors
                        .collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * Used to create low-memory index keys.
     *
     * @param hash the hash we create the key from
     * @param length the length of the desired subhash
     * @return a {@link ByteBuffer} that holds a subarray of {@link Hash#bytes()}
     * that has the specified {@code length}
     */
    public static ByteBuffer getSubHash(Hash hash, int length) {
        if (hash == null) {
            return null;
        }

        return ByteBuffer.wrap(Arrays.copyOf(hash.bytes(), length));
    }

    /**
     * @param clazz Class to inspect
     * @return All the declared and inherited setter method of {@code clazz}
     */
    public static List<Method> getAllSetters(Class<?> clazz) {
        List<Method> setters = new ArrayList<>();
        while (clazz != Object.class) {
            setters.addAll(Stream.of(clazz.getDeclaredMethods())
                    .filter(method -> method.getName().startsWith("set"))
                    .collect(Collectors.toList()));
            clazz = clazz.getSuperclass();
        }
        return Collections.unmodifiableList(setters);
    }

    public static <T> List<T> createImmutableList(T... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    public static String shortToString(byte[] hash, int length) {
        String hexString = Hex.toHexString(hash);
        int startIndex = hexString.length() - length < 0 ? 0 : hexString.length() - length;
        return hexString.substring(startIndex);
    }

    public static String logHashList(Collection<? extends Hash> list, int length) {
        return list.stream()
                .map(h -> PendulumUtils.shortToString(((Hash) h).bytes(), length))
                .collect(Collectors.joining(", "));
    }

    public static int getSystemProp(String propName, int defValue) {
        try {
            String key = System.getProperty(propName);
            return Integer.parseInt(key);
        } catch (Exception e) {
            log.info("Can't parse system property {} using default {}", propName, defValue);
            return defValue;
        }
    }
}
