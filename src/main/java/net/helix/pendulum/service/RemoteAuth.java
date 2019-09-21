package net.helix.pendulum.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteAuth {

    private static final Pattern authPattern = Pattern.compile("^Pendulum *([^ ]+) *$", Pattern.CASE_INSENSITIVE);

    /**
     * Extract an access token from Authorization header, or return
     * the value of 'access_token' request parameter.
     */
    public static String getToken(String authorization) {
        // If Authorization header is not found in the request.
        if (authorization == null) {
            // Return the value of 'access_token' request parameter.
            return "";
        }

        // Pattern matching on the value of Authorization header.
        Matcher matcher = authPattern.matcher(authorization);

        // If the value of Authorization header is in the format of
        // 'Bearer access-token'.
        if (matcher.matches()) {
            // Return the value extracted from Authorization header.
            return matcher.group(1);
        } else {
            // Return the value of 'access_token' request parameter.
            return authorization;
        }
    }
}


